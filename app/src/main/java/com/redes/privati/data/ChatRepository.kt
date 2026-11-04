package com.redes.privati.data

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.redes.privati.crypto.Handshake
import com.redes.privati.discovery.NsdAdvertiser
import com.redes.privati.discovery.NsdBrowser
import com.redes.privati.net.ChatClient
import com.redes.privati.net.ChatServer
import com.redes.privati.net.Connection
import com.redes.privati.net.Frame
import com.redes.privati.net.FrameType
import com.redes.privati.net.HelloBody
import com.redes.privati.net.AudioBody
import com.redes.privati.net.ChatCreateBody
import com.redes.privati.net.ChatInfoBody
import com.redes.privati.net.ImageBody
import com.redes.privati.net.JoinLeaveBody
import com.redes.privati.net.ParticipantInfo
import com.redes.privati.net.RejectBody
import com.redes.privati.net.TextBody
import com.redes.privati.net.WelcomeBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Repositorio central de Privati.
 *
 * Coordina la capa de red ([ChatServer], [ChatClient], [Connection]) con la UI
 * mediante [StateFlow]. Gestiona el protocolo de sala multi-cliente: handshake
 * HELLO/WELCOME, enrutado de mensajes por chatId, avisos JOIN/LEAVE, rechazo y expulsión.
 *
 * ## Modos de operación
 * - **Host (servidor)**: acepta múltiples clientes (máx. [MAX_CLIENTES]).
 *   En `manejarHello` rechaza sala llena y nombres repetidos. Enrutado inteligente
 *   según [chatId]: sala → todos, dm: → solo el otro, grp: → miembros del grupo.
 * - **Cliente**: una sola [connectionCliente] hacia el servidor.
 *   Si la conexión se pierde inesperadamente, intenta reconectar con backoff
 *   exponencial hasta [MAX_INTENTOS_RECONEXION] veces.
 *
 * ## Modelo multichat (Fase 9)
 * Los mensajes se almacenan en [messages] como mapa chatId → lista. El chat "sala"
 * siempre existe. Los dm/grp se añadirán en Fases 11 y 12. El host enruta con
 * [enrutar] validando que el senderId coincide con la conexión que envía y que el
 * emisor es miembro del chat destino.
 *
 * ## Protocolo de cifrado (Fase 5)
 * Cifrado por salto: cada canal cliente↔host tiene su propia clave ECDH+AES-GCM.
 * El host descifra cada trama entrante y la vuelve a cifrar al reenviarla.
 * Limitación aceptada: el host puede leer los privados y grupos; el cifrado E2E entre
 * clientes queda fuera de alcance y está documentado aquí.
 */
class ChatRepository(private val app: Application) {

    companion object {
        /** Máximo de clientes simultáneos en la sala. */
        private const val MAX_CLIENTES = 8

        /** Número máximo de intentos de reconexión automática. */
        private const val MAX_INTENTOS_RECONEXION = 4

        /** Retrasos de backoff exponencial en ms: 1, 2, 4, 8 s. */
        private val BACKOFF_MS = listOf(1_000L, 2_000L, 4_000L, 8_000L)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Estado observable por la UI
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Mensajes agrupados por chatId. La clave "sala" siempre existe.
     * Los chats dm: y grp: se crearán en Fases 11 y 12.
     */
    private val _messages = MutableStateFlow<Map<String, List<Message>>>(
        mapOf("sala" to emptyList())
    )
    val messages: StateFlow<Map<String, List<Message>>> = _messages.asStateFlow()

    /**
     * Resúmenes de chats para la lista de Chats (Fase 10).
     * En Fase 9 siempre contiene solo el chat "sala".
     */
    private val _chats = MutableStateFlow<List<ChatSummary>>(
        listOf(ChatSummary(chatId = "sala", tipo = ChatTipo.SALA, nombre = "Chat general"))
    )
    val chats: StateFlow<List<ChatSummary>> = _chats.asStateFlow()

    private val _roomState = MutableStateFlow(RoomState())
    val roomState: StateFlow<RoomState> = _roomState.asStateFlow()

    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val error: SharedFlow<String> = _error.asSharedFlow()

    /**
     * Emite el chatId de un grupo recién creado por el usuario local para que la UI
     * pueda navegar automáticamente a él. Solo se emite cuando el usuario local inició
     * la creación (host) o cuando el host confirma la creación solicitada (cliente).
     */
    private val _nuevoChatId = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val nuevoChatId: SharedFlow<String> = _nuevoChatId.asSharedFlow()

    /** Solicitudes de entrada pendientes de aprobación (solo host). */
    private val _pendingRequests = MutableStateFlow<List<PendingRequestInfo>>(emptyList())
    val pendingRequests: StateFlow<List<PendingRequestInfo>> = _pendingRequests.asStateFlow()

    // ──────────────────────────────────────────────────────────────────────────
    // Estado interno de red
    // ──────────────────────────────────────────────────────────────────────────

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Descubrimiento NSD ────────────────────────────────────────────────────
    /** Anuncia la sala vía mDNS mientras el servidor está activo (Fase 4). */
    private val nsdAdvertiser = NsdAdvertiser(app)

    /** Busca salas Corro por código en la red local (Fase 4). */
    private val nsdBrowser = NsdBrowser(app)

    /** Lista de salas descubiertas por NSD en la red local (Fase 14). */
    val salasDescubiertas: StateFlow<List<SalaDescubierta>> = nsdBrowser.salas

    // ── Modo host ──────────────────────────────────────────────────────────────
    /** Conexiones activas de clientes: remoteId → Connection. */
    private val clients = ConcurrentHashMap<String, Connection>()

    /** Participantes identificados (handshake HELLO completado): remoteId → Participant. */
    private val clientParticipants = ConcurrentHashMap<String, Participant>()

    /**
     * UUIDs de participantes expulsados explícitamente por el host.
     * Cualquier HELLO con un senderId en esta lista es rechazado automáticamente.
     * Se limpia cuando el host cierra la sala con [desconectar].
     */
    private val expulsadosIds: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap())

