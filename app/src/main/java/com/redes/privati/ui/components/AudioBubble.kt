package com.redes.privati.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.redes.privati.ui.icons.PrivatiIcons
import com.redes.privati.ui.theme.PrivatiShapes
import com.redes.privati.ui.theme.PrivatiSpacing
import com.redes.privati.ui.theme.PrivatiTheme
import com.redes.privati.ui.theme.PrivatiTypography

/**
 * Burbuja de nota de voz en el hilo de conversación.
 *
 * @param modifier Modificador externo.
 * @param isOwn Si es verdadero, la burbuja va a la derecha.
 * @param isPlaying Si se está reproduciendo actualmente.
 * @param onPlayPause Callback al pulsar el botón de reproducción/pausa.
 * @param durationMs Duración total de la nota en milisegundos.
 * @param progressFraction Fracción de reproducción [0f, 1f].
 * @param waveform ~30 amplitudes normalizadas [0f, 1f] grabadas durante la grabación.
 * @param time Hora de envío, p. ej. «14:35».
 */
@Composable
fun AudioBubble(
    modifier: Modifier = Modifier,
    isOwn: Boolean,
    isPlaying: Boolean = false,
    onPlayPause: () -> Unit = {},
    durationMs: Long = 0L,
    progressFraction: Float = 0f,
    waveform: List<Float> = emptyList(),
    time: String = "",
) {
    val colors = PrivatiTheme.colors
    val bubbleColor = if (isOwn) colors.bubbleOut else colors.surface
    val contentColor = if (isOwn) Color.White else colors.ink
    val waveColor = if (isOwn) Color.White.copy(alpha = 0.7f) else colors.primaryTint
    val waveActiveColor = if (isOwn) Color.White else colors.primary
    val timeColor = if (isOwn) Color(0xFFE4E0FF) else colors.inkMuted
    val shape = if (isOwn) PrivatiShapes.bubbleOutgoing else PrivatiShapes.bubbleIncoming

    val durationLabel = run {
        val totalSec = (durationMs / 1000).toInt()
        "%d:%02d".format(totalSec / 60, totalSec % 60)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .clip(shape)
                .background(bubbleColor)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Botón play/pausa circular
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(PrivatiShapes.pill)
                        .background(contentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = onPlayPause, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (isPlaying) PrivatiIcons.Pause else PrivatiIcons.Play,
                            contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                            tint = contentColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                ) {
                    // Forma de onda
                    val bars = if (waveform.isEmpty()) List(30) { 0.3f + (it % 5) * 0.1f } else waveform
                    val cutoffIndex = (bars.size * progressFraction).toInt()

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp),
                    ) {
                        val barWidth = size.width / (bars.size * 2f)
                        bars.forEachIndexed { i, amp ->
                            val barH = size.height * amp.coerceIn(0.1f, 1f)
                            val x = i * barWidth * 2 + barWidth / 2
                            val color = if (i < cutoffIndex) waveActiveColor else waveColor
                            drawLine(
                                color = color,
                                start = androidx.compose.ui.geometry.Offset(x, (size.height - barH) / 2),
                                end = androidx.compose.ui.geometry.Offset(x, (size.height + barH) / 2),
                                strokeWidth = barWidth,
                                cap = StrokeCap.Round,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = durationLabel,
                            style = PrivatiTypography.micro,
                            color = timeColor,
                        )
                        Text(
                            text = time,
                            style = PrivatiTypography.micro,
                            color = timeColor,
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Claro", showBackground = true)
@Preview(name = "Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AudioBubblePreview() {
    PrivatiTheme {
        Column(
            modifier = Modifier
                .background(PrivatiTheme.colors.surfaceMuted)
                .padding(PrivatiSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PrivatiSpacing.bubbleGap),
        ) {
            AudioBubble(
                isOwn = false,
                durationMs = 7500L,
                progressFraction = 0.4f,
                time = "14:33",
            )
            AudioBubble(
                isOwn = true,
                isPlaying = true,
                durationMs = 15000L,
                progressFraction = 0.6f,
                time = "14:35",
            )
        }
    }
}
