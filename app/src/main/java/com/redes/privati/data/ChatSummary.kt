package com.redes.privati.data

/** Tipo de chat dentro de una sala. */
enum class ChatTipo {
    SALA,     // Chat general, chatId = "sala"
    PRIVADO,  // Chat directo entre dos personas, chatId = "dm:<idA>:<idB>"
    GRUPO,    // Grupo con nombre y miembros, chatId = "grp:<uuid>"
}

/**
 * Resumen de un chat para mostrarlo en la lista de Chats.
 *
 * @param chatId Identificador único del chat (Fase 9: siempre "sala"; dm/grp en Fases 11-12).
 * @param tipo Tipo de chat según el prefijo del [chatId].
 * @param nombre Nombre visible: nombre de la sala, del otro participante, o del grupo.
 * @param miembros Lista de ids de los miembros; relevante en grupos (Fase 12).
 * @param ultimoMensaje Vista previa del último mensaje no-sistema.
 * @param ultimaHora Marca de tiempo en ms del último mensaje.
 * @param noLeidos Mensajes recibidos desde que se abrió este chat por última vez.
 * @param silenciado Suprime la insignia de no leídos (local, no viaja por la red).
 * @param fijado Aparece al principio de la lista de chats (local, no viaja por la red).
 */
data class ChatSummary(
    val chatId: String,
    val tipo: ChatTipo,
    val nombre: String,
    val miembros: List<String> = emptyList(),
    val ultimoMensaje: String = "",
    val ultimaHora: Long = 0L,
    val noLeidos: Int = 0,
    val silenciado: Boolean = false,
    val fijado: Boolean = false,
)
