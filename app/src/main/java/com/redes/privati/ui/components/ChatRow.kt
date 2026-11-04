package com.redes.privati.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.redes.privati.data.ChatSummary
import com.redes.privati.data.ChatTipo
import com.redes.privati.ui.icons.PrivatiIcons
import com.redes.privati.ui.theme.PrivatiSizes
import com.redes.privati.ui.theme.PrivatiSpacing
import com.redes.privati.ui.theme.PrivatiTheme
import com.redes.privati.ui.theme.PrivatiTypography
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Fila de chat en la lista de Chats.
 * Muestra el avatar, el nombre del chat, el último mensaje, la hora,
 * el icono de fijado y la insignia de no leídos.
 *
 * @param modifier Modificador externo.
 * @param summary Resumen del chat a mostrar.
 * @param showDivider Si se muestra un separador debajo de la fila.
 */
@Composable
fun ChatRow(
    modifier: Modifier = Modifier,
    summary: ChatSummary,
    showDivider: Boolean = true,
) {
    val colors = PrivatiTheme.colors

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = PrivatiSpacing.screenHorizontal,
                    vertical = PrivatiSpacing.sm + 2.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PrivatiSpacing.md),
        ) {
            // Avatar del chat (usa el chatId como clave de color)
            Avatar(
                participantId = summary.chatId,
                name = summary.nombre,
                size = PrivatiSizes.avatarLg,
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Nombre del chat
                    Text(
                        text = summary.nombre,
                        style = PrivatiTypography.bodyStrong,
                        color = colors.ink,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // Icono de fijado
                        if (summary.fijado) {
                            Icon(
                                imageVector = PrivatiIcons.Pin,
                                contentDescription = "Fijado",
                                tint = colors.inkMuted,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        // Hora del último mensaje
                        if (summary.ultimaHora > 0L) {
                            Text(
                                text = formatearHoraChatRow(summary.ultimaHora),
                                style = PrivatiTypography.caption,
                                color = if (summary.noLeidos > 0 && !summary.silenciado)
                                    colors.primary else colors.inkMuted,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Vista previa del último mensaje
                    Text(
                        text = summary.ultimoMensaje.ifBlank { "—" },
                        style = PrivatiTypography.bodySm,
                        color = colors.inkSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    // Insignia de no leídos (solo si no silenciado)
                    if (summary.noLeidos > 0 && !summary.silenciado) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(colors.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (summary.noLeidos > 99) "99+" else summary.noLeidos.toString(),
                                style = PrivatiTypography.micro,
                                color = colors.surface,
                            )
                        }
                    } else if (summary.silenciado) {
                        Icon(
                            imageVector = PrivatiIcons.Mute,
                            contentDescription = "Silenciado",
                            tint = colors.inkMuted,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        if (showDivider) {
            HorizontalDivider(
                color = colors.divider,
                thickness = 1.dp,
                modifier = Modifier.padding(start = PrivatiSpacing.screenHorizontal + PrivatiSizes.avatarLg + PrivatiSpacing.md),
            )
        }
    }
}

/** Formatea un timestamp para la columna de hora: HH:mm si es hoy, o día abreviado si no. */
private fun formatearHoraChatRow(timestamp: Long): String {
    val hoy = Calendar.getInstance()
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    return if (hoy.get(Calendar.DATE) == cal.get(Calendar.DATE) &&
        hoy.get(Calendar.MONTH) == cal.get(Calendar.MONTH) &&
        hoy.get(Calendar.YEAR) == cal.get(Calendar.YEAR)
    ) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    } else {
        SimpleDateFormat("d MMM", Locale("es")).format(Date(timestamp))
    }
}

@Preview(name = "Claro", showBackground = true)
@Preview(name = "Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatRowPreview() {
    PrivatiTheme {
        Column {
            ChatRow(
                summary = ChatSummary(
                    chatId = "sala",
                    tipo = ChatTipo.SALA,
                    nombre = "Chat general",
                    ultimoMensaje = "¡Hola a todos! ¿Cómo estáis?",
                    ultimaHora = System.currentTimeMillis(),
                    noLeidos = 3,
                ),
            )
            ChatRow(
                summary = ChatSummary(
                    chatId = "dm:a:b",
                    tipo = ChatTipo.PRIVADO,
                    nombre = "Ana García",
                    ultimoMensaje = "Nos vemos en 10 minutos",
                    ultimaHora = System.currentTimeMillis() - 3_600_000L,
                    fijado = true,
                ),
            )
            ChatRow(
                summary = ChatSummary(
                    chatId = "sala",
                    tipo = ChatTipo.SALA,
                    nombre = "Chat general (silenciado)",
                    ultimoMensaje = "Mensaje nuevo",
                    ultimaHora = System.currentTimeMillis(),
                    noLeidos = 5,
                    silenciado = true,
                ),
                showDivider = false,
            )
        }
    }
}
