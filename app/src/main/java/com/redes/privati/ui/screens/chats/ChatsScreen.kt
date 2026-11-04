package com.redes.privati.ui.screens.chats

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.redes.privati.data.ChatSummary
import com.redes.privati.data.ChatTipo
import com.redes.privati.data.ConnectionStatus
import com.redes.privati.data.Participant
import com.redes.privati.data.RoomState
import com.redes.privati.ui.components.Avatar
import com.redes.privati.ui.components.BottomNavBar
import com.redes.privati.ui.components.BottomNavPane
import com.redes.privati.ui.components.ButtonVariant
import com.redes.privati.ui.components.ParticipantRow
import com.redes.privati.ui.components.PillTabs
import com.redes.privati.ui.components.PrivatiButton
import com.redes.privati.ui.components.PrivatiTextField
import com.redes.privati.ui.components.SelectableChip
import com.redes.privati.ui.components.StatusPill
import com.redes.privati.ui.components.SwipeableChatRow
import com.redes.privati.ui.icons.PrivatiIcons
import com.redes.privati.ui.theme.PrivatiSizes
import com.redes.privati.ui.theme.PrivatiSpacing
import com.redes.privati.ui.theme.PrivatiTheme
import com.redes.privati.ui.theme.PrivatiTypography

/**
 * Pantalla principal de la sala: lista de chats, contactos y ajustes.
 * Muestra una barra de navegación inferior flotante en píldora.
 *
 * @param viewModel ViewModel de esta pantalla.
 * @param onOpenChat Navega a la conversación del chat indicado por [chatId].
 * @param onBackToPanel Navega al panel del host (solo host).
 * @param onDisconnect Desconecta y navega a Bienvenida (solo cliente).
 */
@Composable
fun ChatsScreen(
    viewModel: ChatsViewModel,
    onOpenChat: (chatId: String) -> Unit = {},
    onBackToPanel: () -> Unit = {},
    onDisconnect: () -> Unit = {},
) {
    val chats by viewModel.chats.collectAsState()
    val roomState by viewModel.roomState.collectAsState()
    val esHost = viewModel.esHost
    val pendingRequestCount by viewModel.pendingRequestCount.collectAsState()

    // Navegar automáticamente al grupo recién creado cuando el repositorio lo emite
    LaunchedEffect(Unit) {
        viewModel.nuevoChatId.collect { chatId -> onOpenChat(chatId) }
    }

    ChatsContent(
        chats = chats,
        roomState = roomState,
        esHost = esHost,
        pendingRequestCount = pendingRequestCount,
        onOpenChat = onOpenChat,
        onOpenContact = { contactId -> onOpenChat(viewModel.dmChatId(contactId)) },
        onCrearGrupo = viewModel::crearGrupo,
        onBackToPanel = onBackToPanel,
        onDisconnect = {
            viewModel.desconectar()
            onDisconnect()
        },
        onSilenciar = viewModel::toggleSilenciar,
        onFijar = viewModel::toggleFijar,
        onEliminar = viewModel::eliminar,
    )
}

