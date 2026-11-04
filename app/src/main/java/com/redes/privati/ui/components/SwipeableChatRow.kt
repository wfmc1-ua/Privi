package com.redes.privati.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.redes.privati.data.ChatSummary
import com.redes.privati.data.ChatTipo
import com.redes.privati.ui.icons.PrivatiIcons
import com.redes.privati.ui.theme.PrivatiTheme
import com.redes.privati.ui.theme.PrivatiTypography
import kotlin.math.roundToInt

/**
 * Fila de chat con soporte de deslizamiento a la izquierda para revelar acciones.
 * - Deslizar izquierda → Silenciar (warning) + Eliminar (danger).
 * - Pulsación larga → fijar/desfijar.
 * - Tocar cuando está abierta → cerrar sin acción.
 * - El chat «sala» no puede eliminarse (el botón Eliminar se deshabilita).
 *
 * @param modifier Modificador externo.
 * @param summary Resumen del chat.
 * @param onClick Acción al tocar la fila (navegar a la conversación).
 * @param onSilenciar Acción al pulsar «Silenciar».
 * @param onEliminar Acción al pulsar «Eliminar»; nula si el chat no se puede eliminar.
 * @param onFijar Acción de pulsación larga para fijar/desfijar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeableChatRow(
    modifier: Modifier = Modifier,
    summary: ChatSummary,
    onClick: () -> Unit = {},
    onSilenciar: () -> Unit = {},
    onEliminar: (() -> Unit)? = null,
    onFijar: () -> Unit = {},
) {
    val colors = PrivatiTheme.colors
    val density = LocalDensity.current

    // Ancho del panel de acciones: 70 dp por botón × 2
    val revealWidth = 140.dp
    val revealWidthPx = with(density) { revealWidth.toPx() }

    var revealado by remember { mutableStateOf(false) }
    var dragAccum by remember { mutableFloatStateOf(0f) }

    val animatedOffset by animateFloatAsState(
        targetValue = if (revealado) -revealWidthPx else 0f,
        animationSpec = spring(),
        label = "swipe",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp),
    ) {
        // ── Panel de acciones (siempre detrás) ──────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.End,
        ) {
            // Botón Silenciar
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .background(colors.warning)
                    .combinedClickable(onClick = {
                        onSilenciar()
                        revealado = false
                    }),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (summary.silenciado) PrivatiIcons.Bell else PrivatiIcons.Mute,
                        contentDescription = if (summary.silenciado) "Activar" else "Silenciar",
                        tint = colors.warningInk,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = if (summary.silenciado) "Activar" else "Silenciar",
                        style = PrivatiTypography.micro,
                        color = colors.warningInk,
                    )
                }
            }

            // Botón Eliminar (deshabilitado para sala)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .background(if (onEliminar != null) colors.danger else colors.dangerTint)
                    .combinedClickable(
                        enabled = onEliminar != null,
                        onClick = {
                            onEliminar?.invoke()
                            revealado = false
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = PrivatiIcons.Delete,
                        contentDescription = "Eliminar",
                        tint = if (onEliminar != null) colors.surface else colors.dangerInk,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Eliminar",
                        style = PrivatiTypography.micro,
                        color = if (onEliminar != null) colors.surface else colors.inkMuted,
                    )
                }
            }
        }

        // ── Fila principal (encima, se desplaza) ────────────────────────────
        ChatRow(
            summary = summary,
            showDivider = true,
            modifier = Modifier
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .background(colors.surface)
                .pointerInput(summary.chatId) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragAccum = 0f },
                        onDragEnd = {
                            revealado = dragAccum < -(revealWidthPx / 3)
                            dragAccum = 0f
                        },
                        onDragCancel = { dragAccum = 0f; revealado = false },
                        onHorizontalDrag = { _, delta ->
                            dragAccum += delta
                        },
                    )
                }
                .combinedClickable(
                    onClick = {
                        if (revealado) revealado = false else onClick()
                    },
                    onLongClick = onFijar,
                )
                .padding(top = 0.dp), // alinear con el Box padre
        )
    }
}

@Preview(name = "Claro", showBackground = true)
@Preview(name = "Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SwipeableChatRowPreview() {
    PrivatiTheme {
        SwipeableChatRow(
            summary = ChatSummary(
                chatId = "sala",
                tipo = ChatTipo.SALA,
                nombre = "Chat general",
                ultimoMensaje = "Hola, ¿qué tal?",
                ultimaHora = System.currentTimeMillis(),
                noLeidos = 2,
            ),
            onEliminar = null, // sala no se elimina
        )
    }
}
