package com.redes.privati.ui.screens.chat

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.redes.privati.audio.AudioRecorder
import com.redes.privati.data.ChatRepository
import com.redes.privati.data.Message
import com.redes.privati.data.RoomState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel de la pantalla de chat.
 *
 * Gestiona el historial de mensajes, el campo de texto, la grabación de notas
 * de voz ([iniciarGrabacion] / [detenerGrabacion] / [cancelarGrabacion]) y la
 * reproducción de audio ([toggleReproduccion]). Solo suena una nota a la vez.
 *
 * @param repository Repositorio central de la aplicación.
 */
class ChatViewModel(
    private val repository: ChatRepository,
    private val chatId: String = "sala",
) : ViewModel() {

    /** Mensajes del chat identificado por [chatId], filtrados del mapa del repositorio. */
    val messages: StateFlow<List<Message>> = repository.messages
        .map { mapa -> mapa.getOrDefault(chatId, emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = repository.messages.value.getOrDefault(chatId, emptyList()),
        )

    init {
        // Marcar como leídos los mensajes de este chat al abrir la pantalla
        repository.marcarLeido(chatId)
    }

    val roomState: StateFlow<RoomState> = repository.roomState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = repository.roomState.value,
    )

    /** Estado del campo de texto del compositor de mensajes. */
    val inputText: MutableStateFlow<String> = MutableStateFlow("")

    // ──────────────────────────────────────────────────────────────────────────
    // Estado de grabación
    // ──────────────────────────────────────────────────────────────────────────

    private val _estaGrabando = MutableStateFlow(false)
    val estaGrabando: StateFlow<Boolean> = _estaGrabando.asStateFlow()

    private val _duracionGrabacionMs = MutableStateFlow(0L)
    val duracionGrabacionMs: StateFlow<Long> = _duracionGrabacionMs.asStateFlow()

    private var grabador: AudioRecorder? = null
    private var grabacionJob: Job? = null

    // ──────────────────────────────────────────────────────────────────────────
    // Estado de reproducción
    // ──────────────────────────────────────────────────────────────────────────

    private val _idMensajeReproduciendo = MutableStateFlow<String?>(null)
    val idMensajeReproduciendo: StateFlow<String?> = _idMensajeReproduciendo.asStateFlow()

    private val _progresoReproduccion = MutableStateFlow(0f)
    val progresoReproduccion: StateFlow<Float> = _progresoReproduccion.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var archivoReproduccion: java.io.File? = null
    private var reproductorJob: Job? = null

    // ──────────────────────────────────────────────────────────────────────────
    // Acciones de texto e imagen
    // ──────────────────────────────────────────────────────────────────────────

    fun onInputChange(texto: String) { inputText.value = texto }

    /** Envía el texto actual si no está vacío y limpia el campo. */
    fun enviarMensaje() {
        val texto = inputText.value.trim()
        if (texto.isBlank()) return
        inputText.value = ""
        repository.enviarTexto(texto, chatId)
    }

    fun enviarImagen(uri: Uri) {
        viewModelScope.launch { repository.enviarImagen(uri, chatId) }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Grabación de notas de voz
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Crea un [AudioRecorder] y lo arranca.
     * Lanza un contador interno que actualiza [duracionGrabacionMs] cada 100 ms
     * y detiene la grabación automáticamente al alcanzar 60 s.
     */
    fun iniciarGrabacion(context: Context) {
        viewModelScope.launch {
            val g = AudioRecorder(context)
            grabador = g
            val exito = withContext(Dispatchers.IO) { g.iniciar() }
            if (exito) {
                _estaGrabando.value = true
                val inicioMs = System.currentTimeMillis()
                grabacionJob = viewModelScope.launch {
                    while (isActive) {
                        _duracionGrabacionMs.value = System.currentTimeMillis() - inicioMs
                        delay(100L)
                        if (_duracionGrabacionMs.value >= 60_000L) {
                            detenerGrabacion()
                            break
                        }
                    }
                }
            } else {
                grabador = null
            }
        }
    }

    /** Detiene la grabación y envía la nota si dura ≥ 1 s; la descarta si es más corta. */
    fun detenerGrabacion() {
        grabacionJob?.cancel()
        grabacionJob = null
        val g = grabador ?: return
        grabador = null
        _estaGrabando.value = false
        _duracionGrabacionMs.value = 0L

        viewModelScope.launch {
            val grabacion = withContext(Dispatchers.IO) { g.detener() }
            if (grabacion != null) {
                repository.enviarAudio(grabacion.bytes, grabacion.durationMs, grabacion.waveform, chatId)
            }
        }
    }

    /** Cancela la grabación en curso y la descarta sin enviar. */
    fun cancelarGrabacion() {
        grabacionJob?.cancel()
        grabacionJob = null
        grabador?.cancelar()
        grabador = null
        _estaGrabando.value = false
        _duracionGrabacionMs.value = 0L
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Reproducción de notas de voz
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Alterna entre reproducir y detener la nota identificada por [messageId].
     * Si ya se está reproduciendo otra nota, la para primero.
     * Escribe los bytes en un fichero temporal para que [MediaPlayer] los pueda leer.
     */
    fun toggleReproduccion(messageId: String, audioBytes: ByteArray) {
        if (_idMensajeReproduciendo.value == messageId) {
            detenerReproduccion()
            return
        }
        detenerReproduccion()

        viewModelScope.launch {
            val cacheDir = repository.obtenerCacheDir()
            val archivo = withContext(Dispatchers.IO) {
                val f = java.io.File.createTempFile("corro_play_", ".m4a", cacheDir)
                f.writeBytes(audioBytes)
                f
            }
            archivoReproduccion = archivo

            try {
                val mp = MediaPlayer()
                mp.setDataSource(archivo.absolutePath)

                mp.setOnCompletionListener {
                    viewModelScope.launch {
                        reproductorJob?.cancel()
                        reproductorJob = null
                        _idMensajeReproduciendo.value = null
                        _progresoReproduccion.value = 0f
                        mp.release()
                        if (mediaPlayer === mp) mediaPlayer = null
                        withContext(Dispatchers.IO) {
                            try { archivo.delete() } catch (_: Exception) {}
                        }
                        if (archivoReproduccion === archivo) archivoReproduccion = null
                    }
                }

                // prepareAsync es no bloqueante; onPrepared se llama en el hilo principal
                mp.setOnPreparedListener { preparedMp ->
                    mediaPlayer = preparedMp
                    _idMensajeReproduciendo.value = messageId
                    _progresoReproduccion.value = 0f
                    preparedMp.start()
                    val duracion = preparedMp.duration.toLong()
                    reproductorJob = viewModelScope.launch {
                        while (isActive) {
                            delay(100L)
                            val current = mediaPlayer ?: break
                            if (!current.isPlaying) break
                            _progresoReproduccion.value = if (duracion > 0) {
                                current.currentPosition.toFloat() / duracion
                            } else 0f
                        }
                    }
                }
                mp.prepareAsync()
            } catch (_: Exception) {
                mediaPlayer?.release()
                mediaPlayer = null
                withContext(Dispatchers.IO) {
                    try { archivo.delete() } catch (_: Exception) {}
                }
                archivoReproduccion = null
            }
        }
    }

    /** Detiene cualquier reproducción en curso y libera el [MediaPlayer]. */
    fun detenerReproduccion() {
        reproductorJob?.cancel()
        reproductorJob = null
        mediaPlayer?.let { mp ->
            try { mp.stop() } catch (_: Exception) {}
            mp.release()
        }
        mediaPlayer = null
        val archivo = archivoReproduccion
        archivoReproduccion = null
        if (archivo != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try { archivo.delete() } catch (_: Exception) {}
            }
        }
        _idMensajeReproduciendo.value = null
        _progresoReproduccion.value = 0f
    }

    /** Cierra la sesión de chat y limpia el repositorio. */
    fun desconectar() {
        repository.desconectar()
    }

    override fun onCleared() {
        super.onCleared()
        cancelarGrabacion()
        detenerReproduccion()
        grabador?.release()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Factory manual (sin Hilt)
    // ──────────────────────────────────────────────────────────────────────────

    class Factory(
        private val repository: ChatRepository,
        private val chatId: String = "sala",
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ChatViewModel(repository, chatId) as T
    }
}
