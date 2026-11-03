package com.redes.privati.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.redes.privati.data.ConnectionStatus
import com.redes.privati.ui.theme.PrivatiShapes
import com.redes.privati.ui.theme.PrivatiTheme
import com.redes.privati.ui.theme.PrivatiTypography

/**
 * Píldora de estado de conexión mostrada en la cabecera de la conversación.
 * El texto va en gerundio: «En línea», «Reconectando…», «Desconectado».
 *
 * @param modifier Modificador externo.
 * @param status Estado de conexión actual.
 */
@Composable
fun StatusPill(
    modifier: Modifier = Modifier,
    status: ConnectionStatus,
) {
    val colors = PrivatiTheme.colors

    val (label, dotColor, bgColor) = when (status) {
        ConnectionStatus.ONLINE -> Triple(
            "En línea",
            colors.success,
            colors.primarySubtle,
        )
        ConnectionStatus.RECONNECTING -> Triple(
            "Reconectando…",
            colors.inkMuted,
            colors.surfaceRaised,
        )
        ConnectionStatus.DISCONNECTED -> Triple(
            "Desconectado",
            colors.danger,
            colors.dangerTint,
        )
    }

    Row(
        modifier = modifier
            .clip(PrivatiShapes.pill)
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(PrivatiShapes.pill)
                .background(dotColor),
        )
        Text(
            text = label,
            style = PrivatiTypography.caption,
            color = when (status) {
                ConnectionStatus.ONLINE -> colors.primaryInk
                ConnectionStatus.RECONNECTING -> colors.inkSecondary
                ConnectionStatus.DISCONNECTED -> colors.dangerInk
            },
        )
    }
}

@Preview(name = "Claro", showBackground = true)
@Preview(name = "Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StatusPillPreview() {
    PrivatiTheme {
        androidx.compose.foundation.layout.Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            StatusPill(status = ConnectionStatus.ONLINE)
            StatusPill(status = ConnectionStatus.RECONNECTING)
            StatusPill(status = ConnectionStatus.DISCONNECTED)
        }
    }
}
