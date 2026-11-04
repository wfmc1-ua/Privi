package com.redes.privati.data

/** Estado de la conexión de red visible en la interfaz. */
enum class ConnectionStatus {
    ONLINE,        // Conectado y activo
    RECONNECTING,  // Intentando reconectar con backoff
    DISCONNECTED,  // Sin conexión
}

/**
 * Estado de la sala de chat, compartido entre [ChatServer] y la UI.
 *
 * @param roomCode Código de 4 caracteres para unirse.
 * @param hostIp Dirección IP del servidor en la red local.
 * @param port Puerto TCP en escucha.
 * @param participants Lista de participantes conectados, incluido el host.
 * @param isEncrypted Si el canal está cifrado con ECDH+AES-GCM (Fase 5).
 * @param connectionStatus Estado de la conexión del cliente o del servidor.
 */
data class RoomState(
    val localUserId: String = "",
    val roomCode: String = "",
    val hostIp: String = "",
    val port: Int = 0,
    val participants: List<Participant> = emptyList(),
    val isEncrypted: Boolean = false,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
)
