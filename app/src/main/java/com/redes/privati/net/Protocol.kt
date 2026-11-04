package com.redes.privati.net

import kotlinx.serialization.Serializable
import java.io.IOException

/**
 * Tipos de trama del protocolo Privati.
 * Cada trama tiene la forma: [4 bytes longitud big-endian][1 byte tipo][cuerpo].
 * La longitud cubre el byte de tipo + el cuerpo.
 *
 * @param id Identificador de un solo byte que viaja por la red.
 */
enum class FrameType(val id: Byte) {
    /** Cliente → servidor: nombre, id y código de sala. */
    HELLO(1),

    /** Servidor → cliente: id asignado y datos del host. */
    WELCOME(2),

    /** Servidor → cliente: motivo de rechazo (sala llena, código incorrecto…). */
    REJECT(3),

    /** Ambos → ambos: mensaje de texto. */
    TEXT(4),

    /** Ambos → ambos: imagen JPEG. */
    IMAGE(5),

    /** Ambos → ambos: nota de voz AAC. */
    AUDIO(6),

    /** Servidor → todos: aviso de nueva conexión. */
    JOIN(7),

    /** Servidor → todos: aviso de desconexión. */
    LEAVE(8),

    /** Ambos → ambos: latido de comprobación. */
    PING(9),

    /** Ambos → ambos: respuesta al latido. */
    PONG(10),

    /** Ambos → ambos: cierre ordenado de la sesión. */
    BYE(11),

    /** Cliente → host: solicitud de creación de grupo con nombre y lista de miembros. */
    CHAT_CREATE(12),

    /** Host → miembros: confirmación del grupo creado con chatId, nombre y miembros. */
    CHAT_INFO(13),

    /** Host → cliente: solicitud de entrada recibida; el host debe aprobarla antes de WELCOME. */
    PENDING(14);

    companion object {
        private val porId = values().associateBy { it.id }

        /** Convierte un byte de la red al enum correspondiente.
         *  Lanza [IOException] si el byte no corresponde a ningún tipo conocido. */
        fun fromId(id: Byte): FrameType =
            porId[id] ?: throw IOException("Tipo de trama desconocido: 0x${(id.toInt() and 0xFF).toString(16)}")
    }
}

/** Trama cruda: tipo + cuerpo en bytes ya listos para serializar o deserializar. */
data class Frame(
    val type: FrameType,
    val body: ByteArray = ByteArray(0),
) {
    override fun equals(other: Any?) = other is Frame && type == other.type
    override fun hashCode() = type.hashCode()
}

// ──────────────────────────────────────────────────────────────────────────────
// Cuerpos de las tramas de control (JSON con kotlinx.serialization)
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Cuerpo de HELLO: identificación del cliente al conectarse.
 *
 * @param senderId   UUID estable del dispositivo cliente durante la sesión.
 * @param senderName Nombre elegido por el usuario.
 * @param roomCode   Código de sala de 4 caracteres (vacío si la conexión es por IP manual).
 * @param publicKey  Clave pública ECDH P-256 en Base64 (X.509 DER). Vacío en clientes sin Fase 5.
 *                   Si se incluye, el servidor responderá con su propia clave en WELCOME y se
 *                   activará el cifrado AES-256-GCM para la sesión.
 */
@Serializable
data class HelloBody(
    val senderId: String,
    val senderName: String,
    val roomCode: String = "",
    val publicKey: String = "",
    /** "nfc" | "code" | "ip" según cómo el cliente inició la conexión. */
    val joinMethod: String = "ip",
    /** Solo se envía cuando joinMethod="nfc"; permite al host validar la autenticidad. */
    val roomSecret: String = "",
)

/**
 * Información resumida de un participante, enviada dentro de WELCOME.
 * Permite al cliente recién llegado conocer a quién ya estaba en la sala.
 */
@Serializable
data class ParticipantInfo(
    val id: String,
    val name: String,
    val isHost: Boolean = false,
    val joinMethod: String = "",
    val joinedAt: Long = 0L,
)

/**
 * Cuerpo de WELCOME: confirmación del servidor con los datos del host
 * y la lista de participantes ya presentes en la sala.
 *
 * @param assignedId ID confirmado o reasignado por el servidor.
 * @param hostId     UUID del host (participante local del servidor).
 * @param hostName   Nombre del host.
 * @param participants Lista de todos los participantes en la sala en el momento de unirse.
 * @param publicKey  Clave pública ECDH P-256 del servidor en Base64 (X.509 DER).
 *                   Vacío si el cliente no envió clave en su HELLO (sin Fase 5).
 */
