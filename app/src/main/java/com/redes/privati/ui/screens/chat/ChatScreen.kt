package com.redes.privati.ui.screens.chat

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.redes.privati.data.ConnectionStatus
import com.redes.privati.data.Message
import com.redes.privati.data.MessageType
import com.redes.privati.ui.components.AudioBubble
import com.redes.privati.ui.components.ImageBubble
import com.redes.privati.ui.components.MessageBubble
import com.redes.privati.ui.components.MessageInputBar
import com.redes.privati.ui.components.PrivatiTopBar
import com.redes.privati.ui.components.StatusPill
import com.redes.privati.ui.icons.PrivatiIcons
import com.redes.privati.ui.theme.PrivatiSpacing
import com.redes.privati.ui.theme.PrivatiTheme
import com.redes.privati.ui.theme.PrivatiTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla de conversación activa.
 * Muestra el hilo de mensajes (texto, imagen, audio y avisos de sistema)
 * con la barra de entrada en la parte inferior.
 *
 * @param onBack Navegación de retroceso.
 */
@Composable
fun ChatScreen(
    onBack: () -> Unit = {},
) {
    val colors = PrivatiTheme.colors
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }

    // Datos de prueba para la Fase 0
    val messages = remember {
        listOf(
            Message(senderId = "c1", senderName = "Ana", text = "Hola, ¿me escuchas?", timestamp = System.currentTimeMillis() - 120_000, isOwn = false),
            Message(senderId = "me", senderName = "Yo", text = "Sí, perfectamente. ¿Todos conectados?", timestamp = System.currentTimeMillis() - 110_000, isOwn = true),
            Message(senderId = "c2", senderName = "Pedro", text = "Yo también estoy aquí.", timestamp = System.currentTimeMillis() - 100_000, isOwn = false),
            Message(senderId = "", senderName = "", type = MessageType.SYSTEM, text = "María se ha unido", timestamp = System.currentTimeMillis() - 90_000, isOwn = false),
            Message(senderId = "me", senderName = "Yo", type = MessageType.AUDIO, audioDurationMs = 7400, timestamp = System.currentTimeMillis() - 80_000, isOwn = true),
            Message(senderId = "c1", senderName = "Ana", type = MessageType.IMAGE, imageSizeLabel = "212 KB · por red local", timestamp = System.currentTimeMillis() - 60_000, isOwn = false),
            Message(senderId = "me", senderName = "Yo", text = "Genial, todo funciona.", timestamp = System.currentTimeMillis() - 30_000, isOwn = true),
        )
    }

    Scaffold(
        topBar = {
            PrivatiTopBar(
                title = "Sala de prueba",
                subtitle = "3 en la sala",
                onNavigateBack = onBack,
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        StatusPill(status = ConnectionStatus.ONLINE)
                        Icon(
                            imageVector = PrivatiIcons.Lock,
                            contentDescription = "Cifrado",
                            tint = colors.success,
                        )
                    }
                },
            )
        },
        bottomBar = {
            MessageInputBar(
                text = inputText,
                onTextChange = { inputText = it },
                onSend = { inputText = "" },
            )
        },
        containerColor = colors.surfaceMuted,
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                horizontal = PrivatiSpacing.screenHorizontal,
                vertical = PrivatiSpacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(PrivatiSpacing.bubbleGap),
        ) {
            items(messages, key = { it.id }) { msg ->
                when (msg.type) {
                    MessageType.SYSTEM -> {
                        // Aviso de sistema centrado
                        Column(
                            modifier = Modifier.fillParentMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = msg.text,
                                style = PrivatiTypography.caption,
                                color = colors.inkMuted,
                            )
                        }
                    }
                    MessageType.TEXT -> {
                        MessageBubble(
                            text = msg.text,
                            time = fmt.format(Date(msg.timestamp)),
                            isOwn = msg.isOwn,
                            senderName = if (!msg.isOwn) msg.senderName else null,
                            senderAvatarId = if (!msg.isOwn) msg.senderId else null,
                        )
                    }
                    MessageType.IMAGE -> {
                        ImageBubble(
                            isOwn = msg.isOwn,
                            sizeLabel = msg.imageSizeLabel,
                            time = fmt.format(Date(msg.timestamp)),
                        )
                    }
                    MessageType.AUDIO -> {
                        AudioBubble(
                            isOwn = msg.isOwn,
                            durationMs = msg.audioDurationMs,
                            time = fmt.format(Date(msg.timestamp)),
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Claro", showBackground = true)
@Preview(name = "Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatScreenPreview() {
    PrivatiTheme {
        ChatScreen()
    }
}
