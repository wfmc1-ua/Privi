package com.redes.privati.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Resultado de una grabación completada con éxito. */
data class AudioRecording(
    val bytes: ByteArray,
    val durationMs: Long,
    val waveform: List<Float>,
)

/**
 * Envuelve [MediaRecorder] para grabar notas de voz en formato MPEG-4/AAC.
 *
 * Flujo de uso: [iniciar] → [detener] para enviar, o [cancelar] para descartar.
 * Liberar siempre con [release] cuando el ViewModel que lo contiene se destruya.
 *
 * Parámetros de grabación: mono, 64 kbps, 44 100 Hz, máximo 60 s.
 */
class AudioRecorder(private val context: Context) {

    @Volatile private var recorder: MediaRecorder? = null
    @Volatile private var archivoSalida: java.io.File? = null
    @Volatile private var inicioMs = 0L

    private val muestrasAmplitud = mutableListOf<Int>()
    private var muestreoJob: Job? = null

    // Ámbito propio para la corrutina de muestreo; se cancela en release()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Crea el fichero temporal, configura y arranca [MediaRecorder].
     * Lanza una corrutina que sondea [MediaRecorder.getMaxAmplitude] cada 100 ms.
     * Devuelve true si la grabación se inició correctamente.
     */
    suspend fun iniciar(): Boolean = withContext(Dispatchers.IO) {
        try {
            val archivo = java.io.File.createTempFile("corro_audio_", ".m4a", context.cacheDir)
            archivoSalida = archivo

            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioEncodingBitRate(64_000)
                setAudioSamplingRate(44_100)
                setMaxDuration(60_000)
                setOutputFile(archivo.absolutePath)
                prepare()
                start()
            }
            recorder = mr
            inicioMs = System.currentTimeMillis()
            muestrasAmplitud.clear()

            muestreoJob = scope.launch {
                while (isActive) {
                    delay(100L)
                    try { muestrasAmplitud.add(mr.maxAmplitude) } catch (_: Exception) { break }
                }
            }
            true
        } catch (_: Exception) {
            limpiar()
            false
        }
    }

    /**
     * Detiene la grabación y devuelve el [AudioRecording] con los bytes AAC y la forma de onda.
     * Devuelve null si la duración es menor de 1 s (nota descartada según especificación).
     */
    fun detener(): AudioRecording? {
        muestreoJob?.cancel()
        val mr = recorder ?: run { limpiar(); return null }
        val archivo = archivoSalida ?: run { limpiar(); return null }

        val duracionMs = System.currentTimeMillis() - inicioMs
        if (duracionMs < 1_000L) {
            limpiar()
            return null
        }

        return try {
            mr.stop()
            mr.release()
            recorder = null

            val bytes = archivo.readBytes()
            try { archivo.delete() } catch (_: Exception) {}
            archivoSalida = null

            val forma = reducirAmplitudes(muestrasAmplitud.toList(), 30)
            AudioRecording(bytes, duracionMs, forma)
        } catch (_: Exception) {
            limpiar()
            null
        }
    }

    /** Descarta la grabación en curso sin devolverla. */
    fun cancelar() {
        muestreoJob?.cancel()
        limpiar()
    }

    /** Libera todos los recursos. Llamar desde onCleared() del ViewModel. */
    fun release() {
        cancelar()
        scope.cancel()
    }

    private fun limpiar() {
        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        try { archivoSalida?.delete() } catch (_: Exception) {}
        archivoSalida = null
    }

    // Reduce la lista de amplitudes brutas a exactamente [n] valores normalizados en [0f, 1f]
    private fun reducirAmplitudes(lista: List<Int>, n: Int): List<Float> {
        if (lista.isEmpty()) return List(n) { 0.3f }
        val maxVal = lista.maxOrNull()?.toFloat()?.takeIf { it > 0f } ?: 1f
        if (lista.size <= n) {
            val normalizada = lista.map { it / maxVal }
            return normalizada + List(n - lista.size) { 0.1f }
        }
        val step = lista.size.toFloat() / n
        return (0 until n).map { i -> lista[(i * step).toInt()].toFloat() / maxVal }
    }
}