    /**
     * UUIDs de participantes que han sido aceptados en esta sala al menos una vez.
     * Cuando reconectan con el mismo UUID, se aceptan automáticamente sin pasar por PENDING.
     * Se limpia al cerrar la sala con [desconectar].
     */
    private val acceptedIds: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap())

    /** Solicitudes HELLO en espera de aprobación manual del host. */
    private data class PendingEntry(
        val info: PendingRequestInfo,
        val helloBody: com.redes.privati.net.HelloBody,
        val timerJob: Job,
    )
    private val pendingEntries = ConcurrentHashMap<String, PendingEntry>()

    /**
     * Grupos activos en la sala: chatId ("grp:<uuid>") → ChatSummary con la lista de miembros.
     * Se llena en Fase 12 (CHAT_CREATE/CHAT_INFO). En Fase 9 permanece vacío.
     */
    private val grupos = ConcurrentHashMap<String, ChatSummary>()

    private var server: ChatServer? = null

    // ── Modo cliente ───────────────────────────────────────────────────────────
    @Volatile
    private var connectionCliente: Connection? = null

    /** Indica si se está ejecutando un bucle de reconexión automática. */
    @Volatile
    private var reconectando = false

    /**
     * Verdadero cuando el servidor rechazó definitivamente la conexión (REJECT recibido).
     * En ese caso no se debe intentar reconexión automática.
     * Se resetea al llamar [conectarComoCliente] para permitir nuevos intentos manuales.
     */
    @Volatile
    private var reconexionBloqueada = false

    /**
     * Código de sala con el que el cliente se conectó originalmente.
     * Se reenvía en cada HELLO de reconexión para que el servidor valide el código.
     * Vacío cuando la conexión es por IP manual sin código.
     */
    @Volatile
    private var codigoSalaCliente: String = ""

    /** Método de conexión original del cliente: "nfc" | "code" | "ip". */
    @Volatile
    private var metodoConexionCliente: String = "ip"

    /**
     * Par de claves ECDH generado para la sesión actual del cliente (Fase 5).
     * Se crea en [conectarComoCliente] y se consume en [manejarWelcome].
     * Null en modo host o cuando el cifrado no está activo.
     */
    @Volatile
    private var clienteHandshake: Handshake? = null

    // ── Compartido ────────────────────────────────────────────────────────────
    private var localUser: Participant? = null

    /** Verdadero si este dispositivo actúa como servidor. @Volatile para visibilidad entre hilos. */
    @Volatile
    private var modoHost = false

    private val chatClient = ChatClient()
    private val json = Json { ignoreUnknownKeys = true }

    // ──────────────────────────────────────────────────────────────────────────
    // API pública
    // ──────────────────────────────────────────────────────────────────────────

    /** Inicia el descubrimiento continuo de salas NSD (Fase 14). */
    fun iniciarDescubrimiento() = nsdBrowser.iniciarDescubrimiento()

    /** Detiene el descubrimiento continuo de salas NSD (Fase 14). */
    fun detenerDescubrimiento() = nsdBrowser.detenerDescubrimiento()

    fun establecerUsuarioLocal(nombre: String, esHost: Boolean) {
        localUser = Participant(
            id = UUID.randomUUID().toString(),
            name = nombre,
            isHost = esHost,
        )
    }

    /**
     * Inicia el servidor TCP en [puerto]. Acepta clientes en bucle.
     * Llamar siempre después de [establecerUsuarioLocal].
     */
    fun iniciarServidor(puerto: Int = 9000) {
        if (server != null) return
        val usuario = localUser ?: return
        modoHost = true

        val ip = obtenerIpLocal()
        val salaName = "Sala de ${usuario.name}"
        val codigo = generarCodigoSala()
        _roomState.value = RoomState(
            localUserId = usuario.id,
            roomCode = codigo,
            roomSecret = UUID.randomUUID().toString().replace("-", "").substring(0, 16),
            hostIp = ip,
            port = puerto,
            participants = listOf(usuario),
            connectionStatus = ConnectionStatus.DISCONNECTED,
            roomName = salaName,
        )
        _chats.update { lista ->
            lista.map { if (it.chatId == "sala") it.copy(nombre = salaName) else it }
        }

        server = ChatServer(puerto, scope) { socket ->
            val remoteId = UUID.randomUUID().toString()
            val conn = Connection(
                socket = socket,
                scope = scope,
                remoteId = remoteId,
                onFrame = { frame -> procesarTrama(frame, remoteId) },
                onDisconnect = { manejarDesconexionCliente(remoteId) },
            )
            clients[remoteId] = conn
        }
        server!!.start()

        nsdAdvertiser.start(codigo, puerto, salaName, usuario.name, 1)
    }

    /**
     * Conecta como cliente al servidor en [ip]:[puerto].
     * Envía HELLO (con [roomCode] si se proporcionó) y espera WELCOME o PENDING.
     * Llamar siempre después de [establecerUsuarioLocal].
     *
     * @param ip         Dirección IPv4 del servidor.
     * @param puerto     Puerto TCP del servidor.
     * @param roomCode   Código de sala enviado en HELLO; vacío cuando se conecta por IP manual.
     * @param joinMethod Método de conexión: "nfc" | "code" | "ip" (por defecto "ip").
     * @param roomSecret Secreto de sala; solo se envía cuando joinMethod="nfc".
     */
    suspend fun conectarComoCliente(ip: String, puerto: Int, roomCode: String = "", joinMethod: String = "ip", roomSecret: String = "") {
        val usuario = localUser ?: return
        modoHost = false
        reconexionBloqueada = false
        codigoSalaCliente = roomCode
        metodoConexionCliente = joinMethod

        try {
            val socket = chatClient.conectar(ip, puerto)
            val conn = Connection(
                socket = socket,
                scope = scope,
                onFrame = { frame -> procesarTrama(frame, "") },
                onDisconnect = ::manejarDesconexionServidor,
            )
            connectionCliente = conn

            _roomState.update { it.copy(
                localUserId = usuario.id,
                hostIp = ip,
                port = puerto,
                participants = listOf(usuario),
                connectionStatus = ConnectionStatus.RECONNECTING,
                waitingForApproval = false,
            )}

            val handshake = Handshake()
            clienteHandshake = handshake
            val pubKeyB64 = Base64.encodeToString(handshake.publicKeyBytes(), Base64.NO_WRAP)

            val cuerpoHello = json.encodeToString(
                HelloBody(usuario.id, usuario.name, roomCode = roomCode, publicKey = pubKeyB64,
                    joinMethod = joinMethod, roomSecret = roomSecret)
            )
            conn.send(Frame(FrameType.HELLO, cuerpoHello.encodeToByteArray()))
        } catch (e: Exception) {
            _error.tryEmit("No se pudo conectar: ${e.message}")
        }
    }

    /**
     * Busca la sala con [codigoSala] en la red local mediante NSD/mDNS y, al
     * encontrarla, llama a [conectarComoCliente] con la IP y puerto resueltos.
     *
     * Tiempo máximo de espera: 15 segundos. Si se supera, emite un error en [error].
     *
     * @param codigoSala Código de 4 caracteres de la sala a buscar.
     */
    suspend fun buscarSalaPorCodigo(codigoSala: String) {
        try {
            val resultado = withTimeoutOrNull(15_000L) {
                nsdBrowser.buscarSala(codigoSala)
            } ?: throw Exception("No se encontró ninguna sala con el código «$codigoSala» en 15 s")
            val (ip, port) = resultado
            conectarComoCliente(ip, port, roomCode = codigoSala, joinMethod = "code")
        } catch (e: Exception) {
            _error.tryEmit(e.message ?: "No se pudo encontrar la sala")
        }
    }

    /**
     * Selecciona, comprime y envía una imagen al chat [chatId].
     *
     * El bitmap se reescala a 1280 px en el lado largo (si es necesario) y se
     * comprime a JPEG calidad 80 en [Dispatchers.Default] antes de enviar.
     * El cuerpo de la trama IMAGE tiene el formato:
     * ```
     * [4B longitud de cabecera BE][bytes JSON][bytes JPEG]
     * ```
     *
     * @param uri    URI de la imagen seleccionada con [ActivityResultContracts.PickVisualMedia].
     * @param chatId Chat destino (por defecto "sala").
     */
    suspend fun enviarImagen(uri: Uri, chatId: String = "sala") {
        val usuario = localUser ?: return

        val jpegBytes = withContext(Dispatchers.Default) {
            try {
                // URI file:// (foto de cámara): leer directamente con FileInputStream para
                // evitar problemas de permisos del FileProvider en algunos fabricantes.
                // URI content:// (galería): usar ContentResolver normalmente.
                val esArchivoLocal = uri.scheme == "file"
                val entrada: java.io.InputStream = when {
                    esArchivoLocal -> java.io.FileInputStream(java.io.File(uri.path!!))
                    else -> app.contentResolver.openInputStream(uri) ?: return@withContext null
                }
                val original = BitmapFactory.decodeStream(entrada)
                entrada.close()
                // Eliminar el fichero temporal de cámara después de decodificar,
                // no antes: enviarImagen() es asíncrono y el borrado debe ser posterior a la lectura.
                if (esArchivoLocal) {
                    try { java.io.File(uri.path!!).delete() } catch (_: Exception) {}
                }
                if (original == null) return@withContext null

                val maxLado = 1280
                val w = original.width
                val h = original.height
                val escalado = if (maxOf(w, h) > maxLado) {
                    val escala = maxLado.toFloat() / maxOf(w, h)
                    Bitmap.createScaledBitmap(original, (w * escala).toInt(), (h * escala).toInt(), true)
                } else {
                    original
                }

                val salida = ByteArrayOutputStream()
                escalado.compress(Bitmap.CompressFormat.JPEG, 80, salida)
                salida.toByteArray()
            } catch (_: Exception) {
                null
            }
        }

        if (jpegBytes == null) {
            _error.tryEmit("No se pudo procesar la imagen")
            return
        }

        val id = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val etiqueta = "${jpegBytes.size / 1024} KB · por red local"

        agregarMensaje(Message(
            id = id,
            senderId = usuario.id,
            senderName = usuario.name,
            type = MessageType.IMAGE,
            imageBytes = jpegBytes,
            imageSizeLabel = etiqueta,
            timestamp = timestamp,
            isOwn = true,
            chatId = chatId,
        ))

        // Serializar cuerpo: [4B longitud cabecera BE][bytes JSON cabecera][bytes JPEG]
        val cabeceraJson = json.encodeToString(
            ImageBody(id, usuario.id, usuario.name, timestamp, jpegBytes.size, chatId = chatId)
        )
        val cabeceraBytes = cabeceraJson.encodeToByteArray()
        val cuerpo = ByteBuffer.allocate(4 + cabeceraBytes.size + jpegBytes.size)
            .putInt(cabeceraBytes.size)
            .put(cabeceraBytes)
            .put(jpegBytes)
            .array()

        val trama = Frame(FrameType.IMAGE, cuerpo)
        if (modoHost) enrutar(trama, chatId, usuario.id) else connectionCliente?.send(trama)
    }

    /**
     * Envía texto al chat [chatId]: pinta localmente + relay a clientes (host) o envía al servidor (cliente).
     *
     * @param texto  Contenido del mensaje.
     * @param chatId Chat destino (por defecto "sala").
     */
    fun enviarTexto(texto: String, chatId: String = "sala") {
        val usuario = localUser ?: return

        val id = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        agregarMensaje(Message(
            id = id,
            senderId = usuario.id,
            senderName = usuario.name,
            text = texto,
            timestamp = timestamp,
            isOwn = true,
            chatId = chatId,
        ))

        val cuerpo = json.encodeToString(
            TextBody(id, usuario.id, usuario.name, texto, timestamp, chatId = chatId)
        )
        val frame = Frame(FrameType.TEXT, cuerpo.encodeToByteArray())

        if (modoHost) enrutar(frame, chatId, usuario.id) else connectionCliente?.send(frame)
    }

    /**
     * Expulsa a un participante de la sala.
     * Envía REJECT al expulsado, cierra su conexión y notifica al resto con LEAVE.
     * Solo tiene efecto en modo host.
     *
     * @param participantId UUID del participante a expulsar.
     */
    fun expulsarParticipante(participantId: String) {
        if (!modoHost) return
        val remoteId = clientParticipants.entries
            .firstOrNull { it.value.id == participantId }
            ?.key ?: return

        val participant = clientParticipants.remove(remoteId) ?: return

        // Añadir a la lista negra antes de enviar el REJECT para que,
        // si el cliente reconecta rápido, el nuevo HELLO también sea rechazado
        expulsadosIds.add(participant.id)

        val cuerpoReject = json.encodeToString(RejectBody("Has sido expulsado de la sala"))
        clients[remoteId]?.send(Frame(FrameType.REJECT, cuerpoReject.encodeToByteArray()))

        // Cerrar el socket con un pequeño retardo para garantizar que el REJECT
        // llega al cliente antes del EOF. Si cerramos inmediatamente, el sistema
        // operativo puede descartar el REJECT aún en el buffer de envío.
        val conn = clients.remove(remoteId)
        scope.launch {
            delay(500L)
            conn?.close()
        }

        val listaActualizada = construirListaParticipantes()
        val nuevoEstado = if (listaActualizada.size > 1) ConnectionStatus.ONLINE
                          else ConnectionStatus.DISCONNECTED
        _roomState.update { it.copy(participants = listaActualizada, connectionStatus = nuevoEstado) }

        val cuerpoLeave = json.encodeToString(JoinLeaveBody(participant.id, participant.name))
        difundir(Frame(FrameType.LEAVE, cuerpoLeave.encodeToByteArray()))
        agregarMensaje(mensajeSistema("${participant.name} ha sido expulsado"))
    }

    /** Envía BYE, cierra todas las conexiones y limpia el estado. */
    fun desconectar() {
        reconectando = false
        if (modoHost) {
            difundir(Frame(FrameType.BYE, ByteArray(0)))
            clients.values.forEach { it.close() }
            clients.clear()
            clientParticipants.clear()
            expulsadosIds.clear()
            acceptedIds.clear()
            pendingEntries.forEach { (_, entry) -> entry.timerJob.cancel() }
            pendingEntries.clear()
            _pendingRequests.value = emptyList()
            grupos.clear()
            nsdAdvertiser.stop()
            server?.stop()
            server = null
        } else {
            connectionCliente?.send(Frame(FrameType.BYE, ByteArray(0)))
            connectionCliente?.close()
            connectionCliente = null
        }
        _roomState.value = RoomState()
        _messages.value = mapOf("sala" to emptyList())
        _chats.value = listOf(ChatSummary(chatId = "sala", tipo = ChatTipo.SALA, nombre = "Chat general"))
        localUser = null
        modoHost = false
    }

    /**
     * El host acepta la solicitud de entrada del cliente identificado por [remoteId].
     * Se envía WELCOME y el cliente pasa a ser un participante activo.
     */
    fun aceptarSolicitud(remoteId: String) {
        if (!modoHost) return
        val entry = pendingEntries.remove(remoteId) ?: return
        entry.timerJob.cancel()
        _pendingRequests.update { it.filter { r -> r.remoteId != remoteId } }
        procesarAceptacion(remoteId, entry.helloBody)
    }

    /**
     * El host rechaza la solicitud de entrada del cliente identificado por [remoteId].
     * Se envía REJECT y se cierra la conexión.
     */
    fun rechazarSolicitud(remoteId: String) {
        if (!modoHost) return
        val entry = pendingEntries.remove(remoteId) ?: return
        entry.timerJob.cancel()
        _pendingRequests.update { it.filter { r -> r.remoteId != remoteId } }
        rechazar(remoteId, "El host ha rechazado tu solicitud de entrada")
    }

    /**
     * El cliente cancela su solicitud de entrada pendiente.
     * Envía BYE, cierra la conexión y limpia el estado de espera.
     */
    fun cancelarEspera() {
        connectionCliente?.send(Frame(FrameType.BYE, ByteArray(0)))
        connectionCliente?.close()
        connectionCliente = null
        reconexionBloqueada = true
        _roomState.update { it.copy(
            connectionStatus = ConnectionStatus.DISCONNECTED,
            waitingForApproval = false,
            isEncrypted = false,
        )}
    }

    /**
     * El host renombra la sala: actualiza el estado local, notifica a los clientes con
     * CHAT_INFO(chatId="sala") y re-anuncia el servicio NSD con el nuevo nombre.
     */
    fun renombrarSala(nuevoNombre: String) {
        if (!modoHost) return
        val trimmed = nuevoNombre.trim().ifBlank { return }
        val usuario = localUser ?: return
        _roomState.update { it.copy(roomName = trimmed) }
        _chats.update { lista ->
            lista.map { if (it.chatId == "sala") it.copy(nombre = trimmed) else it }
        }
        agregarMensaje(mensajeSistema("El anfitrión renombró la sala a «$trimmed»"))
        val infoBody = ChatInfoBody("sala", trimmed, emptyList())
        difundir(Frame(FrameType.CHAT_INFO, json.encodeToString(infoBody).encodeToByteArray()))
        nsdAdvertiser.actualizar(trimmed, usuario.name, _roomState.value.participants.size)
    }

    /**
     * Crea un nuevo grupo con [nombre] y [memberIds] como miembros adicionales.
     *
     * - **Host**: genera el chatId directamente, registra el grupo localmente, envía
     *   [FrameType.CHAT_INFO] a cada miembro y emite [nuevoChatId] para navegar.
     * - **Cliente**: envía [FrameType.CHAT_CREATE] al host; cuando el host responde con
     *   [FrameType.CHAT_INFO] y el creatorId coincide con el usuario local, se emite [nuevoChatId].
     *
     * @param nombre    Nombre visible del grupo.
     * @param memberIds UUIDs de los otros miembros (sin incluir al usuario local).
     */
    fun crearGrupo(nombre: String, memberIds: List<String>) {
        val usuario = localUser ?: return
        if (modoHost) {
            val chatId = "grp:${UUID.randomUUID()}"
            val todosLosMiembros = (memberIds + usuario.id).distinct()
            registrarGrupoLocal(chatId, nombre, todosLosMiembros, usuario.name)

            val infoBody = ChatInfoBody(chatId, nombre, todosLosMiembros, usuario.id, usuario.name)
            val tramaChatInfo = Frame(FrameType.CHAT_INFO, json.encodeToString(infoBody).encodeToByteArray())
            todosLosMiembros.forEach { miembroId ->
                if (miembroId != usuario.id) {
                    val destRemoteId = remoteIdDeParticipante(miembroId) ?: return@forEach
                    clients[destRemoteId]?.send(tramaChatInfo)
                }
            }
            scope.launch { _nuevoChatId.emit(chatId) }
        } else {
            val body = ChatCreateBody(nombre, memberIds)
            val trama = Frame(FrameType.CHAT_CREATE, json.encodeToString(body).encodeToByteArray())
            connectionCliente?.send(trama)
        }
    }

    /**
     * Asegura que el chat [chatId] existe en [_chats] con nombre visible antes de
     * que llegue el primer mensaje. Se usa al abrir un privado (dm:) desde Contactos
     * para que la cabecera de la conversación muestre el nombre, no el chatId crudo.
     * No hace nada si el chat ya existe.
     */
    fun asegurarChat(chatId: String) {
        if (_chats.value.any { it.chatId == chatId }) return
        _chats.update { lista ->
            lista + ChatSummary(
                chatId = chatId,
                tipo = tipoDeChatId(chatId),
                nombre = nombreDeChatId(chatId),
            )
        }
    }

    /**
     * Marca todos los mensajes de [chatId] como leídos (noLeidos = 0).
     * Llamar al abrir la pantalla de conversación de ese chat.
     */
    fun marcarLeido(chatId: String) {
        _chats.update { lista ->
            lista.map { if (it.chatId == chatId) it.copy(noLeidos = 0) else it }
        }
    }

    /**
     * Silencia o activa las notificaciones del chat [chatId] (solo local).
     * Cuando está silenciado, los mensajes nuevos no incrementan la insignia.
     */
    fun silenciarChat(chatId: String, silenciado: Boolean) {
        _chats.update { lista ->
            lista.map { if (it.chatId == chatId) it.copy(silenciado = silenciado) else it }
        }
    }

    /**
     * Fija o desfija el chat [chatId] al principio de la lista (solo local).
     */
    fun fijarChat(chatId: String, fijado: Boolean) {
        _chats.update { lista ->
            lista.map { if (it.chatId == chatId) it.copy(fijado = fijado) else it }
        }
    }

    /**
     * Elimina el historial local del chat [chatId] y lo oculta de la lista.
     * No tiene efecto sobre el chat «sala» (nunca se elimina).
     * El chat reaparece automáticamente cuando llega un mensaje nuevo.
     */
    fun eliminarChatLocal(chatId: String) {
        if (chatId == "sala") return // sala no se puede eliminar
        _messages.update { mapa -> mapa - chatId }
        _chats.update { lista -> lista.filter { it.chatId != chatId } }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Gestión de mensajes por chat
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Añade [mensaje] al mapa de mensajes del chat correspondiente y actualiza
     * el resumen del chat. Los mensajes de sistema no suman en no-leídos.
     */
    private fun agregarMensaje(mensaje: Message) {
        val chatId = mensaje.chatId
        _messages.update { mapa ->
            val lista = mapa.getOrDefault(chatId, emptyList())
            mapa + (chatId to lista + mensaje)
        }
        // Los mensajes de sistema no actualizan la vista previa ni los no-leídos
        if (mensaje.type != MessageType.SYSTEM) {
            actualizarResumenChat(chatId, mensaje)
        }
    }

    /**
     * Actualiza o crea el [ChatSummary] del chat [chatId] con el último mensaje.
     * Incrementa [ChatSummary.noLeidos] salvo que el mensaje sea propio ([Message.isOwn]).
     */
    private fun actualizarResumenChat(chatId: String, mensaje: Message) {
        val preview = when (mensaje.type) {
            MessageType.TEXT  -> mensaje.text
            MessageType.IMAGE -> "[imagen]"
            MessageType.AUDIO -> "[nota de voz]"
            MessageType.SYSTEM -> ""
        }
        _chats.update { lista ->
            val existe = lista.any { it.chatId == chatId }
            if (existe) {
                lista.map { resumen ->
                    if (resumen.chatId == chatId) {
                        resumen.copy(
                            ultimoMensaje = preview,
                            ultimaHora = mensaje.timestamp,
                            noLeidos = if (!mensaje.isOwn && !resumen.silenciado)
                                resumen.noLeidos + 1 else resumen.noLeidos,
                        )
                    } else resumen
                }
            } else {
                // Chat nuevo (dm o grp que aparece por primera vez en Fases 11-12)
                lista + ChatSummary(
                    chatId = chatId,
                    tipo = tipoDeChatId(chatId),
                    nombre = nombreDeChatId(chatId),
                    ultimoMensaje = preview,
                    ultimaHora = mensaje.timestamp,
                    noLeidos = if (!mensaje.isOwn) 1 else 0,
                )
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Enrutado de tramas
    // ──────────────────────────────────────────────────────────────────────────

    private fun procesarTrama(frame: Frame, remoteId: String) {
        when (frame.type) {
            FrameType.HELLO -> if (modoHost) manejarHello(frame, remoteId)
            FrameType.WELCOME -> if (!modoHost) manejarWelcome(frame)
            FrameType.REJECT -> if (!modoHost) manejarReject(frame)
            FrameType.PENDING -> if (!modoHost) manejarPending()
            FrameType.TEXT -> manejarText(frame, remoteId)
            FrameType.JOIN -> if (!modoHost) manejarJoin(frame)
            FrameType.LEAVE -> if (!modoHost) manejarLeave(frame)
            FrameType.BYE -> manejarBye(remoteId)
            FrameType.IMAGE -> manejarImage(frame, remoteId)
            FrameType.AUDIO -> manejarAudio(frame, remoteId)
            FrameType.CHAT_CREATE -> if (modoHost) manejarChatCreate(frame, remoteId)
            FrameType.CHAT_INFO -> if (!modoHost) manejarChatInfo(frame)
            FrameType.PING -> {
                val pong = Frame(FrameType.PONG, ByteArray(0))
                if (modoHost) clients[remoteId]?.send(pong) else connectionCliente?.send(pong)
            }
            FrameType.PONG -> { /* respuesta al latido; la actividad ya se registró en Connection */ }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Manejadores — Modo host
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * El servidor recibe HELLO de un nuevo cliente.
     *
     * Flujo (Fase 13):
     * 1. Rechaza si expulsado, código incorrecto, sala llena o nombre repetido.
     * 2. Auto-acepta si el UUID ya fue aceptado antes (reconexión) o si viene por NFC con secreto válido.
     * 3. En cualquier otro caso, pone en PENDING con un temporizador de 60 s.
     */
    private fun manejarHello(frame: Frame, remoteId: String) {
        try {
            val hello = json.decodeFromString<HelloBody>(frame.body.decodeToString())

            if (expulsadosIds.contains(hello.senderId)) {
                rechazar(remoteId, "Has sido expulsado de esta sala")
                return
            }
            val codigoDeLaSala = _roomState.value.roomCode
            if (hello.roomCode.isNotBlank() &&
                !hello.roomCode.equals(codigoDeLaSala, ignoreCase = true)
            ) {
                rechazar(remoteId, "Código de sala incorrecto")
                return
            }
            // Las solicitudes pendientes cuentan para el máximo de la sala
            if (clientParticipants.size + pendingEntries.size >= MAX_CLIENTES) {
                rechazar(remoteId, "La sala está llena (máximo $MAX_CLIENTES participantes)")
                return
            }
            val nombreDelHost = localUser?.name ?: ""
            val nombreRepetido = nombreDelHost.equals(hello.senderName, ignoreCase = true) ||
                clientParticipants.values.any {
                    it.name.equals(hello.senderName, ignoreCase = true) && it.id != hello.senderId
                }
            if (nombreRepetido) {
                rechazar(remoteId, "El nombre «${hello.senderName}» ya está en uso en esta sala")
                return
            }

            // Auto-aceptar: reconexión de un ID ya aprobado
            if (acceptedIds.contains(hello.senderId)) {
                procesarAceptacion(remoteId, hello)
                return
            }
            // Auto-aceptar: NFC con secreto válido
            val secretoActual = _roomState.value.roomSecret
            if (hello.joinMethod == "nfc" && secretoActual.isNotBlank() && hello.roomSecret == secretoActual) {
                procesarAceptacion(remoteId, hello)
                return
            }

            // Poner en espera: enviar PENDING y arrancar el temporizador de 60 s
            val timerJob = scope.launch {
                delay(60_000L)
                val entry = pendingEntries.remove(remoteId) ?: return@launch
                entry.timerJob.cancel()
                _pendingRequests.update { it.filter { r -> r.remoteId != remoteId } }
                rechazar(remoteId, "La solicitud expiró (60 s sin respuesta del host)")
            }
            val info = PendingRequestInfo(
                remoteId = remoteId,
                participantId = hello.senderId,
                participantName = hello.senderName,
                joinMethod = hello.joinMethod,
                timestampMs = System.currentTimeMillis(),
            )
            pendingEntries[remoteId] = PendingEntry(info, hello, timerJob)
            _pendingRequests.update { it + info }
            clients[remoteId]?.send(Frame(FrameType.PENDING, ByteArray(0)))
        } catch (e: Exception) {
            rechazar(remoteId, "Formato de identificación incorrecto")
        }
    }

    /**
     * Registra al participante, realiza el handshake ECDH, envía WELCOME y notifica con JOIN.
     * Se llama tanto desde el flujo de auto-aceptación como desde [aceptarSolicitud].
     */
    private fun procesarAceptacion(remoteId: String, hello: com.redes.privati.net.HelloBody) {
        val host = localUser ?: return
        val ahora = System.currentTimeMillis()

        val nuevoParticipante = Participant(
            id = hello.senderId,
            name = hello.senderName,
            joinMethod = hello.joinMethod,
            joinedAt = ahora,
        )
        clientParticipants[remoteId] = nuevoParticipante
        acceptedIds.add(hello.senderId)

        val listaParticipantes = construirListaParticipantes()
        _roomState.update { it.copy(
            participants = listaParticipantes,
            connectionStatus = ConnectionStatus.ONLINE,
        )}
        reAnunciarNsd()

        var hostPublicKeyB64 = ""
        if (hello.publicKey.isNotBlank()) {
            try {
                val peerKeyBytes = Base64.decode(hello.publicKey, Base64.NO_WRAP)
                val handshake = Handshake()
                val cipher = handshake.deriveSessionCipher(
                    peerKeyBytes,
                    roomCode = hello.roomCode,
                    isClient = false,
                )
                clients[remoteId]?.cipher = cipher
                hostPublicKeyB64 = Base64.encodeToString(handshake.publicKeyBytes(), Base64.NO_WRAP)
                _roomState.update { it.copy(isEncrypted = true) }
            } catch (_: Exception) {}
        }

        val gruposDelParticipante = grupos.values
            .filter { g -> g.miembros.contains(hello.senderId) }
            .map { g -> ChatInfoBody(g.chatId, g.nombre, g.miembros) }
        val infoParticipantes = listaParticipantes.map { p ->
            ParticipantInfo(p.id, p.name, p.isHost, p.joinMethod, p.joinedAt)
        }
        val cuerpoWelcome = json.encodeToString(
            WelcomeBody(
                assignedId = hello.senderId,
                hostId = host.id,
                hostName = host.name,
                participants = infoParticipantes,
                publicKey = hostPublicKeyB64,
                grupos = gruposDelParticipante,
                roomName = _roomState.value.roomName,
            )
        )
        clients[remoteId]?.send(Frame(FrameType.WELCOME, cuerpoWelcome.encodeToByteArray()))

        val cuerpoJoin = json.encodeToString(JoinLeaveBody(hello.senderId, hello.senderName, hello.joinMethod, ahora))
        difundir(Frame(FrameType.JOIN, cuerpoJoin.encodeToByteArray()), excluirId = remoteId)

        val etiqueta = etiquetaMetodo(hello.joinMethod)
        agregarMensaje(mensajeSistema("${hello.senderName} se ha unido$etiqueta"))
    }

    private fun etiquetaMetodo(joinMethod: String) = when (joinMethod) {
        "nfc" -> " por NFC"
        "code" -> " con el código"
        else -> ""
    }

    private fun reAnunciarNsd() {
        if (!modoHost) return
        val usuario = localUser ?: return
        val estado = _roomState.value
        nsdAdvertiser.actualizar(
            nombreSala = estado.roomName.ifBlank { "Sala de ${usuario.name}" },
            nombreHost = usuario.name,
            numConectados = estado.participants.size,
        )
    }

    /** Envía REJECT y cierra la conexión del cliente identificado por [remoteId]. */
    private fun rechazar(remoteId: String, motivo: String) {
        val cuerpoReject = json.encodeToString(RejectBody(motivo))
        clients[remoteId]?.send(Frame(FrameType.REJECT, cuerpoReject.encodeToByteArray()))
        clients.remove(remoteId)?.close()
        clientParticipants.remove(remoteId)
    }

    /**
     * El servidor recibe TEXT de un cliente.
     * Valida que el senderId coincida con la conexión que envía (evita suplantación).
     * Valida que el emisor sea miembro del chatId. Si pasa, añade al hilo y enruta.
     */
    private fun manejarTextHost(frame: Frame, remoteId: String) {
        try {
            val textBody = json.decodeFromString<TextBody>(frame.body.decodeToString())

            // Validar que el senderId coincide con el participante de esta conexión
            val participante = clientParticipants[remoteId] ?: return
            if (textBody.senderId != participante.id) {
                android.util.Log.w("Corro", "TEXT: senderId no coincide con remoteId $remoteId")
                return
            }
            // Validar membresía del chat
            if (!esMiembroDelChat(textBody.chatId, participante.id)) {
                android.util.Log.w("Corro", "TEXT: ${participante.name} no es miembro de ${textBody.chatId}")
                return
            }

            agregarMensaje(Message(
                id = textBody.id,
                senderId = textBody.senderId,
                senderName = textBody.senderName,
                text = textBody.text,
                timestamp = textBody.timestamp,
                isOwn = false,
                chatId = textBody.chatId,
            ))
            enrutar(frame, textBody.chatId, textBody.senderId)
        } catch (_: Exception) {}
    }

    /** Desconexión inesperada de un cliente: limpia la sesión y avisa a los demás. */
    private fun manejarDesconexionCliente(remoteId: String) {
        // Si era una solicitud pendiente, solo limpiar sin avisar con LEAVE
        val pendingEntry = pendingEntries.remove(remoteId)
        if (pendingEntry != null) {
            pendingEntry.timerJob.cancel()
            clients.remove(remoteId)?.close()
            _pendingRequests.update { it.filter { r -> r.remoteId != remoteId } }
            return
        }

        val participant = clientParticipants.remove(remoteId) ?: return
        clients.remove(remoteId)?.close()

        val listaActualizada = construirListaParticipantes()
        val nuevoEstado = if (listaActualizada.size > 1) ConnectionStatus.ONLINE
                          else ConnectionStatus.DISCONNECTED
        _roomState.update { it.copy(participants = listaActualizada, connectionStatus = nuevoEstado) }
        reAnunciarNsd()

        val cuerpoLeave = json.encodeToString(JoinLeaveBody(participant.id, participant.name))
        difundir(Frame(FrameType.LEAVE, cuerpoLeave.encodeToByteArray()))
        agregarMensaje(mensajeSistema("${participant.name} se ha desconectado"))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Manejadores — Modo cliente
    // ──────────────────────────────────────────────────────────────────────────

    /** El host ha enviado PENDING: la solicitud está en cola, esperar aprobación. */
    private fun manejarPending() {
        _roomState.update { it.copy(waitingForApproval = true) }
    }

    private fun manejarWelcome(frame: Frame) {
        val cliente = localUser ?: return
        try {
            val welcome = json.decodeFromString<WelcomeBody>(frame.body.decodeToString())
            val participantes = if (welcome.participants.isNotEmpty()) {
                welcome.participants.map { info ->
                    Participant(id = info.id, name = info.name, isHost = info.isHost, joinMethod = info.joinMethod, joinedAt = info.joinedAt)
                }
            } else {
                listOf(
                    Participant(id = welcome.hostId, name = welcome.hostName, isHost = true),
                    cliente,
                )
            }
            val salaName = welcome.roomName.ifBlank { "" }
            _roomState.update { it.copy(
                participants = participantes,
                connectionStatus = ConnectionStatus.ONLINE,
                waitingForApproval = false,
                roomName = salaName,
            )}
            if (salaName.isNotBlank()) {
                _chats.update { lista ->
                    lista.map { if (it.chatId == "sala") it.copy(nombre = salaName) else it }
                }
            }
            reconectando = false

            // Restaurar los grupos a los que pertenece el cliente (reconexión o primer saludo)
            welcome.grupos.forEach { grupoInfo ->
                registrarGrupoLocal(grupoInfo.chatId, grupoInfo.name, grupoInfo.memberIds, "")
            }

            // ── Completar handshake ECDH (Fase 5) ────────────────────────────
            // El host respondió con su clave pública: derivar la clave de sesión
            // e instalar el cifrador en la conexión. A partir de aquí todas las
            // tramas (texto, audio, imagen, ping…) se cifran extremo a servidor.
            if (welcome.publicKey.isNotBlank()) {
                try {
                    val handshake = clienteHandshake
                    if (handshake != null) {
                        val peerKeyBytes = Base64.decode(welcome.publicKey, Base64.NO_WRAP)
                        val cipher = handshake.deriveSessionCipher(
                            peerKeyBytes,
                            roomCode = codigoSalaCliente,
                            isClient = true,  // este extremo es el cliente
                        )
                        connectionCliente?.cipher = cipher
                        _roomState.update { it.copy(isEncrypted = true) }
                    }
                } catch (e: Exception) {
                    // Clave malformada: continuar sin cifrado
                } finally {
                    clienteHandshake = null  // ya no se necesita
                }
            }
        } catch (e: Exception) {
            _error.tryEmit("Respuesta del servidor malformada.")
        }
    }

    /** El servidor rechazó la conexión (sala llena, expulsado, nombre en uso, timeout…). */
    private fun manejarReject(frame: Frame) {
        try {
            val reject = json.decodeFromString<RejectBody>(frame.body.decodeToString())
            _error.tryEmit(reject.reason)
        } catch (_: Exception) {
            _error.tryEmit("Conexión rechazada.")
        }
        reconexionBloqueada = true
        reconectando = false
        connectionCliente?.close()
        connectionCliente = null
        _roomState.update { it.copy(connectionStatus = ConnectionStatus.DISCONNECTED, isEncrypted = false, waitingForApproval = false) }
    }

    private fun manejarJoin(frame: Frame) {
        try {
            val join = json.decodeFromString<JoinLeaveBody>(frame.body.decodeToString())
            val nuevo = Participant(
                id = join.participantId,
                name = join.participantName,
                joinMethod = join.joinMethod,
                joinedAt = if (join.joinedAt > 0) join.joinedAt else System.currentTimeMillis(),
            )
            _roomState.update { it.copy(participants = it.participants + nuevo) }
            val etiqueta = etiquetaMetodo(join.joinMethod)
            agregarMensaje(mensajeSistema("${join.participantName} se ha unido$etiqueta"))
        } catch (_: Exception) {}
    }

    private fun manejarLeave(frame: Frame) {
        try {
            val leave = json.decodeFromString<JoinLeaveBody>(frame.body.decodeToString())
            _roomState.update { it.copy(
                participants = it.participants.filter { p -> p.id != leave.participantId },
            )}
            // Los avisos LEAVE solo se muestran en el chat sala
            agregarMensaje(mensajeSistema("${leave.participantName} ha salido"))
        } catch (_: Exception) {}
    }

    /**
     * Desconexión inesperada del servidor.
     * Cierra la conexión actual y lanza el bucle de reconexión automática con backoff.
     * No se llama cuando el usuario cierra explícitamente con [desconectar].
     */
    private fun manejarDesconexionServidor() {
        connectionCliente?.close()
        connectionCliente = null
        // Si la conexión cae mientras esperamos aprobación, limpiar la bandera
        _roomState.update { it.copy(waitingForApproval = false) }
        if (!reconectando && !reconexionBloqueada) {
            scope.launch { intentarReconectar() }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Manejadores — Compartidos
    // ──────────────────────────────────────────────────────────────────────────

    private fun manejarText(frame: Frame, remoteId: String) {
        if (modoHost) manejarTextHost(frame, remoteId) else manejarTextCliente(frame)
    }

    private fun manejarTextCliente(frame: Frame) {
        try {
            val textBody = json.decodeFromString<TextBody>(frame.body.decodeToString())
            agregarMensaje(Message(
                id = textBody.id,
                senderId = textBody.senderId,
                senderName = textBody.senderName,
                text = textBody.text,
                timestamp = textBody.timestamp,
                isOwn = false,
                chatId = textBody.chatId,
            ))
        } catch (_: Exception) {}
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Envío y recepción de notas de voz (Fase 7)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Añade la nota de voz al hilo local del chat [chatId] y la envía como trama AUDIO.
     * El cuerpo de la trama tiene el formato:
     * ```
     * [4B longitud cabecera BE][bytes JSON][bytes AAC-MPEG4]
     * ```
     *
     * @param chatId Chat destino (por defecto "sala").
     */
    fun enviarAudio(bytes: ByteArray, durationMs: Long, waveform: List<Float>, chatId: String = "sala") {
        val usuario = localUser ?: return

        val id = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        agregarMensaje(Message(
            id = id,
            senderId = usuario.id,
            senderName = usuario.name,
            type = MessageType.AUDIO,
            audioBytes = bytes,
            audioDurationMs = durationMs,
            audioWaveform = waveform,
            timestamp = timestamp,
            isOwn = true,
            chatId = chatId,
        ))

        val cabeceraJson = json.encodeToString(
            AudioBody(id, usuario.id, usuario.name, timestamp, durationMs, bytes.size, waveform, chatId = chatId)
        )
        val cabeceraBytes = cabeceraJson.encodeToByteArray()
        val cuerpo = ByteBuffer.allocate(4 + cabeceraBytes.size + bytes.size)
            .putInt(cabeceraBytes.size)
            .put(cabeceraBytes)
            .put(bytes)
            .array()

        val trama = Frame(FrameType.AUDIO, cuerpo)
        if (modoHost) enrutar(trama, chatId, usuario.id) else connectionCliente?.send(trama)
    }

    /**
     * Parsea la cabecera JSON y los bytes AAC de una trama AUDIO.
     * Formato: [4B longitud cabecera BE][bytes JSON][bytes AAC].
     * Devuelve null si el formato es inválido.
     */
    private fun parsearTramaAudio(frame: Frame): Pair<AudioBody, ByteArray>? {
        return try {
            val buf = ByteBuffer.wrap(frame.body)
            val jsonLen = buf.int
            val jsonBytes = ByteArray(jsonLen)
            buf.get(jsonBytes)
            val cabecera = json.decodeFromString<AudioBody>(jsonBytes.decodeToString())
            val aacBytes = ByteArray(buf.remaining())
            buf.get(aacBytes)
            Pair(cabecera, aacBytes)
        } catch (_: Exception) {
            null
        }
    }

    /** El cliente recibe AUDIO del servidor: añade la nota al hilo del chat correspondiente. */
    private fun manejarAudioCliente(frame: Frame) {
        val (cabecera, aacBytes) = parsearTramaAudio(frame) ?: return
        agregarMensaje(Message(
            id = cabecera.id,
            senderId = cabecera.senderId,
            senderName = cabecera.senderName,
            type = MessageType.AUDIO,
            audioBytes = aacBytes,
            audioDurationMs = cabecera.durationMs,
            audioWaveform = cabecera.waveform,
            timestamp = cabecera.timestamp,
            isOwn = false,
            chatId = cabecera.chatId,
        ))
    }

    /**
     * El servidor recibe AUDIO de un cliente.
     * Valida senderId y membresía antes de añadir al hilo y enrutar.
     */
    private fun manejarAudioHost(frame: Frame, remoteId: String) {
        val (cabecera, _) = parsearTramaAudio(frame) ?: return

        val participante = clientParticipants[remoteId] ?: return
        if (cabecera.senderId != participante.id) {
            android.util.Log.w("Corro", "AUDIO: senderId no coincide con remoteId $remoteId")
            return
        }
        if (!esMiembroDelChat(cabecera.chatId, participante.id)) {
            android.util.Log.w("Corro", "AUDIO: ${participante.name} no es miembro de ${cabecera.chatId}")
            return
        }

        manejarAudioCliente(frame) // añadir localmente (el host también lo ve)
        enrutar(frame, cabecera.chatId, cabecera.senderId)
    }

    private fun manejarAudio(frame: Frame, remoteId: String) {
        if (modoHost) manejarAudioHost(frame, remoteId) else manejarAudioCliente(frame)
    }

    /** Expone el directorio de caché de la app al ViewModel para ficheros temporales de audio. */
    fun obtenerCacheDir(): java.io.File = app.cacheDir

    private fun manejarImage(frame: Frame, remoteId: String) {
        if (modoHost) manejarImageHost(frame, remoteId) else manejarImageCliente(frame)
    }

    /**
     * Parsea la cabecera JSON y los bytes JPEG de una trama IMAGE.
     * Formato del cuerpo: [4B longitud cabecera BE][bytes JSON][bytes JPEG].
     * Devuelve null si el formato es inválido.
     */
    private fun parsearTramImagen(frame: Frame): Triple<ImageBody, ByteArray, String>? {
        return try {
            val buf = ByteBuffer.wrap(frame.body)
            val jsonLen = buf.int
            val jsonBytes = ByteArray(jsonLen)
            buf.get(jsonBytes)
            val cabecera = json.decodeFromString<ImageBody>(jsonBytes.decodeToString())
            val jpegBytes = ByteArray(buf.remaining())
            buf.get(jpegBytes)
            val etiqueta = "${jpegBytes.size / 1024} KB · por red local"
            Triple(cabecera, jpegBytes, etiqueta)
        } catch (_: Exception) {
            null
        }
    }

    /** El cliente recibe IMAGE del servidor: añade el mensaje al hilo del chat correspondiente. */
    private fun manejarImageCliente(frame: Frame) {
        val (cabecera, jpegBytes, etiqueta) = parsearTramImagen(frame) ?: return
        agregarMensaje(Message(
            id = cabecera.id,
            senderId = cabecera.senderId,
            senderName = cabecera.senderName,
            type = MessageType.IMAGE,
            imageBytes = jpegBytes,
            imageSizeLabel = etiqueta,
            timestamp = cabecera.timestamp,
            isOwn = false,
            chatId = cabecera.chatId,
        ))
    }

    /**
     * El servidor recibe IMAGE de un cliente.
     * Valida senderId y membresía antes de añadir al hilo y enrutar.
     * El descifrado ya ocurrió en [Connection]; el re-cifrado por salto ocurre en
     * [enrutar] al pasar por el cifrador de cada conexión de destino.
     */
    private fun manejarImageHost(frame: Frame, remoteId: String) {
        val (cabecera, _, _) = parsearTramImagen(frame) ?: return

        val participante = clientParticipants[remoteId] ?: return
        if (cabecera.senderId != participante.id) {
            android.util.Log.w("Corro", "IMAGE: senderId no coincide con remoteId $remoteId")
            return
        }
        if (!esMiembroDelChat(cabecera.chatId, participante.id)) {
            android.util.Log.w("Corro", "IMAGE: ${participante.name} no es miembro de ${cabecera.chatId}")
            return
        }

        manejarImageCliente(frame) // añadir localmente
        enrutar(frame, cabecera.chatId, cabecera.senderId)
    }

    /**
     * Cierre ordenado (BYE recibido).
     * - Host: el cliente que envió BYE sale de la sala (también si era pendiente).
     * - Cliente: el servidor cerró la sala. No reintenta reconexión.
     */
    private fun manejarBye(remoteId: String) {
        if (modoHost) {
            // Si era una solicitud pendiente, limpiar sin LEAVE
            val pendingEntry = pendingEntries.remove(remoteId)
            if (pendingEntry != null) {
                pendingEntry.timerJob.cancel()
                clients.remove(remoteId)?.close()
                _pendingRequests.update { it.filter { r -> r.remoteId != remoteId } }
                return
            }

            val participant = clientParticipants.remove(remoteId) ?: return
            clients.remove(remoteId)?.close()

            val listaActualizada = construirListaParticipantes()
            val nuevoEstado = if (listaActualizada.size > 1) ConnectionStatus.ONLINE
                              else ConnectionStatus.DISCONNECTED
            _roomState.update { it.copy(participants = listaActualizada, connectionStatus = nuevoEstado) }

            val cuerpoLeave = json.encodeToString(JoinLeaveBody(participant.id, participant.name))
            difundir(Frame(FrameType.LEAVE, cuerpoLeave.encodeToByteArray()))
            agregarMensaje(mensajeSistema("${participant.name} ha salido"))
        } else {
            // BYE ordenado: no intentar reconectar
            connectionCliente?.close()
            connectionCliente = null
            // La sesión cifrada se cerró ordenadamente; quitar el candado
            _roomState.update { it.copy(connectionStatus = ConnectionStatus.DISCONNECTED, isEncrypted = false) }
            agregarMensaje(mensajeSistema("El servidor cerró la sala"))
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Reconexión automática (modo cliente)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Intenta reconectar al último servidor conocido con backoff exponencial.
     * Muestra [ConnectionStatus.RECONNECTING] en la UI durante los intentos.
     * Tras agotar [MAX_INTENTOS_RECONEXION], muestra [ConnectionStatus.DISCONNECTED].
     *
     * El ciclo se abandona si:
     * - [reconectando] pasa a falso (el usuario llamó a [desconectar])
     * - Se recibe WELCOME (conexión restablecida)
     * - Se recibe REJECT (rechazo definitivo)
     */
    private suspend fun intentarReconectar() {
        if (reconectando || modoHost || reconexionBloqueada) return
        val ip = _roomState.value.hostIp
        val puerto = _roomState.value.port
        if (ip.isBlank() || puerto == 0) return

        reconectando = true
        // La sesión cifrada se perdió con el socket; el candado vuelve tras el nuevo WELCOME
        _roomState.update { it.copy(connectionStatus = ConnectionStatus.RECONNECTING, isEncrypted = false) }
        agregarMensaje(mensajeSistema("Conexión perdida. Reconectando…"))

        for (intento in 1..MAX_INTENTOS_RECONEXION) {
            if (!reconectando) return // el usuario llamó desconectar()

            val retraso = BACKOFF_MS.getOrElse(intento - 1) { BACKOFF_MS.last() }
            delay(retraso)

            if (!reconectando) return

            try {
                val socket = chatClient.conectar(ip, puerto)
                val usuario = localUser ?: break

                val conn = Connection(
                    socket = socket,
                    scope = scope,
                    onFrame = { frame -> procesarTrama(frame, "") },
                    onDisconnect = ::manejarDesconexionServidor,
                )
                connectionCliente = conn

                // Nueva sesión ECDH por cada reconexión (Fase 5): las claves anteriores no son válidas
                val handshake = Handshake()
                clienteHandshake = handshake
                val pubKeyB64 = Base64.encodeToString(handshake.publicKeyBytes(), Base64.NO_WRAP)

                val cuerpoHello = json.encodeToString(
                    HelloBody(usuario.id, usuario.name, roomCode = codigoSalaCliente, publicKey = pubKeyB64,
                        joinMethod = metodoConexionCliente)
                )
                conn.send(Frame(FrameType.HELLO, cuerpoHello.encodeToByteArray()))
                // reconectando se pone a false en manejarWelcome o manejarReject
                return
            } catch (_: Exception) {
                // Este intento falló; continuar con el siguiente delay
            }
        }

        // Agotados los intentos
        if (reconectando) {
            reconectando = false
            _roomState.update { it.copy(connectionStatus = ConnectionStatus.DISCONNECTED) }
            _error.tryEmit("No se pudo reconectar. Verifica la conexión de red.")
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Enrutado en el host (Fase 9)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Enruta [frame] al/los destinatarios correctos según [chatId].
     * Solo se usa en modo host para tramas TEXT/IMAGE/AUDIO.
     *
     * - "sala"  → todos los clientes conectados menos el emisor.
     * - "dm:*"  → solo al otro participante del par privado.
     * - "grp:*" → a los miembros del grupo menos el emisor (Fase 12).
     *
     * Si un destinatario es el host, ya recibió el mensaje localmente en el
     * manejador correspondiente; aquí solo se envía a los clientes (via socket).
     * Si un destinatario está desconectado, el mensaje se descarta sin almacenar.
     *
     * @param frame     Trama a reenviar (ya incluye chatId en el cuerpo).
     * @param chatId    Identificador del chat destino extraído del cuerpo.
     * @param emisorId  participantId del emisor para excluirle del envío.
     */
    private fun enrutar(frame: Frame, chatId: String, emisorId: String) {
        // remoteId del emisor: null si el emisor es el host (no tiene socket)
        val emisorRemoteId = remoteIdDeParticipante(emisorId)

        when {
            chatId == "sala" -> {
                // Chat general: a todos los clientes menos al emisor
                difundir(frame, excluirId = emisorRemoteId)
            }
            chatId.startsWith("dm:") -> {
                // Privado: extraer el id del otro participante del par
                val partes = chatId.removePrefix("dm:").split(":")
                if (partes.size != 2) return
                val destinatarioId = partes.firstOrNull { it != emisorId } ?: return
                val destinatarioRemoteId = remoteIdDeParticipante(destinatarioId) ?: return
                // Si el destinatario es el host ya recibió en local; aquí solo va a clientes
                clients[destinatarioRemoteId]?.send(frame)
            }
            chatId.startsWith("grp:") -> {
                // Grupo: enviar a los miembros conectados menos al emisor (Fase 12)
                val grupo = grupos[chatId] ?: return
                grupo.miembros.forEach { miembroId ->
                    if (miembroId != emisorId) {
                        val destRemoteId = remoteIdDeParticipante(miembroId) ?: return@forEach
                        clients[destRemoteId]?.send(frame)
                    }
                }
            }
            else -> android.util.Log.w("Corro", "enrutar: chatId desconocido «$chatId»")
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Gestión de grupos (Fase 12)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * El host recibe CHAT_CREATE de un cliente: valida, genera el chatId y envía
     * CHAT_INFO a todos los miembros.
     */
    private fun manejarChatCreate(frame: Frame, remoteId: String) {
        try {
            val createBody = json.decodeFromString<ChatCreateBody>(frame.body.decodeToString())
            val creador = clientParticipants[remoteId] ?: return

            val todosLosMiembros = (createBody.memberIds + creador.id).distinct()
            if (todosLosMiembros.size < 3) {
                android.util.Log.w("Corro", "CHAT_CREATE: se necesitan mínimo 3 miembros")
                return
            }

            val chatId = "grp:${UUID.randomUUID()}"
            registrarGrupoLocal(chatId, createBody.name, todosLosMiembros, creador.name)

            val infoBody = ChatInfoBody(chatId, createBody.name, todosLosMiembros, creador.id, creador.name)
            val tramaChatInfo = Frame(FrameType.CHAT_INFO, json.encodeToString(infoBody).encodeToByteArray())
            // Enviar CHAT_INFO a todos los miembros que son clientes (el host ya registró en local)
            todosLosMiembros.forEach { miembroId ->
                val destRemoteId = remoteIdDeParticipante(miembroId) ?: return@forEach
                clients[destRemoteId]?.send(tramaChatInfo)
            }
        } catch (e: Exception) {
            android.util.Log.e("Corro", "Error en CHAT_CREATE: ${e.message}")
        }
    }

    /**
     * El cliente recibe CHAT_INFO del host: registra el grupo localmente.
     * Si el usuario local es el creador, emite [nuevoChatId] para navegar automáticamente.
     */
    private fun manejarChatInfo(frame: Frame) {
        try {
            val infoBody = json.decodeFromString<ChatInfoBody>(frame.body.decodeToString())
            if (infoBody.chatId == "sala") {
                // Renombrado de sala por el host
                _roomState.update { it.copy(roomName = infoBody.name) }
                _chats.update { lista ->
                    lista.map { if (it.chatId == "sala") it.copy(nombre = infoBody.name) else it }
                }
                agregarMensaje(mensajeSistema("El anfitrión renombró la sala a «${infoBody.name}»"))
                return
            }
            registrarGrupoLocal(infoBody.chatId, infoBody.name, infoBody.memberIds, infoBody.creatorName)
            if (infoBody.creatorId.isNotBlank() && infoBody.creatorId == localUser?.id) {
                scope.launch { _nuevoChatId.emit(infoBody.chatId) }
            }
        } catch (e: Exception) {
            android.util.Log.e("Corro", "Error en CHAT_INFO: ${e.message}")
        }
    }

    /**
     * Registra el grupo en [grupos] y en [_chats]. Añade el mensaje de sistema
     * «[creadorNombre] creó el grupo» si [creadorNombre] no está vacío (omitido al reconectar).
     */
    private fun registrarGrupoLocal(chatId: String, nombre: String, miembros: List<String>, creadorNombre: String) {
        val summary = ChatSummary(chatId = chatId, tipo = ChatTipo.GRUPO, nombre = nombre, miembros = miembros)
        grupos[chatId] = summary
        _chats.update { lista ->
            if (lista.any { it.chatId == chatId }) lista else lista + summary
        }
        if (creadorNombre.isNotBlank()) {
            agregarMensaje(Message(
                type = MessageType.SYSTEM,
                text = "$creadorNombre creó el grupo",
                timestamp = System.currentTimeMillis(),
                chatId = chatId,
            ))
        }
    }

    /**
     * Envía [frame] a todos los clientes conectados, excepto al identificado por [excluirId].
     * Se usa para tramas de control (JOIN, LEAVE, BYE) y como parte de [enrutar] para "sala".
     */
    private fun difundir(frame: Frame, excluirId: String? = null) {
        clients.forEach { (id, conn) -> if (id != excluirId) conn.send(frame) }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Utilidades internas
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Verifica si [participantId] tiene derecho a enviar/recibir en [chatId].
     * - sala → todos los participantes son miembros.
     * - dm:* → el participante debe estar en el par de ids del chatId.
     * - grp:* → el participante debe estar en [grupos][chatId].miembros.
     */
    private fun esMiembroDelChat(chatId: String, participantId: String): Boolean {
        return when {
            chatId == "sala" -> true
            chatId.startsWith("dm:") -> {
                val partes = chatId.removePrefix("dm:").split(":")
                partes.size == 2 && participantId in partes
            }
            chatId.startsWith("grp:") -> {
                grupos[chatId]?.miembros?.contains(participantId) == true
            }
            else -> false
        }
    }

    /** Busca el remoteId (clave de socket) de un participante dado su participantId. */
    private fun remoteIdDeParticipante(participantId: String): String? =
        clientParticipants.entries.firstOrNull { it.value.id == participantId }?.key

    private fun construirListaParticipantes(): List<Participant> {
        val lista = mutableListOf<Participant>()
        localUser?.let { lista.add(it) }
        clientParticipants.values.forEach { lista.add(it) }
        return lista.toList()
    }

    fun obtenerIpLocal(): String {
        return try {
            NetworkInterface.getNetworkInterfaces()
                ?.toList()
                ?.filter { it.isUp && !it.isLoopback && !it.isVirtual }
                ?.flatMap { it.inetAddresses.toList() }
                ?.filterIsInstance<Inet4Address>()
                ?.firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress
                ?: "0.0.0.0"
        } catch (_: Exception) {
            "0.0.0.0"
        }
    }

    private fun generarCodigoSala(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..4).map { chars.random() }.joinToString("")
    }

    /** Infiere el [ChatTipo] a partir del prefijo del [chatId]. */
    private fun tipoDeChatId(chatId: String) = when {
        chatId == "sala"              -> ChatTipo.SALA
        chatId.startsWith("dm:")  -> ChatTipo.PRIVADO
        chatId.startsWith("grp:") -> ChatTipo.GRUPO
        else                          -> ChatTipo.SALA
    }

    /**
     * Infiere el nombre visible de un chat a partir de su [chatId].
     * Para dm: busca el nombre del otro participante en la lista actual.
     * Para grp: usa el nombre almacenado en [grupos].
     */
    private fun nombreDeChatId(chatId: String): String = when {
        chatId == "sala" -> "Chat general"
        chatId.startsWith("dm:") -> {
            val partes = chatId.removePrefix("dm:").split(":")
            val otroId = partes.firstOrNull { it != localUser?.id } ?: chatId
            _roomState.value.participants.firstOrNull { it.id == otroId }?.name ?: chatId
        }
        chatId.startsWith("grp:") -> grupos[chatId]?.nombre ?: "Grupo"
        else -> chatId
    }

    /** Crea un mensaje de sistema para el chat sala con la [hora] actual. */
    private fun mensajeSistema(texto: String) = Message(
        type = MessageType.SYSTEM,
        text = texto,
        timestamp = System.currentTimeMillis(),
        chatId = "sala",
    )
}
