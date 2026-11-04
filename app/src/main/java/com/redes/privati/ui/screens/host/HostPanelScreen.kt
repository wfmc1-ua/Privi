package com.redes.privati.ui.screens.host

import android.content.res.Configuration
import android.nfc.NfcAdapter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.redes.privati.data.ConnectionStatus
import com.redes.privati.data.Participant
import com.redes.privati.data.PendingRequestInfo
import com.redes.privati.data.RoomState
import com.redes.privati.discovery.nfc.CorroHceService
import com.redes.privati.ui.components.Avatar
import com.redes.privati.ui.components.ButtonVariant
import com.redes.privati.ui.components.NfcInviteCard
import com.redes.privati.ui.components.ParticipantRow
import com.redes.privati.ui.components.PillTabs
import com.redes.privati.ui.components.PrivatiButton
import com.redes.privati.ui.components.PrivatiTopBar
import com.redes.privati.ui.icons.PrivatiIcons
import com.redes.privati.ui.theme.PrivatiShapes
import com.redes.privati.ui.theme.PrivatiSizes
import com.redes.privati.ui.theme.PrivatiSpacing
import com.redes.privati.ui.theme.PrivatiTheme
import com.redes.privati.ui.theme.PrivatiTypography
import kotlin.math.max

@Composable
fun HostPanelScreen(
    viewModel: HostViewModel,
    onGoToChat: () -> Unit = {},
    onCloseRoom: () -> Unit = {},
) {
    val context = LocalContext.current
    val roomState by viewModel.roomState.collectAsState()
    val pendingRequests by viewModel.pendingRequests.collectAsState()

    val hostIp = roomState.hostIp
    val port = roomState.port
    val roomCode = roomState.roomCode
    val roomSecret = roomState.roomSecret
    val salaKey = "$hostIp:$port:$roomCode:$roomSecret"

    DisposableEffect(salaKey) {
        if (hostIp.isNotBlank() && port > 0 && roomCode.isNotBlank()) {
            CorroHceService.salaActiva = salaKey
        }
        onDispose {
            CorroHceService.salaActiva = null
        }
    }

    val nfcActivo = remember { NfcAdapter.getDefaultAdapter(context)?.isEnabled == true }

    HostPanelContent(
        roomState = roomState,
        pendingRequests = pendingRequests,
        nfcActivo = nfcActivo,
        onGoToChat = onGoToChat,
        onCloseRoom = onCloseRoom,
        onKick = viewModel::expulsarParticipante,
        onAceptar = viewModel::aceptarSolicitud,
        onRechazar = viewModel::rechazarSolicitud,
        onRenombrar = viewModel::renombrarSala,
    )
}

