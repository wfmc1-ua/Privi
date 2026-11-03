package com.redes.privati.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.redes.privati.ui.theme.PrivatiTheme

/**
 * Animación de ondas concéntricas que indica la espera de un toque NFC.
 * Dibuja 3 círculos que se expanden y desvanecen en bucle con desfase entre ellos.
 *
 * @param modifier Modificador externo.
 * @param size Tamaño del área cuadrada que ocupa la animación.
 * @param isActive Si es falso las ondas se detienen (NFC no disponible).
 */
@Composable
fun NfcWaves(
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    isActive: Boolean = true,
) {
    val colors = PrivatiTheme.colors
    val waveColor = colors.primary

    val infiniteTransition = rememberInfiniteTransition(label = "nfc_waves")

    // Tres ondas con desfase de 500 ms entre ellas
    val phase0 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = InfiniteRepeatableSpec(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave0",
    )
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = InfiniteRepeatableSpec(
            animation = tween(durationMillis = 1800, delayMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave1",
    )
    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = InfiniteRepeatableSpec(
            animation = tween(durationMillis = 1800, delayMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave2",
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = this.center
            val maxRadius = this.size.minDimension / 2f

            if (isActive) {
                listOf(phase0, phase1, phase2).forEach { phase ->
                    val radius = maxRadius * phase
                    val alpha = (1f - phase).coerceIn(0f, 1f)
                    drawCircle(
                        color = waveColor.copy(alpha = alpha * 0.3f),
                        radius = radius,
                        center = center,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()),
                    )
                }
            }

            // Círculo central fijo
            drawCircle(
                color = waveColor,
                radius = maxRadius * 0.22f,
                center = center,
            )
        }
    }
}

@Preview(name = "Claro", showBackground = true)
@Preview(name = "Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NfcWavesPreview() {
    PrivatiTheme {
        NfcWaves(isActive = true)
    }
}
