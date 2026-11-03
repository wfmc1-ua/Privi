package com.redes.privati.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.redes.privati.ui.theme.PrivatiTheme
import com.redes.privati.ui.theme.PrivatiTypography

/**
 * Isotipo y logotipo de Privati.
 * El isotipo es un círculo de color [primary] con un bocadillo de chat
 * sonriente dibujado en Canvas. Junto a él va el nombre «Privati».
 *
 * @param modifier Modificador externo.
 * @param iconSize Tamaño del isotipo cuadrado.
 * @param showName Si se muestra el nombre de la app junto al isotipo.
 */
@Composable
fun PrivatiLogo(
    modifier: Modifier = Modifier,
    iconSize: Dp = 56.dp,
    showName: Boolean = true,
) {
    val colors = PrivatiTheme.colors

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Canvas(modifier = Modifier.size(iconSize)) {
            val r = size.minDimension / 2f
            val cx = center.x
            val cy = center.y

            // Círculo de fondo primary
            drawCircle(color = colors.primary, radius = r, center = center)

            // Bocadillo: rectángulo redondeado blanco
            val bubbleW = r * 1.1f
            val bubbleH = r * 0.75f
            val bubbleLeft = cx - bubbleW / 2
            val bubbleTop = cy - bubbleH / 2 - r * 0.08f
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(bubbleLeft, bubbleTop),
                size = Size(bubbleW, bubbleH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 0.2f),
            )

            // Cola del bocadillo (triángulo)
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx - r * 0.15f, bubbleTop + bubbleH - 1f)
                lineTo(cx - r * 0.15f, bubbleTop + bubbleH + r * 0.25f)
                lineTo(cx + r * 0.15f, bubbleTop + bubbleH - 1f)
                close()
            }
            drawPath(path = path, color = Color.White)

            // Sonrisa: arco
            val smileR = r * 0.22f
            val smileCenter = Offset(cx, cy - r * 0.02f)
            drawArc(
                color = colors.primary,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(smileCenter.x - smileR, smileCenter.y - smileR),
                size = Size(smileR * 2, smileR * 2),
                style = Stroke(width = r * 0.1f),
            )

            // Ojos
            val eyeY = cy - r * 0.18f
            drawCircle(color = colors.primary, radius = r * 0.07f, center = Offset(cx - r * 0.22f, eyeY))
            drawCircle(color = colors.primary, radius = r * 0.07f, center = Offset(cx + r * 0.22f, eyeY))
        }

        if (showName) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Privati",
                style = PrivatiTypography.title1,
                color = colors.ink,
            )
        }
    }
}

@Preview(name = "Claro", showBackground = true)
@Preview(name = "Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PrivatiLogoPreview() {
    PrivatiTheme {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PrivatiLogo(iconSize = 56.dp, showName = true)
            PrivatiLogo(iconSize = 40.dp, showName = false)
        }
    }
}