@Composable
private fun ChatsContent(
    chats: List<ChatSummary>,
    roomState: RoomState,
    esHost: Boolean,
    pendingRequestCount: Int = 0,
    onOpenChat: (String) -> Unit,
    onOpenContact: (contactId: String) -> Unit,
    onCrearGrupo: (nombre: String, memberIds: List<String>) -> Unit,
    onBackToPanel: () -> Unit,
    onDisconnect: () -> Unit,
    onSilenciar: (String) -> Unit,
    onFijar: (String) -> Unit,
    onEliminar: (String) -> Unit,
) {
    val colors = PrivatiTheme.colors

    // Pestaña de la barra inferior (Contactos / Chats / Ajustes)
    var paneActiva by remember { mutableStateOf(BottomNavPane.CHATS) }

    // Pestaña de la lista de chats (Personal / Todos / Grupos)
    var tabActivo by remember { mutableStateOf(1) } // 0=Personal 1=Todos 2=Grupos

    // Diálogo de confirmación para salir de la sala (solo cliente)
    var mostrarDialogoSalir by remember { mutableStateOf(false) }

    // Interceptar el botón Atrás:
    // host → volver al panel; cliente → mostrar diálogo de salida
    BackHandler {
        if (esHost) onBackToPanel() else mostrarDialogoSalir = true
    }

    if (mostrarDialogoSalir) {
        AlertDialog(
            onDismissRequest = { mostrarDialogoSalir = false },
            title = { Text("¿Salir de la sala?") },
            text = { Text("Perderás el historial de conversación. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    mostrarDialogoSalir = false
                    onDisconnect()
                }) { Text("Salir", color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogoSalir = false }) { Text("Cancelar") }
            },
        )
    }

    Scaffold(
        containerColor = colors.surface,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),  // marca los insets del Scaffold como consumidos
        ) {
            // ── Contenido de la pestaña activa ───────────────────────────────
            when (paneActiva) {
                BottomNavPane.CHATS -> ChatsPane(
                    chats = chats,
                    roomState = roomState,
                    esHost = esHost,
                    pendingRequestCount = pendingRequestCount,
                    tabActivo = tabActivo,
                    onTabChange = { tabActivo = it },
                    onOpenChat = onOpenChat,
                    onSilenciar = onSilenciar,
                    onFijar = onFijar,
                    onEliminar = onEliminar,
                    onBackToPanel = onBackToPanel,
                )
                BottomNavPane.CONTACTS -> ContactsPane(
                    roomState = roomState,
                    onOpenContact = onOpenContact,
                    onCrearGrupo = onCrearGrupo,
                )
                BottomNavPane.SETTINGS -> SettingsPane(
                    roomState = roomState,
                    esHost = esHost,
                    onBackToPanel = onBackToPanel,
                    onSalir = { mostrarDialogoSalir = true },
                    onCerrarSala = onDisconnect,
                )
            }

            // ── Barra de navegación flotante ─────────────────────────────────
            BottomNavBar(
                selected = paneActiva,
                onSelect = { paneActiva = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = PrivatiSpacing.lg),
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Pestaña Chats
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChatsPane(
    chats: List<ChatSummary>,
    roomState: RoomState,
    esHost: Boolean,
    pendingRequestCount: Int = 0,
    tabActivo: Int,
    onTabChange: (Int) -> Unit,
    onOpenChat: (String) -> Unit,
    onSilenciar: (String) -> Unit,
    onFijar: (String) -> Unit,
    onEliminar: (String) -> Unit,
    onBackToPanel: () -> Unit,
) {
    val colors = PrivatiTheme.colors

    // Nombre de la sala: «Sala de [host]»
    val nombreHost = roomState.participants.firstOrNull { it.isHost }?.name ?: ""
    val nombreSala = if (nombreHost.isNotBlank()) "Sala de $nombreHost" else "Sala"

    // Contadores de no leídos por pestaña
    val noLeidosPersonal = chats.filter { it.tipo == ChatTipo.PRIVADO }.sumOf { it.noLeidos }
    val noLeidosTodos = chats.sumOf { it.noLeidos }
    val noLeidosGrupos = chats.filter { it.tipo == ChatTipo.SALA || it.tipo == ChatTipo.GRUPO }
        .sumOf { it.noLeidos }

    // Lista filtrada según la pestaña activa, fijados primero, luego por hora descendente
    val chatsFiltrados = chats
        .filter { resumen ->
            when (tabActivo) {
                0 -> resumen.tipo == ChatTipo.PRIVADO
                1 -> true
                2 -> resumen.tipo == ChatTipo.SALA || resumen.tipo == ChatTipo.GRUPO
                else -> true
            }
        }
        .sortedWith(compareByDescending<ChatSummary> { it.fijado }.thenByDescending { it.ultimaHora })

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Top bar ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = PrivatiSpacing.screenHorizontal,
                    vertical = PrivatiSpacing.sm,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Chats",
                    style = PrivatiTypography.title2,
                    color = colors.ink,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Punto de estado
                    Canvas(modifier = Modifier.size(6.dp)) {
                        drawCircle(
                            color = if (roomState.connectionStatus == ConnectionStatus.ONLINE)
                                colors.success else colors.inkMuted
                        )
                    }
                    Text(
                        text = "$nombreSala · ${roomState.participants.size} en línea",
                        style = PrivatiTypography.caption,
                        color = colors.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Icono buscar
            IconButton(onClick = { /* Fase 14: búsqueda local */ }) {
                Icon(
                    imageVector = PrivatiIcons.Search,
                    contentDescription = "Buscar",
                    tint = colors.inkSecondary,
                )
            }

            // Campana del host con insignia de solicitudes pendientes
            if (esHost) {
                IconButton(onClick = onBackToPanel) {
                    BadgedBox(
                        badge = {
                            if (pendingRequestCount > 0) {
                                Badge { Text("$pendingRequestCount") }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = PrivatiIcons.Bell,
                            contentDescription = "Volver al panel",
                            tint = if (pendingRequestCount > 0) colors.primary else colors.inkSecondary,
                        )
                    }
                }
            }

            // Indicador de cifrado y conexión
            StatusPill(status = roomState.connectionStatus)
        }

        // ── Pestañas Personal / Todos / Grupos ───────────────────────────────
        PillTabs(
            tabs = listOf(
                "Personal" to noLeidosPersonal,
                "Todos" to noLeidosTodos,
                "Grupos" to noLeidosGrupos,
            ),
            selectedIndex = tabActivo,
            onSelect = onTabChange,
        )

        // ── Lista de chats ───────────────────────────────────────────────────
        if (chatsFiltrados.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Sin chats en esta sección",
                    style = PrivatiTypography.body,
                    color = colors.inkMuted,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 100.dp),
            ) {
                items(chatsFiltrados, key = { it.chatId }) { resumen ->
                    SwipeableChatRow(
                        summary = resumen,
                        onClick = { onOpenChat(resumen.chatId) },
                        onSilenciar = { onSilenciar(resumen.chatId) },
                        onEliminar = if (resumen.chatId != "sala") ({ onEliminar(resumen.chatId) }) else null,
                        onFijar = { onFijar(resumen.chatId) },
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Pestaña Contactos (Fase 11: tocar → privado | Fase 12: Nuevo grupo)
// ──────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactsPane(
    roomState: RoomState,
    onOpenContact: (contactId: String) -> Unit,
    onCrearGrupo: (nombre: String, memberIds: List<String>) -> Unit,
) {
    val colors = PrivatiTheme.colors
    val contactos = roomState.participants.filter { it.id != roomState.localUserId }

    var mostrarSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PrivatiSpacing.screenHorizontal, vertical = PrivatiSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Contactos",
                style = PrivatiTypography.title2,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            if (contactos.isNotEmpty()) {
                IconButton(onClick = { mostrarSheet = true }) {
                    Icon(
                        imageVector = PrivatiIcons.Group,
                        contentDescription = "Nuevo grupo",
                        tint = colors.primary,
                    )
                }
            }
        }

        if (contactos.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Nadie más en la sala",
                    style = PrivatiTypography.body,
                    color = colors.inkMuted,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 100.dp),
            ) {
                items(contactos, key = { it.id }) { participante ->
                    ParticipantRow(
                        participant = participante,
                        isCurrentUser = false,
                        canKick = false,
                        onClick = { onOpenContact(participante.id) },
                    )
                }
            }
        }
    }

    if (mostrarSheet) {
        ModalBottomSheet(
            onDismissRequest = { mostrarSheet = false },
            sheetState = sheetState,
            containerColor = colors.surface,
        ) {
            NuevoGrupoContent(
                contactos = contactos,
                onCrearGrupo = { nombre, memberIds ->
                    mostrarSheet = false
                    onCrearGrupo(nombre, memberIds)
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NuevoGrupoContent(
    contactos: List<Participant>,
    onCrearGrupo: (nombre: String, memberIds: List<String>) -> Unit,
) {
    val colors = PrivatiTheme.colors
    var nombre by remember { mutableStateOf("") }
    var seleccionados by remember { mutableStateOf(emptySet<String>()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PrivatiSpacing.screenHorizontal),
    ) {
        Text(
            text = "Nuevo grupo",
            style = PrivatiTypography.title2,
            color = colors.ink,
        )

        Spacer(Modifier.height(PrivatiSpacing.md))

        PrivatiTextField(
            value = nombre,
            onValueChange = { nombre = it },
            placeholder = "Nombre del grupo",
        )

        Spacer(Modifier.height(PrivatiSpacing.md))

        Text(
            text = "Miembros — selecciona al menos 2",
            style = PrivatiTypography.label,
            color = colors.inkMuted,
        )

        Spacer(Modifier.height(PrivatiSpacing.sm))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PrivatiSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(PrivatiSpacing.sm),
        ) {
            contactos.forEach { participante ->
                SelectableChip(
                    text = participante.name,
                    selected = participante.id in seleccionados,
                    onClick = {
                        seleccionados = if (participante.id in seleccionados)
                            seleccionados - participante.id
                        else
                            seleccionados + participante.id
                    },
                )
            }
        }

        Spacer(Modifier.height(PrivatiSpacing.lg))

        PrivatiButton(
            text = "Crear grupo",
            onClick = { onCrearGrupo(nombre.trim(), seleccionados.toList()) },
            enabled = nombre.isNotBlank() && seleccionados.size >= 2,
            modifier = Modifier.fillMaxWidth(),
        )

        // Espacio final + consumo de insets de barra de navegación para el ModalBottomSheet
        Spacer(
            Modifier
                .navigationBarsPadding()
                .height(PrivatiSpacing.xl),
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Pestaña Ajustes
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsPane(
    roomState: RoomState,
    esHost: Boolean,
    onBackToPanel: () -> Unit,
    onSalir: () -> Unit,
    onCerrarSala: () -> Unit,
) {
    val colors = PrivatiTheme.colors
    val usuarioLocal = roomState.participants.firstOrNull { it.id == roomState.localUserId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = PrivatiSpacing.screenHorizontal),
    ) {
        Spacer(Modifier.height(PrivatiSpacing.sm))

        Text(
            text = "Ajustes",
            style = PrivatiTypography.title2,
            color = colors.ink,
        )

        Spacer(Modifier.height(PrivatiSpacing.lg))

        // Avatar + nombre
        if (usuarioLocal != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PrivatiSpacing.md),
            ) {
                Avatar(
                    participantId = usuarioLocal.id,
                    name = usuarioLocal.name,
                    size = PrivatiSizes.avatarLg,
                )
                Column {
                    Text(
                        text = usuarioLocal.name,
                        style = PrivatiTypography.bodyStrong,
                        color = colors.ink,
                    )
                    Text(
                        text = if (esHost) "Host de la sala" else "Participante",
                        style = PrivatiTypography.bodySm,
                        color = colors.inkMuted,
                    )
                }
            }
        }

        Spacer(Modifier.height(PrivatiSpacing.lg))

        // Estado del cifrado
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PrivatiSpacing.sm),
        ) {
            Icon(
                imageVector = PrivatiIcons.Lock,
                contentDescription = null,
                tint = if (roomState.isEncrypted) colors.success else colors.inkMuted,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = if (roomState.isEncrypted) "Cifrado activo (ECDH+AES-GCM)" else "Sin cifrado",
                style = PrivatiTypography.bodySm,
                color = if (roomState.isEncrypted) colors.success else colors.inkMuted,
            )
        }

        Spacer(Modifier.weight(1f))

        // Botones de acción (encima de la barra flotante y de la barra de sistema)
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(PrivatiSpacing.sm),
        ) {
            if (esHost) {
                PrivatiButton(
                    text = "Volver al panel",
                    onClick = onBackToPanel,
                    variant = ButtonVariant.Secondary,
                )
                PrivatiButton(
                    text = "Cerrar sala",
                    onClick = onCerrarSala,
                    variant = ButtonVariant.Danger,
                )
            } else {
                PrivatiButton(
                    text = "Salir de la sala",
                    onClick = onSalir,
                    variant = ButtonVariant.Danger,
                )
            }
        }
    }
}

@Preview(name = "Claro", showBackground = true)
@Preview(name = "Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatsScreenPreview() {
    PrivatiTheme {
        ChatsContent(
            chats = listOf(
                ChatSummary(
                    chatId = "sala",
                    tipo = ChatTipo.SALA,
                    nombre = "Chat general",
                    ultimoMensaje = "¡Hola a todos!",
                    ultimaHora = System.currentTimeMillis(),
                    noLeidos = 2,
                ),
            ),
            roomState = RoomState(
                localUserId = "h1",
                roomCode = "K7Q2",
                participants = listOf(
                    Participant(id = "h1", name = "Carlos", isHost = true),
                    Participant(id = "c1", name = "Ana"),
                ),
                connectionStatus = ConnectionStatus.ONLINE,
            ),
            esHost = true,
            onOpenChat = {},
            onOpenContact = {},
            onCrearGrupo = { _, _ -> },
            onBackToPanel = {},
            onDisconnect = {},
            onSilenciar = {},
            onFijar = {},
            onEliminar = {},
        )
    }
}
