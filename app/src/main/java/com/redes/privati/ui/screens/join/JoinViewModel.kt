package com.redes.privati.ui.screens.join

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.redes.privati.data.ChatRepository
import com.redes.privati.data.ConnectionStatus
import com.redes.privati.data.RoomState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel de la pantalla de unión al chat.
 *
 * Gestiona el estado del formulario de conexión (IP, puerto) y delega
 * en [ChatRepository] el proceso de conexión TCP y handshake.
 *
 * @param nombre Nombre elegido por el cliente en la pantalla de bienvenida.
 * @param repository Repositorio central de la aplicación.
 */
class JoinViewModel(
    nombre: String,
    private val repository: ChatRepository,
) : ViewModel() {

    val roomState: StateFlow<RoomState> = repository.roomState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = repository.roomState.value,
    )

    /** Errores de conexión de un solo disparo (mostrar Snackbar en la UI). */
    val error: SharedFlow<String> = repository.error

    /** Indica si hay un intento de conexión en curso (manual por IP). */
    val conectando: MutableStateFlow<Boolean> = MutableStateFlow(false)

    /**
     * Verdadero mientras se realiza la búsqueda NSD por código.
     * Permite distinguir en la UI entre "buscando sala…" y "conectando…".
     */
    val buscandoPorCodigo: MutableStateFlow<Boolean> = MutableStateFlow(false)

    init {
        repository.establecerUsuarioLocal(nombre, esHost = false)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Validación de IP y puerto
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Comprueba si [ip] tiene formato IPv4 válido (a.b.c.d con cada octeto 0-255).
     * No verifica si la dirección es alcanzable.
     */
    fun esIpValida(ip: String): Boolean {
        val partes = ip.trim().split(".")
        if (partes.size != 4) return false
        return partes.all { parte ->
            val n = parte.toIntOrNull() ?: return false
            n in 0..255
        }
    }

    /**
     * Comprueba si [port] es un número de puerto válido para un usuario (1024-65535).
     * Los puertos 0-1023 están reservados para servicios del sistema.
     */
    fun esPuertoValido(port: String): Boolean {
        val n = port.trim().toIntOrNull() ?: return false
        return n in 1024..65535
    }

    /**
     * Inicia la conexión directa al servidor en [ip]:[puerto] (modo manual).
     * [conectando] permanece verdadero hasta recibir WELCOME o detectar un fallo.
     */
    fun conectar(ip: String, puerto: Int) {
        if (conectando.value) return
        viewModelScope.launch {
            conectando.value = true
            try {
                repository.conectarComoCliente(ip, puerto)
                // Suspender hasta que el estado deje de ser RECONNECTING
                // (se convierte en ONLINE con WELCOME, o en DISCONNECTED si hay error)
                roomState.first { it.connectionStatus != ConnectionStatus.RECONNECTING }
            } finally {
                conectando.value = false
            }
        }
    }

    /**
     * Busca la sala con [codigoSala] mediante NSD/mDNS y, al encontrarla, inicia
     * la conexión TCP automáticamente.
     *
     * [buscandoPorCodigo] es verdadero durante todo el proceso (búsqueda + conexión).
     * Los errores se propagan a través de [error] como en la conexión manual.
     *
     * @param codigoSala Código de 4 caracteres de la sala.
     */
    fun buscarPorCodigo(codigoSala: String) {
        if (conectando.value || buscandoPorCodigo.value) return
        viewModelScope.launch {
            buscandoPorCodigo.value = true
            try {
                repository.buscarSalaPorCodigo(codigoSala)
                // Esperar a que la conexión salga del estado RECONNECTING
                roomState.first { it.connectionStatus != ConnectionStatus.RECONNECTING }
            } finally {
                buscandoPorCodigo.value = false
            }
        }
    }

    /**
     * Conecta directamente con los datos recibidos por NFC (ip, puerto y código de sala).
     * El código se reenvía a [ChatRepository.conectarComoCliente] para usarlo como salt
     * HKDF en el handshake ECDH, igual que en la conexión por código NSD.
     */
    fun conectarViaNfc(ip: String, puerto: Int, codigo: String) {
        if (conectando.value || buscandoPorCodigo.value) return
        viewModelScope.launch {
            conectando.value = true
            try {
                repository.conectarComoCliente(ip, puerto, roomCode = codigo)
                roomState.first { it.connectionStatus != ConnectionStatus.RECONNECTING }
            } finally {
                conectando.value = false
            }
        }
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
            JoinViewModel(nombre, repository) as T
    }
}
