package com.redes.privati.net

import com.redes.privati.crypto.SessionCipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.Socket

/**
 * Abstracción de una conexión TCP bidireccional con detección activa de caídas.
 *
 * Lanza tres corrutinas independientes en [Dispatchers.IO]:
 * - **Lectura**: bloquea en [FrameCodec.read] e invoca [onFrame] por cada trama.
 *   Actualiza [ultimaActividadMs] en cada trama recibida.
 * - **Escritura**: consume el canal [outgoing] y escribe tramas en orden.
 * - **Latido (heartbeat)**: envía [FrameType.PING] cada [PING_INTERVALO_MS].
 *   Si no llega ninguna trama en [TIMEOUT_MS], declara la conexión muerta e invoca
 *   [onDisconnect]. Esto detecta caídas de Wi-Fi donde no se genera un RST TCP.
 *
 * La desconexión local (vía [close]) se distingue de la remota gracias a [cerradoLocalmente]:
 * [onDisconnect] solo se invoca si el cierre no fue iniciado localmente.
 *
 * @param socket Socket TCP ya conectado.
 * @param scope Ámbito padre; las corrutinas se cancelan si el ámbito se cancela.
 * @param remoteId Identificador interno del extremo remoto (UUID generado localmente).
 * @param onFrame Callback por cada trama recibida, en el hilo IO.
 * @param onDisconnect Callback cuando el extremo remoto cierra o pierde la conexión.
 */
class Connection(
    private val socket: Socket,
    scope: CoroutineScope,
    val remoteId: String = java.util.UUID.randomUUID().toString(),
    private val onFrame: (Frame) -> Unit,
    private val onDisconnect: () -> Unit,
) {
    companion object {
        /** Intervalo entre PINGs en milisegundos. */
        const val PING_INTERVALO_MS = 5_000L

        /**
         * Tiempo máximo sin recibir ninguna trama antes de declarar la conexión muerta.
         * 60 s da margen suficiente para que el usuario abra la cámara u otras apps
         * mientras Android throttlea las corrutinas en segundo plano.
         */
        const val TIMEOUT_MS = 60_000L
    }

    /** Canal ilimitado de tramas pendientes de enviar. trySend() nunca bloquea. */
    private val outgoing = Channel<Frame>(Channel.UNLIMITED)

    /** Indica que el cierre fue iniciado localmente; suprime la llamada a [onDisconnect]. */
    @Volatile
    private var cerradoLocalmente = false

    /**
     * Cifrador de sesión AES-256-GCM instalado tras el handshake ECDH (Fase 5).
     * Null hasta que el intercambio de claves HELLO/WELCOME se complete.
     * HELLO y WELCOME siempre se envían/reciben sin cifrar, independientemente de este campo.
     * @Volatile garantiza visibilidad entre el hilo que instala el cifrador (IO) y los
     * bucles de lectura/escritura.
     */
    @Volatile
    var cipher: SessionCipher? = null

    /**
     * Marca de tiempo de la última trama recibida (cualquier tipo).
     * Inicializada al momento de creación para evitar falso positivo en el primer ciclo.
     */
    @Volatile
    private var ultimaActividadMs = System.currentTimeMillis()

    private val connectionJob = Job(scope.coroutineContext[Job])
    private val connectionScope = CoroutineScope(scope.coroutineContext + connectionJob)

    init {
        // Corrutina de lectura: bloquea en FrameCodec.read hasta que llega una trama
        connectionScope.launch(Dispatchers.IO) {
            try {
                val input = socket.getInputStream()
                while (isActive) {
                    val rawFrame = FrameCodec.read(input)
                    ultimaActividadMs = System.currentTimeMillis() // cualquier trama cuenta
                    onFrame(desencriptarSiCorresponde(rawFrame))
                }
            } catch (e: Exception) {
                if (!cerradoLocalmente) onDisconnect()
            } finally {
                cerrarSocketSilencioso()
            }
        }

        // Corrutina de escritura: espera tramas en el canal y las escribe en orden
        connectionScope.launch(Dispatchers.IO) {
            try {
                val output = socket.getOutputStream()
                for (frame in outgoing) {
                    FrameCodec.write(output, encriptarSiCorresponde(frame))
                }
            } catch (e: Exception) {
                if (!cerradoLocalmente) onDisconnect()
            } finally {
                cerrarSocketSilencioso()
            }
        }

        // Corrutina de latido: PING periódico + vigilancia de timeout
        connectionScope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    delay(PING_INTERVALO_MS)
                    val tiempoSilencio = System.currentTimeMillis() - ultimaActividadMs
                    if (tiempoSilencio > TIMEOUT_MS) {
                        // Sin actividad en 15 s: conexión considerada muerta
                        if (!cerradoLocalmente) onDisconnect()
                        break
                    }
                    outgoing.trySend(Frame(FrameType.PING, ByteArray(0)))
                }
            } finally {
                cerrarSocketSilencioso()
            }
        }
    }

    /**
     * Encola una trama para su envío. Nunca bloquea; usa un canal ilimitado.
     * Si la conexión ya está cerrada, la trama se descarta silenciosamente.
     */
    fun send(frame: Frame) {
        outgoing.trySend(frame)
    }

    /**
     * Cierra la conexión limpiamente: marca el cierre como local, cierra el canal
     * de salida, cancela las corrutinas y cierra el socket.
     */
    fun close() {
        cerradoLocalmente = true
        outgoing.close()
        connectionJob.cancel()
        cerrarSocketSilencioso()
    }

    private fun cerrarSocketSilencioso() {
        try { socket.close() } catch (_: Exception) {}
    }

    /**
     * Cifra el cuerpo de [frame] si el [cipher] está instalado y el tipo no es
     * HELLO ni WELCOME (esas tramas siempre viajan en claro para el handshake).
     */
    private fun encriptarSiCorresponde(frame: Frame): Frame {
        val c = cipher ?: return frame
        if (frame.type == FrameType.HELLO || frame.type == FrameType.WELCOME) return frame
        return try {
            Frame(frame.type, c.encrypt(frame.body))
        } catch (e: Exception) {
            throw IOException("Error al cifrar trama ${frame.type}", e)
        }
    }

    /**
     * Descifra el cuerpo de [frame] si el [cipher] está instalado y el tipo no es
     * HELLO ni WELCOME.
     * Lanza [IOException] si la autenticación GCM falla: la conexión se cierra
     * por el manejador de excepciones del bucle de lectura.
     */
    private fun desencriptarSiCorresponde(frame: Frame): Frame {
        val c = cipher ?: return frame
        if (frame.type == FrameType.HELLO || frame.type == FrameType.WELCOME) return frame
        return try {
            Frame(frame.type, c.decrypt(frame.body))
        } catch (e: SecurityException) {
            throw IOException("Fallo de autenticación AES-GCM en trama ${frame.type}: ${e.message}", e)
        }
    }
}
