package com.redes.privati.ui.screens.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.redes.privati.data.MessageType
import com.redes.privati.ui.components.AudioBubble
import com.redes.privati.ui.components.DaySeparator
import com.redes.privati.ui.components.ImageBubble
import com.redes.privati.ui.components.MessageBubble
import com.redes.privati.ui.components.MessageInputBar
import com.redes.privati.ui.components.PrivatiTopBar
import com.redes.privati.ui.components.StatusPill
import com.redes.privati.ui.components.SystemMessage
import com.redes.privati.ui.components.formatearDia
import com.redes.privati.ui.icons.PrivatiIcons
import com.redes.privati.ui.theme.PrivatiSpacing
import com.redes.privati.ui.theme.PrivatiTheme
import com.redes.privati.ui.theme.PrivatiTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla de conversación activa para el chat identificado por [chatId].
 * Muestra el hilo de mensajes con separadores de día, avisos de sistema y la barra de entrada.
 *
 * @param viewModel ViewModel que provee mensajes, estado de sala y entrada de texto.
 * @param chatId Identificador del chat a mostrar (por defecto "sala").
 * @param chatNombre Nombre visible del chat para la barra superior.
 * @param onBack Navegación de retroceso (volver a ChatsScreen).
 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    chatId: String = "sala",
    chatNombre: String = "Chat general",
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val colors = PrivatiTheme.colors
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val listState = rememberLazyListState()

    val messages by viewModel.messages.collectAsState()
    val roomState by viewModel.roomState.collectAsState()
    val inputText by viewModel.inputText.collectAsState()

    // Estado de grabación y reproducción de audio
    val estaGrabando by viewModel.estaGrabando.collectAsState()
    val duracionGrabacionMs by viewModel.duracionGrabacionMs.collectAsState()
    val idMensajeReproduciendo by viewModel.idMensajeReproduciendo.collectAsState()
    val progresoReproduccion by viewModel.progresoReproduccion.collectAsState()

    // Duración formateada «0:07» que se muestra en el indicador de grabación
    val duracionFormateada = remember(duracionGrabacionMs) {
        val seg = (duracionGrabacionMs / 1000).toInt()
        "%d:%02d".format(seg / 60, seg % 60)
    }

    // Launcher para solicitar el permiso RECORD_AUDIO la primera vez que se usa el micrófono.
    // Si se concede, inicia la grabación de inmediato.
    val permisoAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (concedido) viewModel.iniciarGrabacion(context)
    }

    // Liberar grabación y reproducción al salir de la pantalla (spec: "se para al salir")
    DisposableEffect(Unit) {
        onDispose {
            viewModel.cancelarGrabacion()
            viewModel.detenerReproduccion()
        }
    }

    // Launcher del selector de imágenes del sistema (sin permisos de almacenamiento)
    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let { viewModel.enviarImagen(it) }
    }

    // Ruta absoluta del fichero temporal de la cámara.
    // rememberSaveable sobrevive a la recreación de Activity (Android puede matar el
    // proceso mientras la app de cámara está en primer plano y recrearlo al volver).
    var tempFotoRuta by rememberSaveable { mutableStateOf("") }

    // Contrato extendido que añade FLAG_GRANT_WRITE_URI_PERMISSION: sin este flag
    // la app de cámara no puede escribir en la URI del FileProvider.
    val contratoCamara = remember {
        object : ActivityResultContracts.TakePicture() {
            override fun createIntent(context: Context, input: Uri): Intent =
                super.createIntent(context, input)
                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }

    // Launcher de la cámara: recibe true si el usuario capturó la foto, false si canceló.
    // NO borramos el fichero aquí: enviarImagen() es asíncrono y el borrado debe ocurrir
    // después de que la corrutina haya leído el bitmap (ver ChatRepository.enviarImagen).
    val fotoLauncher = rememberLauncherForActivityResult(contratoCamara) { capturada ->
        if (capturada && tempFotoRuta.isNotEmpty()) {
            val archivo = java.io.File(tempFotoRuta)
            if (archivo.exists() && archivo.length() > 0) {
                viewModel.enviarImagen(Uri.fromFile(archivo))
            }
        }
        tempFotoRuta = ""
    }

    // Bytes de la imagen seleccionada para la vista a pantalla completa; null = oculto
    var imagenPantallaCompleta by remember { mutableStateOf<ByteArray?>(null) }

    // Título: usa el nombre del chat pasado por parámetro
    val tituloChat = chatNombre.ifBlank {
        // Fallback: nombre del otro participante (compatibilidad hacia atrás)
        roomState.participants.firstOrNull { it.id != roomState.localUserId }?.name ?: "Chat"
    }

    // Subtítulo según tipo de chat (Fase 10: solo sala)
    val subtituloChat = when {
        chatId == "sala" -> "${roomState.participants.size} en la sala"
        chatId.startsWith("dm:") ->
            if (roomState.connectionStatus == com.redes.privati.data.ConnectionStatus.ONLINE)
                "En línea" else "Desconectado"
        chatId.startsWith("grp:") ->
            "${roomState.participants.size} miembros"
        else -> "${roomState.participants.size} en la sala"
    }

    // Auto-scroll al último mensaje cuando llega uno nuevo
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            PrivatiTopBar(
                title = tituloChat,
                subtitle = subtituloChat,
                onNavigateBack = onBack,
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        StatusPill(status = roomState.connectionStatus)
                        if (roomState.isEncrypted) {
                            Icon(
                                imageVector = PrivatiIcons.Lock,
                                contentDescription = "Cifrado activo",
                                tint = colors.success,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            MessageInputBar(
                text = inputText,
                onTextChange = viewModel::onInputChange,
                onSend = { viewModel.enviarMensaje() },
                onAttachImage = {
                    imageLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onTakePhoto = {
                    try {
                        val archivo = java.io.File.createTempFile(
                            "corro_foto_", ".jpg", context.cacheDir,
                        )
                        tempFotoRuta = archivo.absolutePath
                        val uri = FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", archivo,
                        )
                        fotoLauncher.launch(uri)
                    } catch (_: Exception) {
                        tempFotoRuta = ""
                    }
                },
                onMicPress = {
                    val tienePermiso = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (tienePermiso) {
                        viewModel.iniciarGrabacion(context)
                    } else {
                        permisoAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onMicRelease = { viewModel.detenerGrabacion() },
                onMicCancel = { viewModel.cancelarGrabacion() },
                isRecording = estaGrabando,
                recordingDuration = duracionFormateada,
            )
        },
        containerColor = colors.surfaceMuted,
    ) { innerPadding ->
        if (messages.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Sé el primero en escribir algo",
                    style = PrivatiTypography.body,
                    color = colors.inkMuted,
                )
            }
        } else {
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
                itemsIndexed(messages, key = { _, msg -> msg.id }) { index, msg ->
                    // Separador de día: mostrar si es el primer mensaje o cambió el día
                    val msgAnterior = if (index > 0) messages[index - 1] else null
                    val cambiodia = msgAnterior == null
                        || formatearDia(msgAnterior.timestamp) != formatearDia(msg.timestamp)
                    if (cambiodia) {
                        DaySeparator(timestamp = msg.timestamp)
                    }

                    when (msg.type) {
                        MessageType.SYSTEM -> {
                            SystemMessage(text = msg.text)
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
                                imageModel = msg.imageBytes,
                                sizeLabel = msg.imageSizeLabel,
                                time = fmt.format(Date(msg.timestamp)),
                                onTap = { imagenPantallaCompleta = msg.imageBytes },
                            )
                        }
                        MessageType.AUDIO -> {
                            val estaReproduciendo = idMensajeReproduciendo == msg.id
                            AudioBubble(
                                isOwn = msg.isOwn,
                                isPlaying = estaReproduciendo,
                                onPlayPause = {
                                    msg.audioBytes?.let { bytes ->
                                        viewModel.toggleReproduccion(msg.id, bytes)
                                    }
                                },
                                durationMs = msg.audioDurationMs,
                                progressFraction = if (estaReproduciendo) progresoReproduccion else 0f,
                                waveform = msg.audioWaveform,
                                time = fmt.format(Date(msg.timestamp)),
                            )
                        }
                    }
                }
            }
        }
    }

    // Visor de imagen a pantalla completa: se abre al pulsar cualquier burbuja de imagen
    imagenPantallaCompleta?.let { bytes ->
        Dialog(
            onDismissRequest = { imagenPantallaCompleta = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { imagenPantallaCompleta = null },
                contentAlignment = Alignment.Center,
            ) {
                val bitmap = remember(bytes) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Imagen a pantalla completa",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
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
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Vista previa: ChatScreen", style = PrivatiTypography.heading)
        }
    }
}
