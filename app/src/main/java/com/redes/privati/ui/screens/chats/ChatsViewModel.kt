package com.redes.privati.ui.screens.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.redes.privati.data.ChatRepository
import com.redes.privati.data.ChatSummary
import com.redes.privati.data.RoomState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
