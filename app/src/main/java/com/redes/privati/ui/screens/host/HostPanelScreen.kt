package com.redes.privati.ui.screens.host

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redes.privati.data.Participant
import com.redes.privati.ui.components.ButtonVariant
import com.redes.privati.ui.components.ChatRow
import com.redes.privati.ui.components.PrivatiButton
import com.redes.privati.ui.components.PrivatiTopBar
import com.redes.privati.ui.icons.PrivatiIcons
import com.redes.privati.ui.theme.PrivatiSpacing
import com.redes.privati.ui.theme.PrivatiTheme
import com.redes.privati.ui.theme.PrivatiTypography

/**
 * Panel de control del host. Muestra el código de sala, la IP:puerto,
 * el estado de NFC y la lista de participantes con opción de expulsión.
 *
 * @param onGoToChat Navegación al hilo de conversación.
 * @param onCloseRoom Cierra la sala y vuelve a la pantalla de bienvenida.
 */
@Composable
fun HostPanelScreen(
    onGoToChat: () -> Unit = {},
    onCloseRoom: () -> Unit = {},
) {
    val colors = PrivatiTheme.colors

    // Datos de prueba para la Fase 0
    val roomCode = "X7KP"
    val hostIp = "192.168.1.45"
    val port = 9000
    val participants = remember {
        listOf(
            Participant(id = "host1", name = "Carlos (tú)", isHost = true),
            Participant(id = "c1", name = "Ana García"),
            Participant(id = "c2", name = "Pedro"),
        )
    }

    Scaffold(
        topBar = {
            PrivatiTopBar(
                title = "Panel del anfitrión",
                subtitle = "${participants.size} en la sala",
            )
        },
        containerColor = colors.surface,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PrivatiSpacing.screenHorizontal),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PrivatiSpacing.sm),
            ) {
                Spacer(Modifier.height(PrivatiSpacing.lg))

                Text(
                    text = "Código de sala",
                    style = PrivatiTypography.label,
                    color = colors.inkMuted,
                )

                // Código grande con espaciado entre caracteres
                Text(
                    text = roomCode.map { it.toString() }.joinToString("  "),
                    style = PrivatiTypography.display,
                    color = colors.primary,
                    textAlign = TextAlign.Center,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PrivatiSpacing.sm),
                ) {
                    Text(
                        text = "$hostIp:$port",
                        style = PrivatiTypography.code.copy(fontSize = 14.sp),
                        color = colors.inkSecondary,
                    )
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = PrivatiIcons.Copy,
                            contentDescription = "Copiar IP",
                            tint = colors.inkMuted,
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = PrivatiIcons.Nfc,
                        contentDescription = null,
                        tint = colors.success,
                    )
                    Text(
                        text = "NFC activado · esperando…",
                        style = PrivatiTypography.caption,
                        color = colors.inkMuted,
                    )
                }

                Spacer(Modifier.height(PrivatiSpacing.md))

                HorizontalDivider(color = colors.divider)

                Text(
                    text = "Participantes",
                    style = PrivatiTypography.heading,
                    color = colors.ink,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = PrivatiSpacing.sm),
                )
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(participants) { participant ->
                    ChatRow(
                        participant = participant,
                        isCurrentUser = participant.isHost,
                        canKick = !participant.isHost,
                        onKick = {},
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PrivatiSpacing.screenHorizontal)
                    .padding(bottom = PrivatiSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(PrivatiSpacing.sm),
            ) {
                PrivatiButton(
                    text = "Ir al chat",
                    onClick = onGoToChat,
                    variant = ButtonVariant.Primary,
                )
                PrivatiButton(
                    text = "Cerrar sala",
                    onClick = onCloseRoom,
                    variant = ButtonVariant.Danger,
                )
            }
        }
    }
}

@Preview(name = "Claro", showBackground = true)
@Preview(name = "Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HostPanelScreenPreview() {
    PrivatiTheme {
        HostPanelScreen()
    }
}
