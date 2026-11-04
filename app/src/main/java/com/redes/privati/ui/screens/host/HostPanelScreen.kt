package com.redes.privati.ui.screens.host

import android.content.res.Configuration
import android.nfc.NfcAdapter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redes.privati.data.ConnectionStatus
import com.redes.privati.data.Participant
import com.redes.privati.data.RoomState
import com.redes.privati.discovery.nfc.CorroHceService
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
 * el estado NFC y NSD, y la lista de participantes con opción de expulsión.
 *
 * Mientras esta pantalla está visible publica los datos de sala en
 * [CorroHceService.salaActiva] para que el HCE pueda responder a los lectores NFC.
 * Al salir de la pantalla limpia esa variable.
 *
 * @param viewModel ViewModel que provee el estado del servidor.
 * @param onGoToChat Navega al hilo de conversación.
 * @param onCloseRoom Cierra la sala y vuelve a la pantalla de bienvenida.
 */
@Composable
fun HostPanelScreen(
    viewModel: HostViewModel,
    onGoToChat: () -> Unit = {},
    onCloseRoom: () -> Unit = {},
) {
    val context = LocalContext.current
    val roomState by viewModel.roomState.collectAsState()

    // Publicar los datos de sala en el HCE mientras el panel esté en pantalla.
    // La clave combina los tres valores: si cambian (no ocurre en operación normal)
    // se limpia y vuelve a establecer salaActiva con los datos nuevos.
    val hostIp = roomState.hostIp
    val port = roomState.port
    val roomCode = roomState.roomCode
    val salaKey = "$hostIp:$port:$roomCode"

    DisposableEffect(salaKey) {
        if (hostIp.isNotBlank() && port > 0 && roomCode.isNotBlank()) {
            CorroHceService.salaActiva = salaKey
        }
        onDispose {
            CorroHceService.salaActiva = null
        }
    }

    // Comprobar si NFC está disponible y activo en este dispositivo
    val nfcActivo = remember { NfcAdapter.getDefaultAdapter(context)?.isEnabled == true }

    HostPanelContent(
        roomState = roomState,
        nfcActivo = nfcActivo,
        onGoToChat = onGoToChat,
        onCloseRoom = onCloseRoom,
        onKick = viewModel::expulsarParticipante,
    )
}

@Composable
private fun HostPanelContent(
    roomState: RoomState,
    nfcActivo: Boolean = false,
    onGoToChat: () -> Unit = {},
    onCloseRoom: () -> Unit = {},
    onKick: (participantId: String) -> Unit = {},
) {
    val colors = PrivatiTheme.colors
    val esperando = roomState.connectionStatus == ConnectionStatus.DISCONNECTED

    Scaffold(
        topBar = {
            PrivatiTopBar(
                title = "Panel del anfitrión",
                subtitle = "${roomState.participants.size} en la sala",
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

                Text(
                    text = roomState.roomCode.ifBlank { "—" }
                        .map { it.toString() }.joinToString("  "),
                    style = PrivatiTypography.display,
                    color = colors.primary,
                    textAlign = TextAlign.Center,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PrivatiSpacing.sm),
                ) {
                    Text(
                        text = "${roomState.hostIp}:${roomState.port}",
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

                // Estado NSD
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (esperando) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colors.inkMuted,
                        )
                        Text(
                            text = "NSD activo · esperando conexión…",
                            style = PrivatiTypography.caption,
                            color = colors.inkMuted,
                        )
                    } else {
                        Icon(
                            imageVector = PrivatiIcons.Check,
                            contentDescription = null,
                            tint = colors.success,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Cliente conectado · entrando al chat…",
                            style = PrivatiTypography.caption,
                            color = colors.success,
                        )
                    }
                }

                // Estado NFC (solo si el hardware está disponible y activo)
                if (nfcActivo) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = PrivatiIcons.Nfc,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "NFC activado · acerca tu móvil",
                            style = PrivatiTypography.caption,
                            color = colors.primary,
                        )
                    }
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
                items(roomState.participants, key = { it.id }) { participant ->
                    val esUsuarioActual = participant.id == roomState.localUserId
                    ChatRow(
                        participant = participant,
                        isCurrentUser = esUsuarioActual,
                        canKick = !esUsuarioActual && !participant.isHost,
                        onKick = { onKick(participant.id) },
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
                    enabled = roomState.connectionStatus == ConnectionStatus.ONLINE,
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

@Preview(name = "Esperando · Claro", showBackground = true)
@Preview(name = "Esperando · Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HostPanelPreviewEsperando() {
    PrivatiTheme {
        HostPanelContent(
            roomState = RoomState(
                localUserId = "host1",
                roomCode = "X7KP",
                hostIp = "192.168.1.45",
                port = 9000,
                participants = listOf(Participant(id = "host1", name = "Carlos (tú)", isHost = true)),
                connectionStatus = ConnectionStatus.DISCONNECTED,
            ),
            nfcActivo = true,
            onCloseRoom = {},
        )
    }
}

@Preview(name = "Conectado", showBackground = true)
@Composable
private fun HostPanelPreviewConectado() {
    PrivatiTheme {
        HostPanelContent(
            roomState = RoomState(
                localUserId = "host1",
                roomCode = "X7KP",
                hostIp = "192.168.1.45",
                port = 9000,
                participants = listOf(
                    Participant(id = "host1", name = "Carlos (tú)", isHost = true),
                    Participant(id = "c1", name = "Ana García"),
                ),
                connectionStatus = ConnectionStatus.ONLINE,
            ),
            nfcActivo = false,
            onCloseRoom = {},
        )
    }
}
