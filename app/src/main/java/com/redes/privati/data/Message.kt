package com.redes.privati.data

import java.util.UUID

/** Tipos de mensaje que puede contener una burbuja en el hilo de chat. */
enum class MessageType {
    TEXT,   // Texto plano
    IMAGE,  // Imagen JPEG enviada por red local
    AUDIO,  // Nota de voz AAC
    SYSTEM, // Aviso de sistema: «Ana se ha unido», «Ana ha salido»
}

/**
 * Participante en la sala de chat.
 *
 * @param id Identificador único (UUID), estable durante la sesión.
 * @param name Nombre elegido al unirse.
 * @param isHost Si es verdadero, este participante creó la sala.
 */
data class Participant(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isHost: Boolean = false,
)

/**
 * Mensaje en el hilo de conversación.
 *
 * @param id Identificador único del mensaje.
 * @param senderId Id del participante emisor; vacío en mensajes de sistema.
 * @param senderName Nombre visible del emisor.
 * @param type Tipo de contenido.
 * @param text Texto del mensaje (vacío para imagen/audio).
 * @param imageBytes Bytes JPEG de la imagen (nulo si no es IMAGE).
 * @param imageSizeLabel Etiqueta legible del tamaño, p. ej. «212 KB · por red local».
 * @param audioDurationMs Duración en milisegundos de la nota de voz.
 * @param audioWaveform ~30 amplitudes normalizadas [0f, 1f] para visualizar la onda.
 * @param timestamp Hora de envío en milisegundos desde epoch.
 * @param isOwn Si es verdadero, este dispositivo lo envió.
 */
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val senderId: String = "",
    val senderName: String = "",
    val type: MessageType = MessageType.TEXT,
    val text: String = "",
    val imageBytes: ByteArray? = null,
    val imageSizeLabel: String? = null,
    val audioDurationMs: Long = 0L,
    val audioWaveform: List<Float> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val isOwn: Boolean = false,
) {
    // ByteArray rompe equals/hashCode automáticos; se excluye de la comparación.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Message) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
