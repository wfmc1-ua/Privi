package com.redes.privati.ui.components

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.redes.privati.ui.icons.PrivatiIcons
import com.redes.privati.ui.theme.PrivatiShapes
import com.redes.privati.ui.theme.PrivatiSizes
import com.redes.privati.ui.theme.PrivatiSpacing
import com.redes.privati.ui.theme.PrivatiTheme
import com.redes.privati.ui.theme.PrivatiTypography

/**
 * Barra de entrada de mensajes en la parte inferior de la conversación.
 * Cuando el campo de texto está vacío muestra el botón de micrófono (nota de voz);
 * cuando tiene texto muestra el botón de enviar. Incluye el botón de adjuntar imagen.
 *
 * @param modifier Modificador externo.
 * @param text Texto actual del campo.
 * @param onTextChange Callback al modificar el texto.
 * @param onSend Callback al pulsar enviar.
 * @param onAttachImage Callback al pulsar adjuntar imagen.
 * @param onMicPress Callback al mantener pulsado el micrófono (inicio de grabación).
 * @param onMicRelease Callback al soltar el micrófono (fin de grabación / envío).
 * @param isRecording Si se está grabando actualmente (muestra indicador rojo).
 * @param enabled Si la barra acepta interacción.
 */
@Composable
fun MessageInputBar(
    modifier: Modifier = Modifier,
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit = {},
    onAttachImage: () -> Unit = {},
    onMicPress: () -> Unit = {},
    onMicRelease: () -> Unit = {},
    isRecording: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = PrivatiTheme.colors
    val showSend = text.isNotBlank()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .imePadding(),
    ) {
        HorizontalDivider(color = colors.divider, thickness = 1.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = PrivatiSpacing.sm,
                    vertical = PrivatiSpacing.sm,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Botón adjuntar imagen
            IconButton(
                onClick = onAttachImage,
                enabled = enabled,
                modifier = Modifier.size(PrivatiSizes.minTouchTarget),
            ) {
                Icon(
                    imageVector = PrivatiIcons.Attach,
                    contentDescription = "Adjuntar imagen",
                    tint = colors.inkSecondary,
                )
            }

            // Campo de texto
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp, max = 120.dp)
                    .clip(PrivatiShapes.pill)
                    .background(colors.surfaceMuted)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isEmpty() && !isRecording) {
                    Text(
                        text = "Escribe un mensaje",
                        style = PrivatiTypography.body,
                        color = colors.inkMuted,
                    )
                }
                if (isRecording) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(PrivatiShapes.pill)
                                .background(colors.danger),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Grabando · desliza para cancelar",
                            style = PrivatiTypography.bodySm,
                            color = colors.inkSecondary,
                        )
                    }
                } else {
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        enabled = enabled,
                        textStyle = PrivatiTypography.body.copy(color = colors.ink),
                        cursorBrush = SolidColor(colors.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.width(PrivatiSpacing.sm))

            // Botón enviar o micrófono
            Box(
                modifier = Modifier
                    .size(PrivatiSizes.iconButton)
                    .clip(PrivatiShapes.pill)
                    .background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(
                    onClick = if (showSend) onSend else onMicPress,
                    enabled = enabled,
                    modifier = Modifier.size(PrivatiSizes.iconButton),
                ) {
                    Icon(
                        imageVector = if (showSend) PrivatiIcons.Send else PrivatiIcons.Mic,
                        contentDescription = if (showSend) "Enviar" else "Grabar nota de voz",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Preview(name = "Claro", showBackground = true)
@Preview(name = "Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MessageInputBarPreview() {
    PrivatiTheme {
        androidx.compose.foundation.layout.Column {
            MessageInputBar(text = "", onTextChange = {})
            MessageInputBar(text = "Hola!", onTextChange = {})
            MessageInputBar(text = "", onTextChange = {}, isRecording = true)
        }
    }
}
