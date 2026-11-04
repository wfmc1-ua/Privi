package com.redes.privati.ui.components

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
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
 * Cuando el campo de texto está vacío muestra el botón de micrófono;
 * cuando tiene texto muestra el botón de enviar.
 *
 * El micrófono usa [detectDragGesturesAfterLongPress]: mantener pulsado
 * dispara [onMicPress], soltar llama [onMicRelease] (enviar), y deslizar
 * más de 80 dp a la izquierda llama [onMicCancel] (descartar).
 *
 * @param modifier          Modificador externo.
 * @param text              Texto actual del campo.
 * @param onTextChange      Callback al modificar el texto.
 * @param onSend            Callback al pulsar enviar.
 * @param onAttachImage     Callback al seleccionar imagen de la galería.
 * @param onTakePhoto       Callback al seleccionar «Cámara» del menú adjuntar.
 * @param onMicPress        Callback cuando se detecta la pulsación larga (inicio de grabación).
 * @param onMicRelease      Callback al soltar (fin de grabación / envío).
 * @param onMicCancel       Callback al deslizar a la izquierda (cancelar grabación).
 * @param isRecording       Si se está grabando actualmente (muestra indicador rojo).
 * @param recordingDuration Duración formateada «0:07» que se muestra durante la grabación.
 * @param enabled           Si la barra acepta interacción.
 */
@Composable
fun MessageInputBar(
    modifier: Modifier = Modifier,
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit = {},
    onAttachImage: () -> Unit = {},
    onTakePhoto: () -> Unit = {},
    onMicPress: () -> Unit = {},
    onMicRelease: () -> Unit = {},
    onMicCancel: () -> Unit = {},
    isRecording: Boolean = false,
    recordingDuration: String = "",
    enabled: Boolean = true,
) {
    val colors = PrivatiTheme.colors
    val showSend = text.isNotBlank()
    var mostrarMenuAdjuntar by remember { mutableStateOf(false) }

    // rememberUpdatedState captura siempre la versión más reciente de los callbacks
    // en el bloque pointerInput(Unit) que no se recrea en cada recomposición.
    val latestOnSend by rememberUpdatedState(onSend)
    val latestOnMicPress by rememberUpdatedState(onMicPress)
    val latestOnMicRelease by rememberUpdatedState(onMicRelease)
    val latestOnMicCancel by rememberUpdatedState(onMicCancel)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .navigationBarsPadding()  // reserva el hueco de la barra de navegación del sistema
            .imePadding(),            // sube sobre el teclado si está visible
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
            // Botón adjuntar con menú desplegable: galería o cámara
            Box {
                IconButton(
                    onClick = { mostrarMenuAdjuntar = true },
                    enabled = enabled,
                    modifier = Modifier.size(PrivatiSizes.minTouchTarget),
                ) {
                    Icon(
                        imageVector = PrivatiIcons.Attach,
                        contentDescription = "Adjuntar imagen",
                        tint = colors.inkSecondary,
                    )
                }
                DropdownMenu(
                    expanded = mostrarMenuAdjuntar,
                    onDismissRequest = { mostrarMenuAdjuntar = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Galería", style = PrivatiTypography.bodySm) },
                        onClick = {
                            mostrarMenuAdjuntar = false
                            onAttachImage()
                        },
                        leadingIcon = {
                            Icon(PrivatiIcons.Gallery, contentDescription = null)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Cámara", style = PrivatiTypography.bodySm) },
                        onClick = {
                            mostrarMenuAdjuntar = false
                            onTakePhoto()
                        },
                        leadingIcon = {
                            Icon(PrivatiIcons.Camera, contentDescription = null)
                        },
                    )
                }
            }

            // Campo de texto (o indicador de grabación)
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
                            text = if (recordingDuration.isNotEmpty()) {
                                "$recordingDuration · Desliza para cancelar"
                            } else {
                                "Desliza para cancelar"
                            },
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

            // Botón enviar (clic) o micrófono (pulsación larga + arrastre)
            if (showSend) {
                Box(
                    modifier = Modifier
                        .size(PrivatiSizes.iconButton)
                        .clip(PrivatiShapes.pill)
                        .background(colors.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = { latestOnSend() },
                        enabled = enabled,
                        modifier = Modifier.size(PrivatiSizes.iconButton),
                    ) {
                        Icon(
                            imageVector = PrivatiIcons.Send,
                            contentDescription = "Enviar",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            } else {
                // pointerInput(Unit): el bloque no se recrea; los lambdas llegan siempre
                // actualizados gracias a rememberUpdatedState.
                Box(
                    modifier = Modifier
                        .size(PrivatiSizes.iconButton)
                        .clip(PrivatiShapes.pill)
                        .background(if (isRecording) colors.danger else colors.primary)
                        .pointerInput(Unit) {
                            var totalDragX = 0f
                            val cancelThreshold = 80.dp.toPx()
                            var cancelado = false
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    totalDragX = 0f
                                    cancelado = false
                                    latestOnMicPress()
                                },
                                onDrag = { change, offset ->
                                    change.consume()
                                    totalDragX += offset.x
                                    if (!cancelado && totalDragX < -cancelThreshold) {
                                        cancelado = true
                                        latestOnMicCancel()
                                    }
                                },
                                onDragEnd = {
                                    if (!cancelado) latestOnMicRelease()
                                    totalDragX = 0f
                                    cancelado = false
                                },
                                onDragCancel = {
                                    if (!cancelado) latestOnMicCancel()
                                    totalDragX = 0f
                                    cancelado = false
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = PrivatiIcons.Mic,
                        contentDescription = "Grabar nota de voz (mantener pulsado)",
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
            MessageInputBar(text = "", onTextChange = {}, isRecording = true, recordingDuration = "0:07")
        }
    }
}
