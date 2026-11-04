package com.redes.privati.data

/**
 * Información de una solicitud de entrada pendiente de aprobación por el host.
 *
 * @param remoteId       Identificador interno de la conexión TCP (no estable entre sesiones).
 * @param participantId  UUID estable del participante que solicita la entrada.
 * @param participantName Nombre visible del participante.
 * @param joinMethod     Cómo intentó entrar: "code" | "ip".
 * @param timestampMs    Momento en que se recibió el HELLO, en milisegundos desde epoch.
 */
data class PendingRequestInfo(
    val remoteId: String,
    val participantId: String,
    val participantName: String,
    val joinMethod: String,
    val timestampMs: Long,
)
