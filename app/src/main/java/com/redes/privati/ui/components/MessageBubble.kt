package com.redes.privati.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redes.privati.ui.icons.PrivatiIcons
import com.redes.privati.ui.theme.PrivatiShapes
import com.redes.privati.ui.theme.PrivatiSpacing
import com.redes.privati.ui.theme.PrivatiTheme
import com.redes.privati.ui.theme.PrivatiTypography

/**
 * Burbuja de mensaje de texto en el hilo de conversación.
 *
 * - Entrante: fondo [PrivatiColors.surface], cola abajo-izquierda, sombra suave.
 * - Propia: fondo [PrivatiColors.bubbleOut], cola abajo-derecha, sin sombra.
 *
 * @param modifier Modificador externo.
 * @param text Texto del mensaje.
 * @param time Hora de envío formateada, p. ej. «14:35».
 * @param isOwn Si es verdadero la burbuja va a la derecha (mensaje propio).
 * @param senderName Nombre del remitente (visible en grupos cuando no es propio).
 * @param senderAvatarId Id del remitente para seleccionar el color del avatar.
 * @param showDoubleCheck Si se muestran los dos checks de confirmación (solo mensajes propios).
 */
@Composable
fun MessageBubble(
    modifier: Modifier = Modifier,
    text: String,
    time: String,
    isOwn: Boolean,
    senderName: String? = null,
    senderAvatarId: String? = null,
    showDoubleCheck: Boolean = false,
) {
    val colors = PrivatiTheme.colors

    val bubbleColor = if (isOwn) colors.bubbleOut else colors.surface
    val textColor = if (isOwn) Color.White else colors.ink
    val timeColor = if (isOwn) Color(0xFFE4E0FF) else colors.inkMuted
    val bubbleShape = if (isOwn) PrivatiShapes.bubbleOutgoing else PrivatiShapes.bubbleIncoming

    val bubbleModifier = if (!isOwn) {
        Modifier.shadow(
            elevation = 2.dp,
            shape = bubbleShape,
            ambientColor = colors.ink.copy(alpha = 0.06f),
            spotColor = colors.ink.copy(alpha = 0.06f),
        )
    } else Modifier

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
    ) {
        if (!isOwn && senderAvatarId != null && senderName != null) {
            Avatar(
                participantId = senderAvatarId,
                name = senderName,
                size = 30.dp,
                modifier = Modifier
                    .padding(end = PrivatiSpacing.sm)
                    .align(Alignment.Bottom),
            )
        }

        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start,
        ) {
            // Nombre del remitente en grupos
            if (!isOwn && senderName != null) {
                val palettes = if (colors.isDark) {
                    com.redes.privati.ui.theme.AvatarPalettesDark
                } else {
                    com.redes.privati.ui.theme.AvatarPalettesLight
                }
                val nameColor = senderAvatarId?.let {
                    palettes[kotlin.math.abs(it.hashCode()) % palettes.size].second
                } ?: colors.inkSecondary

                Text(
                    text = senderName,
                    style = PrivatiTypography.caption,
                    color = nameColor,
                    modifier = Modifier.padding(start = 14.dp, bottom = 2.dp),
                )
            }

            Box(
                modifier = bubbleModifier
                    .clip(bubbleShape)
                    .background(bubbleColor)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Column {
                    Text(
                        text = text,
                        style = PrivatiTypography.body.copy(lineHeight = (15 * 1.4).sp),
                        color = textColor,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = time,
                            style = PrivatiTypography.micro,
                            color = timeColor,
                        )
                        if (isOwn && showDoubleCheck) {
                            Icon(
                                imageVector = PrivatiIcons.CheckDouble,
                                contentDescription = "Entregado",
                                tint = timeColor,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(14.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Claro", showBackground = true)
@Preview(name = "Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MessageBubblePreview() {
    PrivatiTheme {
        Column(
            modifier = Modifier
                .background(PrivatiTheme.colors.surfaceMuted)
                .padding(PrivatiSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PrivatiSpacing.bubbleGap),
        ) {
            MessageBubble(
                text = "Hola, ¿me escuchas?",
                time = "14:32",
                isOwn = false,
                senderName = "Ana",
                senderAvatarId = "abc123",
            )
            MessageBubble(
                text = "Sí, te escucho perfectamente.",
                time = "14:33",
                isOwn = true,
                showDoubleCheck = true,
            )
        }
    }
}
