package com.redes.privati.ui.screens.host

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.redes.privati.data.ChatRepository
import com.redes.privati.data.RoomState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel de la pantalla del host (sala creada, esperando cliente).
 *
 * Inicia el servidor en el repositorio y expone el [RoomState] para
 * que la pantalla muestre la IP, el puerto y el estado de la conexión.
 *
 * @param nombre Nombre elegido por el host en la pantalla de bienvenida.
 * @param repository Repositorio central de la aplicación.
 */
class HostViewModel(
    nombre: String,
    private val repository: ChatRepository,
) : ViewModel() {

    val roomState: StateFlow<RoomState> = repository.roomState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = repository.roomState.value,
    )

    init {
        repository.establecerUsuarioLocal(nombre, esHost = true)
        repository.iniciarServidor()
    }

    /** Cierra la sala: envía BYE si hay conexión activa y limpia el repositorio. */
    fun cerrarSala() {
        repository.desconectar()
    }

    /**
     * Expulsa a un participante de la sala.
     * @param participantId UUID del participante a expulsar.
     */
    fun expulsarParticipante(participantId: String) {
        repository.expulsarParticipante(participantId)
    }

    override fun onCleared() {
        super.onCleared()
        // El servidor se detiene solo si el ViewModel se destruye sin navegar al chat.
        // Si el cliente ya se conectó, la desconexión la gestiona ChatViewModel.
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Factory manual (sin Hilt)
    // ──────────────────────────────────────────────────────────────────────────

    class Factory(
        private val nombre: String,
        private val repository: ChatRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HostViewModel(nombre, repository) as T
    }
}
