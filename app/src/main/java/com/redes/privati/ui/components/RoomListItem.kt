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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.redes.privati.data.SalaDescubierta
import com.redes.privati.ui.theme.PrivatiShapes
import com.redes.privati.ui.theme.PrivatiSizes
import com.redes.privati.ui.theme.PrivatiSpacing
import com.redes.privati.ui.theme.PrivatiTheme
import com.redes.privati.ui.theme.PrivatiTypography

/**
 * Fila de sala descubierta por NSD en la pantalla de unirse.
 *
 * @param sala      Sala descubierta con código, nombre, host y número de participantes.
 * @param onEntrar  Callback al pulsar «Entrar».
 * @param showDivider Si se muestra un separador al final.
 */
@Composable
fun RoomListItem(
    sala: SalaDescubierta,
    onEntrar: () -> Unit,
    showDivider: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = PrivatiTheme.colors
    val inicial = sala.name.firstOrNull()?.uppercaseChar()?.toString() ?: sala.code.first().toString()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PrivatiSpacing.screenHorizontal, vertical = PrivatiSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PrivatiSpacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(PrivatiSizes.avatarMd)
                    .clip(PrivatiShapes.pill)
                    .background(colors.primaryTint),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = inicial,
                    style = PrivatiTypography.bodyStrong,
                    color = colors.primaryInk,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sala.name,
                    style = PrivatiTypography.bodyStrong,
                    color = colors.ink,
                )
                val personas = if (sala.n == 1) "1 persona" else "${sala.n} personas"
                Text(
                    text = "Host: ${sala.hostName.ifBlank { "—" }} · $personas",
                    style = PrivatiTypography.caption,
                    color = colors.inkSecondary,
                )
            }

            FilledTonalButton(onClick = onEntrar) {
                Text("Entrar", style = PrivatiTypography.label)
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
private fun RoomListItemPreview() {
    PrivatiTheme {
        Column {
            RoomListItem(
                sala = SalaDescubierta("X7KP", "Sala de Carlos", "Carlos", 3, "192.168.1.45", 9000),
                onEntrar = {},
            )
            RoomListItem(
                sala = SalaDescubierta("AB12", "Mi Sala", "Ana", 1, "192.168.1.56", 9000),
                onEntrar = {},
                showDivider = false,
            )
        }
    }
}
