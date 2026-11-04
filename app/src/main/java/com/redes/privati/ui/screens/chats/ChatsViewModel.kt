package com.redes.privati.ui.screens.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.redes.privati.data.ChatRepository
import com.redes.privati.data.ChatSummary
import com.redes.privati.data.RoomState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel de la pantalla principal de chats (hub post-conexión).
 *
 * Expone la lista de chats y el estado de la sala. Las operaciones de
 * silenciar, fijar y eliminar son locales: no viajan por la red.
 *
 * @param repository Repositorio central de la aplicación.
 */
class ChatsViewModel(
    private val repository: ChatRepository,
) : ViewModel() {

    val chats: StateFlow<List<ChatSummary>> = repository.chats.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = repository.chats.value,
    )

    val roomState: StateFlow<RoomState> = repository.roomState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = repository.roomState.value,
    )

    /** Verdadero si el usuario local es el host de la sala. */
    val esHost: Boolean
        get() {
            val state = roomState.value
            return state.participants.firstOrNull { it.id == state.localUserId }?.isHost == true
        }

    // ──────────────────────────────────────────────────────────────────────────
    // Operaciones locales (no viajan por la red)
    // ──────────────────────────────────────────────────────────────────────────

    /** Alterna el silenciado del chat [chatId]. */
    fun toggleSilenciar(chatId: String) {
        val silenciado = chats.value.firstOrNull { it.chatId == chatId }?.silenciado ?: false
        repository.silenciarChat(chatId, !silenciado)
    }

    /** Alterna el estado de fijado del chat [chatId]. */
    fun toggleFijar(chatId: String) {
        val fijado = chats.value.firstOrNull { it.chatId == chatId }?.fijado ?: false
        repository.fijarChat(chatId, !fijado)
    }

    /** Elimina el historial local del chat [chatId]. Sin efecto sobre «sala». */
    fun eliminar(chatId: String) {
        repository.eliminarChatLocal(chatId)
    }

    /** chatId del grupo recién creado; la UI lo usa para navegar automáticamente. */
    val nuevoChatId: SharedFlow<String> = repository.nuevoChatId

    /** Número de solicitudes de entrada pendientes (solo host); usado para la insignia de la campana. */
    val pendingRequestCount: StateFlow<Int> = repository.pendingRequests
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )

    /**
     * Crea un grupo con [nombre] y [memberIds] como miembros adicionales.
     * Delega al repositorio según el modo (host o cliente).
     */
    fun crearGrupo(nombre: String, memberIds: List<String>) {
        repository.crearGrupo(nombre, memberIds)
    }

    /**
     * Calcula el chatId del privado con [contactId] (ids ordenados alfabéticamente
     * para que los dos extremos lleguen al mismo chatId sin coordinación) y
     * pre-registra el resumen en el repositorio para que el nombre sea visible
     * en la cabecera antes de que se envíe el primer mensaje.
     *
     * @param contactId UUID del participante destino.
     * @return chatId con formato "dm:<idMenor>:<idMayor>".
     */
    fun dmChatId(contactId: String): String {
        val localId = roomState.value.localUserId
        val partes = listOf(localId, contactId).sorted()
        val chatId = "dm:${partes[0]}:${partes[1]}"
        repository.asegurarChat(chatId)
        return chatId
    }

    /** Cierra la sesión de chat y limpia el repositorio. */
    fun desconectar() {
        repository.desconectar()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Factory manual (sin Hilt)
    // ──────────────────────────────────────────────────────────────────────────

    class Factory(
        private val repository: ChatRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ChatsViewModel(repository) as T
    }
}