@Composable
private fun HostPanelContent(
    roomState: RoomState,
    pendingRequests: List<PendingRequestInfo> = emptyList(),
    nfcActivo: Boolean = false,
    onGoToChat: () -> Unit = {},
    onCloseRoom: () -> Unit = {},
    onKick: (participantId: String) -> Unit = {},
    onAceptar: (remoteId: String) -> Unit = {},
    onRechazar: (remoteId: String) -> Unit = {},
    onRenombrar: (String) -> Unit = {},
) {
    val colors = PrivatiTheme.colors

    var selectedTab by rememberSaveable(pendingRequests.isNotEmpty()) {
        mutableIntStateOf(if (pendingRequests.isNotEmpty()) 1 else 0)
    }

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember(roomState.roomName) { mutableStateOf(roomState.roomName) }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Renombrar sala") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Nombre de la sala") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) {
                        onRenombrar(renameText)
                        showRenameDialog = false
                    }
                }) { Text("Renombrar") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancelar") }
            },
        )
    }

    androidx.compose.material3.Scaffold(
        topBar = {
            PrivatiTopBar(
                title = roomState.roomName.ifBlank { "Mi sala" },
                subtitle = "${roomState.participants.size} en la sala",
                actions = {
                    IconButton(onClick = { showRenameDialog = true }) {
                        Icon(
                            imageVector = PrivatiIcons.Edit,
                            contentDescription = "Renombrar sala",
                            tint = colors.inkSecondary,
                        )
                    }
                },
            )
        },
        containerColor = colors.surface,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),  // marca los insets del Scaffold como consumidos
        ) {
            // ── Badge "Eres el host" + tarjeta NFC ───────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PrivatiSpacing.screenHorizontal),
                verticalArrangement = Arrangement.spacedBy(PrivatiSpacing.sm),
            ) {
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = PrivatiSpacing.sm))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(PrivatiShapes.pill)
                            .background(colors.success),
                    )
                    Text(
                        text = "Eres el host",
                        style = PrivatiTypography.caption,
                        color = colors.inkMuted,
                    )
                }

                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = PrivatiSpacing.xs))

                NfcInviteCard(
                    roomCode = roomState.roomCode.ifBlank { "—" },
                    hostIp = roomState.hostIp,
                    port = roomState.port,
                    nfcActivo = nfcActivo,
                )

                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = PrivatiSpacing.xs))
            }

            // ── Pestañas Conectados / Solicitudes ────────────────────────────
            PillTabs(
                tabs = listOf(
                    "Conectados" to 0,
                    "Solicitudes" to pendingRequests.size,
                ),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
            )

            HorizontalDivider(color = colors.divider)

            when (selectedTab) {
                0 -> {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(roomState.participants, key = { it.id }) { participant ->
                            val esUsuarioActual = participant.id == roomState.localUserId
                            val subtitle = when {
                                participant.isHost -> "Anfitrión"
                                participant.joinedAt > 0 -> {
                                    val metodo = when (participant.joinMethod) {
                                        "nfc" -> "Entró por NFC"
                                        "code" -> "Entró con el código"
                                        else -> "Conectado"
                                    }
                                    "$metodo · ${tiempoLabel(participant.joinedAt)}"
                                }
                                else -> null
                            }
                            ParticipantRow(
                                participant = participant,
                                isCurrentUser = esUsuarioActual,
                                canKick = !esUsuarioActual && !participant.isHost,
                                onKick = { onKick(participant.id) },
                                subtitle = subtitle,
                            )
                        }
                    }
                }
                else -> {
                    if (pendingRequests.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Sin solicitudes pendientes",
                                style = PrivatiTypography.body,
                                color = colors.inkMuted,
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(pendingRequests, key = { it.remoteId }) { solicitud ->
                                SolicitudRow(
                                    solicitud = solicitud,
                                    onAceptar = { onAceptar(solicitud.remoteId) },
                                    onRechazar = { onRechazar(solicitud.remoteId) },
                                )
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()           // reserva barra de sistema (0 si ya consumida)
                    .imePadding()                      // reserva teclado (siempre 0 aquí, por consistencia)
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

private fun tiempoLabel(joinedAt: Long): String {
    val diff = System.currentTimeMillis() - joinedAt
    return when {
        diff < 60_000L -> "ahora"
        diff < 3_600_000L -> "hace ${diff / 60_000}min"
        else -> "hace ${diff / 3_600_000}h"
    }
}

@Composable
private fun SolicitudRow(
    solicitud: PendingRequestInfo,
    onAceptar: () -> Unit,
    onRechazar: () -> Unit,
) {
    val colors = PrivatiTheme.colors
    val segundosTranscurridos = max(0L, (System.currentTimeMillis() - solicitud.timestampMs) / 1000)
    val tiempoLabel = when {
        segundosTranscurridos < 60 -> "hace ${segundosTranscurridos}s"
        else -> "hace ${segundosTranscurridos / 60}min"
    }
    val metodoLabel = when (solicitud.joinMethod) {
        "code" -> "Por código"
        "nfc" -> "Por NFC"
        else -> "Por IP"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PrivatiSpacing.screenHorizontal, vertical = PrivatiSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PrivatiSpacing.sm),
    ) {
        Avatar(
            participantId = solicitud.participantId,
            name = solicitud.participantName,
            size = PrivatiSizes.avatarMd,
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = solicitud.participantName,
                style = PrivatiTypography.body,
                color = colors.ink,
            )
            Text(
                text = "$metodoLabel · $tiempoLabel",
                style = PrivatiTypography.caption,
                color = colors.inkMuted,
            )
        }

        FilledTonalButton(onClick = onAceptar) {
            Text("Aceptar", style = PrivatiTypography.label)
        }

        IconButton(onClick = onRechazar) {
            Icon(
                imageVector = PrivatiIcons.Close,
                contentDescription = "Rechazar",
                tint = colors.danger,
                modifier = Modifier.size(20.dp),
            )
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
                roomName = "Sala de Carlos",
            ),
            nfcActivo = true,
        )
    }
}

@Preview(name = "Con solicitudes", showBackground = true)
@Composable
private fun HostPanelPreviewSolicitudes() {
    PrivatiTheme {
        HostPanelContent(
            roomState = RoomState(
                localUserId = "host1",
                roomCode = "X7KP",
                hostIp = "192.168.1.45",
                port = 9000,
                participants = listOf(
                    Participant(id = "host1", name = "Carlos", isHost = true),
                    Participant(id = "c1", name = "Ana", joinMethod = "nfc", joinedAt = System.currentTimeMillis() - 180_000),
                ),
                connectionStatus = ConnectionStatus.ONLINE,
                roomName = "Sala de Carlos",
            ),
            pendingRequests = listOf(
                PendingRequestInfo("r1", "p1", "Pedro", "code", System.currentTimeMillis() - 20_000),
            ),
        )
    }
}