@Serializable
data class WelcomeBody(
    val assignedId: String,
    val hostId: String,
    val hostName: String,
    val participants: List<ParticipantInfo> = emptyList(),
    val publicKey: String = "",
    /** Grupos a los que pertenece el participante; enviados en el WELCOME al reconectar. */
    val grupos: List<ChatInfoBody> = emptyList(),
    /** Nombre visible de la sala; default "Sala de <host>", renombrable por el host. */
    val roomName: String = "",
)

/**
 * Cuerpo de REJECT: motivo por el que se rechaza la conexión.
 *
 * @param reason Texto legible para mostrar al usuario.
 */
@Serializable
data class RejectBody(val reason: String)

/**
 * Cuerpo de TEXT: mensaje de chat.
 *
 * @param id UUID único del mensaje, para deduplicación.
 * @param senderId UUID del emisor.
 * @param senderName Nombre visible del emisor.
 * @param text Contenido del mensaje.
 * @param timestamp Marca de tiempo en milisegundos desde epoch.
 * @param chatId Identificador del chat destino ("sala", "dm:<a>:<b>", "grp:<uuid>").
 */
@Serializable
data class TextBody(
    val id: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val timestamp: Long,
    val chatId: String = "sala",
)

/** Cuerpo de JOIN/LEAVE: aviso de entrada o salida de un participante. */
@Serializable
data class JoinLeaveBody(
    val participantId: String,
    val participantName: String,
    /** Método con que el participante entró: "nfc" | "code" | "ip". Vacío en LEAVE. */
    val joinMethod: String = "",
    /** Marca de tiempo de entrada (ms desde epoch); 0 en LEAVE. */
    val joinedAt: Long = 0L,
)

/**
 * Cabecera JSON de la trama IMAGE.
 *
 * El cuerpo completo de la trama tiene el formato:
 * ```
 * [4 bytes longitud de cabecera BE][bytes JSON][bytes JPEG]
 * ```
 *
 * @param id        UUID único del mensaje (para deduplicación).
 * @param senderId  UUID del emisor.
 * @param senderName Nombre visible del emisor.
 * @param timestamp Marca de tiempo en ms desde epoch.
 * @param sizeBytes Tamaño del bloque JPEG en bytes.
 * @param chatId    Identificador del chat destino ("sala", "dm:<a>:<b>", "grp:<uuid>").
 */
@Serializable
data class ImageBody(
    val id: String,
    val senderId: String,
    val senderName: String,
    val timestamp: Long,
    val sizeBytes: Int,
    val chatId: String = "sala",
)

/**
 * Cuerpo de CHAT_CREATE: petición de creación de grupo enviada por un cliente al host.
 *
 * @param name     Nombre visible del grupo.
 * @param memberIds UUIDs de los miembros deseados sin incluir al creador; el host lo añade.
 */
@Serializable
data class ChatCreateBody(
    val name: String,
    val memberIds: List<String>,
)

/**
 * Cuerpo de CHAT_INFO: descripción de un grupo enviada por el host a sus miembros.
 * También se incluye en [WelcomeBody.grupos] al reconectar para restaurar grupos del cliente.
 *
 * @param chatId      Identificador del grupo ("grp:<uuid>").
 * @param name        Nombre visible del grupo.
 * @param memberIds   UUIDs de todos los miembros.
 * @param creatorId   UUID del creador; vacío si viene de WELCOME al reconectar.
 * @param creatorName Nombre del creador para el mensaje de sistema «X creó el grupo».
 */
@Serializable
data class ChatInfoBody(
    val chatId: String,
    val name: String,
    val memberIds: List<String>,
    val creatorId: String = "",
    val creatorName: String = "",
)

/**
 * Cabecera JSON de la trama AUDIO.
 *
 * El cuerpo completo de la trama tiene el formato:
 * ```
 * [4 bytes longitud de cabecera BE][bytes JSON][bytes AAC-MPEG4]
 * ```
 *
 * @param id         UUID único del mensaje (para deduplicación).
 * @param senderId   UUID del emisor.
 * @param senderName Nombre visible del emisor.
 * @param timestamp  Marca de tiempo en ms desde epoch.
 * @param durationMs Duración de la nota en milisegundos.
 * @param sizeBytes  Tamaño del bloque AAC en bytes.
 * @param waveform   ~30 amplitudes normalizadas [0f, 1f] calculadas con getMaxAmplitude() durante la grabación.
 * @param chatId     Identificador del chat destino ("sala", "dm:<a>:<b>", "grp:<uuid>").
 */
@Serializable
data class AudioBody(
    val id: String,
    val senderId: String,
    val senderName: String,
    val timestamp: Long,
    val durationMs: Long,
    val sizeBytes: Int,
    val waveform: List<Float> = emptyList(),
    val chatId: String = "sala",
)
