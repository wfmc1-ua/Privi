package com.redes.privati.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.redes.privati.data.Participant
import com.redes.privati.ui.icons.PrivatiIcons
import com.redes.privati.ui.theme.PrivatiSizes
import com.redes.privati.ui.theme.PrivatiSpacing
import com.redes.privati.ui.theme.PrivatiTheme
import com.redes.privati.ui.theme.PrivatiTypography

/**
 * Fila de participante para el panel del host y la pestaña de Contactos.
 * Muestra el avatar, el nombre y, opcionalmente, un botón de expulsión.
 *
 * @param modifier Modificador externo.
 * @param participant Datos del participante.
 * @param isCurrentUser Si este participante es el dispositivo actual.
 * @param canKick Si el host puede expulsar a este participante (falso para el propio host).
 * @param onKick Callback al pulsar el botón de expulsión.
 * @param onClick Callback al tocar la fila; null para filas no navegables.
 * @param subtitle Texto secundario opcional bajo el nombre (p. ej. «Entró por NFC · ahora»).
 * @param showDivider Si se muestra un separador debajo de la fila.
 */
@Composable
fun ParticipantRow(
    modifier: Modifier = Modifier,
    participant: Participant,
    isCurrentUser: Boolean = false,
    canKick: Boolean = false,
    onKick: () -> Unit = {},
    onClick: (() -> Unit)? = null,
    subtitle: String? = null,
    showDivider: Boolean = true,
) {
    val colors = PrivatiTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .run { if (onClick != null) clickable(onClick = onClick) else this },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = PrivatiSpacing.screenHorizontal,
                    vertical = PrivatiSpacing.sm,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PrivatiSpacing.md),
        ) {
            Avatar(
                participantId = participant.id,
                name = participant.name,
                size = PrivatiSizes.avatarMd,
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = participant.name,
                        style = PrivatiTypography.bodyStrong,
                        color = colors.ink,
                    )
                    if (participant.isHost) {
                        Text(
                            text = "host",
                            style = PrivatiTypography.micro,
                            color = colors.primaryInk,
                        )
                    }
                    if (isCurrentUser) {
                        Text(
                            text = "tú",
                            style = PrivatiTypography.micro,
                            color = colors.inkMuted,
                        )
                    }
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = PrivatiTypography.caption,
                        color = colors.inkMuted,
                    )
                }
            }

            if (canKick) {
                IconButton(
                    onClick = onKick,
                    modifier = Modifier.size(PrivatiSizes.minTouchTarget),
                ) {
                    Icon(
                        imageVector = PrivatiIcons.Kick,
                        contentDescription = "Expulsar a ${participant.name}",
                        tint = colors.danger,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        if (showDivider) {
            HorizontalDivider(
                color = colors.divider,
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = PrivatiSpacing.screenHorizontal),
            )
        }
    }
}

@Preview(name = "Claro", showBackground = true)
@Preview(name = "Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ParticipantRowPreview() {
    PrivatiTheme {
        Column {
            ParticipantRow(
                participant = Participant(id = "h1", name = "Carlos", isHost = true),
                isCurrentUser = true,
                canKick = false,
            )
            ParticipantRow(
                participant = Participant(id = "c1", name = "Ana García"),
                canKick = true,
                onKick = {},
            )
            ParticipantRow(
                participant = Participant(id = "c2", name = "Pedro"),
                canKick = true,
                showDivider = false,
            )
        }
    }
}
