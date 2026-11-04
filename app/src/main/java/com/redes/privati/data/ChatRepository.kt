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
import com.redes.privati.net.ImageBody
import com.redes.privati.net.JoinLeaveBody
import com.redes.privati.net.ParticipantInfo
import com.redes.privati.net.RejectBody
import com.redes.privati.net.TextBody
import com.redes.privati.net.WelcomeBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
 * HELLO/WELCOME, relay de mensajes, avisos JOIN/LEAVE, rechazo y expulsión.
 *
 * ## Modos de operación
 * - **Host (servidor)**: acepta múltiples clientes (máx. [MAX_CLIENTES]).
 *   En `manejarHello` rechaza sala llena y nombres repetidos. Relay por salto:
 *   cuando recibe TEXT de un cliente lo reenvía a todos los demás.
 * - **Cliente**: una sola [connectionCliente] hacia el servidor.
 *   Si la conexión se pierde inesperadamente, intenta reconectar con backoff
 *   exponencial hasta [MAX_INTENTOS_RECONEXION] veces.
 *
 * ## Protocolo de cifrado (Fase 5)
 * En la Fase 5 se añadirá ECDH + AES-GCM justo antes de [Connection.send] y
 * tras recibir en [procesarTrama].
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

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _roomState = MutableStateFlow(RoomState())
    val roomState: StateFlow<RoomState> = _roomState.asStateFlow()

    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val error: SharedFlow<String> = _error.asSharedFlow()

    // ──────────────────────────────────────────────────────────────────────────
    // Estado interno de red
    // ──────────────────────────────────────────────────────────────────────────

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Descubrimiento NSD ────────────────────────────────────────────────────
    /** Anuncia la sala vía mDNS mientras el servidor está activo (Fase 4). */
    private val nsdAdvertiser = NsdAdvertiser(app)

    /** Busca salas Corro por código en la red local (Fase 4). */
    private val nsdBrowser = NsdBrowser(app)

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
        _roomState.value = RoomState(
            localUserId = usuario.id,
            roomCode = generarCodigoSala(),
            hostIp = ip,
            port = puerto,
            participants = listOf(usuario),
            connectionStatus = ConnectionStatus.DISCONNECTED,
        )

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

        // Anunciar la sala vía NSD para que los clientes la encuentren por código (Fase 4)
        nsdAdvertiser.start(_roomState.value.roomCode, puerto)
    }

    /**
     * Conecta como cliente al servidor en [ip]:[puerto].
     * Envía HELLO (con [roomCode] si se proporcionó) y espera WELCOME.
     * Llamar siempre después de [establecerUsuarioLocal].
     *
     * @param ip       Dirección IPv4 del servidor.
     * @param puerto   Puerto TCP del servidor.
     * @param roomCode Código de sala enviado en HELLO; vacío cuando se conecta por IP manual.
     */
    suspend fun conectarComoCliente(ip: String, puerto: Int, roomCode: String = "") {
        val usuario = localUser ?: return
        modoHost = false
        reconexionBloqueada = false // nuevo intento manual: limpiar bloqueo previo
        codigoSalaCliente = roomCode // persistir para reenviar en reconexión automática

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
            )}

            // Generar par de claves ECDH para esta sesión (Fase 5)
            val handshake = Handshake()
            clienteHandshake = handshake
            val pubKeyB64 = Base64.encodeToString(handshake.publicKeyBytes(), Base64.NO_WRAP)

            val cuerpoHello = json.encodeToString(
                HelloBody(usuario.id, usuario.name, roomCode = roomCode, publicKey = pubKeyB64)
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
            conectarComoCliente(ip, port, roomCode = codigoSala)
        } catch (e: Exception) {
            _error.tryEmit(e.message ?: "No se pudo encontrar la sala")
        }
    }

    /**
     * Selecciona, comprime y envía una imagen.
     *
     * El bitmap se reescala a 1280 px en el lado largo (si es necesario) y se
     * comprime a JPEG calidad 80 en [Dispatchers.Default] antes de enviar.
     * El cuerpo de la trama IMAGE tiene el formato:
     * ```
     * [4B longitud de cabecera BE][bytes JSON][bytes JPEG]
     * ```
     * El cifrado AES-GCM se aplica automáticamente en la capa [Connection].
     *
     * @param uri URI de la imagen seleccionada con [ActivityResultContracts.PickVisualMedia].
     */
    suspend fun enviarImagen(uri: Uri) {
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

        _messages.update { lista ->
            lista + Message(
                id = id,
                senderId = usuario.id,
                senderName = usuario.name,
                type = MessageType.IMAGE,
                imageBytes = jpegBytes,
                imageSizeLabel = etiqueta,
                timestamp = timestamp,
                isOwn = true,
            )
        }

        // Serializar cuerpo: [4B longitud cabecera BE][bytes JSON cabecera][bytes JPEG]
        val cabeceraJson = json.encodeToString(ImageBody(id, usuario.id, usuario.name, timestamp, jpegBytes.size))
        val cabeceraBytes = cabeceraJson.encodeToByteArray()
        val cuerpo = ByteBuffer.allocate(4 + cabeceraBytes.size + jpegBytes.size)
            .putInt(cabeceraBytes.size)
            .put(cabeceraBytes)
            .put(jpegBytes)
            .array()

        val trama = Frame(FrameType.IMAGE, cuerpo)
        if (modoHost) difundir(trama) else connectionCliente?.send(trama)
    }

    /** Envía texto: pinta localmente + relay a clientes (host) o envía al servidor (cliente). */
    fun enviarTexto(texto: String) {
        val usuario = localUser ?: return

        val id = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        _messages.update { lista ->
            lista + Message(
                id = id,
                senderId = usuario.id,
                senderName = usuario.name,
                text = texto,
                timestamp = timestamp,
                isOwn = true,
            )
        }

        val cuerpo = json.encodeToString(TextBody(id, usuario.id, usuario.name, texto, timestamp))
        val frame = Frame(FrameType.TEXT, cuerpo.encodeToByteArray())

        if (modoHost) difundir(frame) else connectionCliente?.send(frame)
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
        _messages.update { it + mensajeSistema("${participant.name} ha sido expulsado") }
    }

    /** Envía BYE, cierra todas las conexiones y limpia el estado. */
    fun desconectar() {
        reconectando = false // cancelar cualquier bucle de reconexión en curso
        if (modoHost) {
            difundir(Frame(FrameType.BYE, ByteArray(0)))
            clients.values.forEach { it.close() }
            clients.clear()
            clientParticipants.clear()
            expulsadosIds.clear() // la sala se cierra; la lista negra no persiste
            // Retirar el anuncio NSD antes de parar el servidor (Fase 4)
            nsdAdvertiser.stop()
            server?.stop()
            server = null
        } else {
            connectionCliente?.send(Frame(FrameType.BYE, ByteArray(0)))
            connectionCliente?.close()
            connectionCliente = null
        }
        _roomState.value = RoomState()
        _messages.value = emptyList()
        localUser = null
        modoHost = false
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Enrutado de tramas
    // ──────────────────────────────────────────────────────────────────────────

    private fun procesarTrama(frame: Frame, remoteId: String) {
        when (frame.type) {
            FrameType.HELLO -> if (modoHost) manejarHello(frame, remoteId)
            FrameType.WELCOME -> if (!modoHost) manejarWelcome(frame)
            FrameType.REJECT -> if (!modoHost) manejarReject(frame)
            FrameType.TEXT -> manejarText(frame, remoteId)
            FrameType.JOIN -> if (!modoHost) manejarJoin(frame)
            FrameType.LEAVE -> if (!modoHost) manejarLeave(frame)
            FrameType.BYE -> manejarBye(remoteId)
            FrameType.IMAGE -> manejarImage(frame, remoteId)
            FrameType.AUDIO -> manejarAudio(frame, remoteId)
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
     * Rechaza si la sala está llena o el nombre ya existe (evita suplantación).
     * En caso contrario, registra, envía WELCOME con lista completa y avisa con JOIN.
     */
    private fun manejarHello(frame: Frame, remoteId: String) {
        val host = localUser ?: return
        try {
            val hello = json.decodeFromString<HelloBody>(frame.body.decodeToString())

            // ── Comprobaciones de rechazo ─────────────────────────────────────
            // Participante previamente expulsado por el host
            if (expulsadosIds.contains(hello.senderId)) {
                rechazar(remoteId, "Has sido expulsado de esta sala")
                return
            }
            // Código de sala incorrecto (solo se valida si el cliente lo envió; Fase 4+)
            val codigoDeLaSala = _roomState.value.roomCode
            if (hello.roomCode.isNotBlank() &&
                !hello.roomCode.equals(codigoDeLaSala, ignoreCase = true)
            ) {
                rechazar(remoteId, "Código de sala incorrecto")
                return
            }
            // Sala llena
            if (clientParticipants.size >= MAX_CLIENTES) {
                rechazar(remoteId, "La sala está llena (máximo $MAX_CLIENTES participantes)")
                return
            }
            // Nombre ya en uso: comprobar tanto clientes como el nombre del propio host
            val nombreDelHost = localUser?.name ?: ""
            val nombreRepetido = nombreDelHost.equals(hello.senderName, ignoreCase = true) ||
                clientParticipants.values.any {
                    it.name.equals(hello.senderName, ignoreCase = true) && it.id != hello.senderId
                }
            if (nombreRepetido) {
                rechazar(remoteId, "El nombre «${hello.senderName}» ya está en uso en esta sala")
                return
            }

            // ── Registrar participante ────────────────────────────────────────
            val nuevoParticipante = Participant(id = hello.senderId, name = hello.senderName)
            clientParticipants[remoteId] = nuevoParticipante
            val listaParticipantes = construirListaParticipantes()

            _roomState.update { it.copy(
                participants = listaParticipantes,
                connectionStatus = ConnectionStatus.ONLINE,
            )}

            // ── Handshake ECDH (Fase 5) ───────────────────────────────────────
            // Si el cliente envió su clave pública, generamos la nuestra, derivamos
            // la clave de sesión e instalamos el cifrador en la conexión.
            // Cifrado por salto: cada canal cliente↔host tiene su propia clave;
            // el host descifra cada trama entrante y la vuelve a cifrar al reenviarla.
            var hostPublicKeyB64 = ""
            if (hello.publicKey.isNotBlank()) {
                try {
                    val peerKeyBytes = Base64.decode(hello.publicKey, Base64.NO_WRAP)
                    val handshake = Handshake()
                    val cipher = handshake.deriveSessionCipher(
                        peerKeyBytes,
                        roomCode = hello.roomCode,
                        isClient = false,  // este extremo es el servidor
                    )
                    clients[remoteId]?.cipher = cipher
                    hostPublicKeyB64 = Base64.encodeToString(handshake.publicKeyBytes(), Base64.NO_WRAP)
                    _roomState.update { it.copy(isEncrypted = true) }
                } catch (e: Exception) {
                    // Clave malformada o ECDH fallido: continuar sin cifrado para esta conexión
                }
            }

            // WELCOME al nuevo cliente con la lista completa y la clave pública del host
            val infoParticipantes = listaParticipantes.map { p ->
                ParticipantInfo(p.id, p.name, p.isHost)
            }
            val cuerpoWelcome = json.encodeToString(
                WelcomeBody(
                    assignedId = hello.senderId,
                    hostId = host.id,
                    hostName = host.name,
                    participants = infoParticipantes,
                    publicKey = hostPublicKeyB64,
                )
            )
            clients[remoteId]?.send(Frame(FrameType.WELCOME, cuerpoWelcome.encodeToByteArray()))

            // JOIN a los demás clientes (no al recién llegado)
            val cuerpoJoin = json.encodeToString(JoinLeaveBody(hello.senderId, hello.senderName))
            difundir(Frame(FrameType.JOIN, cuerpoJoin.encodeToByteArray()), excluirId = remoteId)

            _messages.update { it + mensajeSistema("${hello.senderName} se ha unido") }
        } catch (e: Exception) {
            rechazar(remoteId, "Formato de identificación incorrecto")
        }
    }

    /** Envía REJECT y cierra la conexión del cliente identificado por [remoteId]. */
    private fun rechazar(remoteId: String, motivo: String) {
        val cuerpoReject = json.encodeToString(RejectBody(motivo))
        clients[remoteId]?.send(Frame(FrameType.REJECT, cuerpoReject.encodeToByteArray()))
        clients.remove(remoteId)?.close()
        clientParticipants.remove(remoteId)
    }

    /** El servidor recibe TEXT de un cliente: lo añade al hilo y lo reenvía (relay). */
    private fun manejarTextHost(frame: Frame, remoteId: String) {
        try {
            val textBody = json.decodeFromString<TextBody>(frame.body.decodeToString())
            _messages.update { lista ->
                lista + Message(
                    id = textBody.id,
                    senderId = textBody.senderId,
                    senderName = textBody.senderName,
                    text = textBody.text,
                    timestamp = textBody.timestamp,
                    isOwn = false,
                )
            }
            difundir(frame, excluirId = remoteId)
        } catch (_: Exception) {}
    }

    /** Desconexión inesperada de un cliente: limpia la sesión y avisa a los demás. */
    private fun manejarDesconexionCliente(remoteId: String) {
        val participant = clientParticipants.remove(remoteId) ?: return
        clients.remove(remoteId)?.close()

        val listaActualizada = construirListaParticipantes()
        val nuevoEstado = if (listaActualizada.size > 1) ConnectionStatus.ONLINE
                          else ConnectionStatus.DISCONNECTED
        _roomState.update { it.copy(participants = listaActualizada, connectionStatus = nuevoEstado) }

        val cuerpoLeave = json.encodeToString(JoinLeaveBody(participant.id, participant.name))
        difundir(Frame(FrameType.LEAVE, cuerpoLeave.encodeToByteArray()))
        _messages.update { it + mensajeSistema("${participant.name} se ha desconectado") }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Manejadores — Modo cliente
    // ──────────────────────────────────────────────────────────────────────────

    private fun manejarWelcome(frame: Frame) {
        val cliente = localUser ?: return
        try {
            val welcome = json.decodeFromString<WelcomeBody>(frame.body.decodeToString())
            val participantes = if (welcome.participants.isNotEmpty()) {
                welcome.participants.map { info ->
                    Participant(id = info.id, name = info.name, isHost = info.isHost)
                }
            } else {
                listOf(
                    Participant(id = welcome.hostId, name = welcome.hostName, isHost = true),
                    cliente,
                )
            }
            _roomState.update { it.copy(participants = participantes, connectionStatus = ConnectionStatus.ONLINE) }
            reconectando = false // reconexión completada con éxito

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

    /** El servidor rechazó la conexión (sala llena, expulsado, nombre en uso…). */
    private fun manejarReject(frame: Frame) {
        try {
            val reject = json.decodeFromString<RejectBody>(frame.body.decodeToString())
            _error.tryEmit(reject.reason)
        } catch (_: Exception) {
            _error.tryEmit("Conexión rechazada.")
        }
        reconexionBloqueada = true  // un rechazo es definitivo; no reintentar aunque caiga el socket
        reconectando = false
        connectionCliente?.close()
        connectionCliente = null
        // La sesión cifrada ya no existe; ocultar el candado hasta la próxima conexión
        _roomState.update { it.copy(connectionStatus = ConnectionStatus.DISCONNECTED, isEncrypted = false) }
    }

    private fun manejarJoin(frame: Frame) {
        try {
            val join = json.decodeFromString<JoinLeaveBody>(frame.body.decodeToString())
            val nuevo = Participant(id = join.participantId, name = join.participantName)
            _roomState.update { it.copy(participants = it.participants + nuevo) }
            _messages.update { it + mensajeSistema("${join.participantName} se ha unido") }
        } catch (_: Exception) {}
    }

    private fun manejarLeave(frame: Frame) {
        try {
            val leave = json.decodeFromString<JoinLeaveBody>(frame.body.decodeToString())
            _roomState.update { it.copy(
                participants = it.participants.filter { p -> p.id != leave.participantId },
            )}
            _messages.update { it + mensajeSistema("${leave.participantName} ha salido") }
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
            _messages.update { lista ->
                lista + Message(
                    id = textBody.id,
                    senderId = textBody.senderId,
                    senderName = textBody.senderName,
                    text = textBody.text,
                    timestamp = textBody.timestamp,
                    isOwn = false,
                )
            }
        } catch (_: Exception) {}
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Envío y recepción de notas de voz (Fase 7)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Añade la nota de voz al hilo local y la envía como trama AUDIO.
     * El cuerpo de la trama tiene el formato:
     * ```
     * [4B longitud cabecera BE][bytes JSON][bytes AAC-MPEG4]
     * ```
     */
    fun enviarAudio(bytes: ByteArray, durationMs: Long, waveform: List<Float>) {
        val usuario = localUser ?: return

        val id = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        _messages.update { lista ->
            lista + Message(
                id = id,
                senderId = usuario.id,
                senderName = usuario.name,
                type = MessageType.AUDIO,
                audioBytes = bytes,
                audioDurationMs = durationMs,
                audioWaveform = waveform,
                timestamp = timestamp,
                isOwn = true,
            )
        }

        val cabeceraJson = json.encodeToString(
            AudioBody(id, usuario.id, usuario.name, timestamp, durationMs, bytes.size, waveform)
        )
        val cabeceraBytes = cabeceraJson.encodeToByteArray()
        val cuerpo = ByteBuffer.allocate(4 + cabeceraBytes.size + bytes.size)
            .putInt(cabeceraBytes.size)
            .put(cabeceraBytes)
            .put(bytes)
            .array()

        val trama = Frame(FrameType.AUDIO, cuerpo)
        if (modoHost) difundir(trama) else connectionCliente?.send(trama)
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

    /** El cliente recibe AUDIO del servidor: añade la nota al hilo. */
    private fun manejarAudioCliente(frame: Frame) {
        val (cabecera, aacBytes) = parsearTramaAudio(frame) ?: return
        _messages.update { lista ->
            lista + Message(
                id = cabecera.id,
                senderId = cabecera.senderId,
                senderName = cabecera.senderName,
                type = MessageType.AUDIO,
                audioBytes = aacBytes,
                audioDurationMs = cabecera.durationMs,
                audioWaveform = cabecera.waveform,
                timestamp = cabecera.timestamp,
                isOwn = false,
            )
        }
    }

    /** El servidor recibe AUDIO de un cliente: lo añade al hilo y hace relay. */
    private fun manejarAudioHost(frame: Frame, remoteId: String) {
        manejarAudioCliente(frame)
        difundir(frame, excluirId = remoteId)
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

    /** El cliente recibe IMAGE del servidor: añade el mensaje al hilo. */
    private fun manejarImageCliente(frame: Frame) {
        val (cabecera, jpegBytes, etiqueta) = parsearTramImagen(frame) ?: return
        _messages.update { lista ->
            lista + Message(
                id = cabecera.id,
                senderId = cabecera.senderId,
                senderName = cabecera.senderName,
                type = MessageType.IMAGE,
                imageBytes = jpegBytes,
                imageSizeLabel = etiqueta,
                timestamp = cabecera.timestamp,
                isOwn = false,
            )
        }
    }

    /**
     * El servidor recibe IMAGE de un cliente: lo añade al hilo y lo reenvía (relay).
     * El descifrado ya ocurrió en [Connection]; el re-cifrado por salto ocurre en
     * [difundir] al pasar por el cifrador de cada conexión de destino.
     */
    private fun manejarImageHost(frame: Frame, remoteId: String) {
        manejarImageCliente(frame)
        difundir(frame, excluirId = remoteId)
    }

    /**
     * Cierre ordenado (BYE recibido).
     * - Host: el cliente que envió BYE sale de la sala.
     * - Cliente: el servidor cerró la sala. No reintenta reconexión.
     */
    private fun manejarBye(remoteId: String) {
        if (modoHost) {
            val participant = clientParticipants.remove(remoteId) ?: return
            clients.remove(remoteId)?.close()

            val listaActualizada = construirListaParticipantes()
            val nuevoEstado = if (listaActualizada.size > 1) ConnectionStatus.ONLINE
                              else ConnectionStatus.DISCONNECTED
            _roomState.update { it.copy(participants = listaActualizada, connectionStatus = nuevoEstado) }

            val cuerpoLeave = json.encodeToString(JoinLeaveBody(participant.id, participant.name))
            difundir(Frame(FrameType.LEAVE, cuerpoLeave.encodeToByteArray()))
            _messages.update { it + mensajeSistema("${participant.name} ha salido") }
        } else {
            // BYE ordenado: no intentar reconectar
            connectionCliente?.close()
            connectionCliente = null
            // La sesión cifrada se cerró ordenadamente; quitar el candado
            _roomState.update { it.copy(connectionStatus = ConnectionStatus.DISCONNECTED, isEncrypted = false) }
            _messages.update { it + mensajeSistema("El servidor cerró la sala") }
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
        _messages.update { it + mensajeSistema("Conexión perdida. Reconectando…") }

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
                    HelloBody(usuario.id, usuario.name, roomCode = codigoSalaCliente, publicKey = pubKeyB64)
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
    // Utilidades internas
    // ──────────────────────────────────────────────────────────────────────────

    private fun difundir(frame: Frame, excluirId: String? = null) {
        clients.forEach { (id, conn) -> if (id != excluirId) conn.send(frame) }
    }

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

    private fun nombreDelOtroParticipante(): String? =
        _roomState.value.participants.firstOrNull { it.id != localUser?.id }?.name

    private fun mensajeSistema(texto: String) = Message(
        type = MessageType.SYSTEM,
        text = texto,
        timestamp = System.currentTimeMillis(),
    )
}
