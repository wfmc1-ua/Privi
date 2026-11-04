package com.redes.privati.ui.components

import android.content.res.Configuration
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.redes.privati.ui.theme.PrivatiShapes
import com.redes.privati.ui.theme.PrivatiSpacing
import com.redes.privati.ui.theme.PrivatiTheme
import com.redes.privati.ui.theme.PrivatiTypography

/**
 * Burbuja de imagen en el hilo de conversación.
 * La miniatura se carga con Coil desde un [ByteArray] o una URI.
 * Al tocarla se invoca [onTap] para abrir la vista a pantalla completa.
 *
 * @param modifier Modificador externo.
 * @param isOwn Si es verdadero la burbuja va a la derecha.
 * @param imageModel Modelo para Coil: [ByteArray], [android.net.Uri] o [String].
 * @param sizeLabel Etiqueta de tamaño, p. ej. «212 KB · por red local».
 * @param time Hora de envío.
 * @param onTap Callback al pulsar la imagen (abrir pantalla completa).
 */
@Composable
fun ImageBubble(
    modifier: Modifier = Modifier,
    isOwn: Boolean,
    imageModel: Any? = null,
    sizeLabel: String? = null,
    time: String = "",
    onTap: () -> Unit = {},
) {
    val colors = PrivatiTheme.colors
    val bubbleColor = if (isOwn) colors.bubbleOut else colors.surface
    val timeColor = if (isOwn) Color(0xFFE4E0FF) else colors.inkMuted
    val shape = if (isOwn) PrivatiShapes.bubbleOutgoing else PrivatiShapes.bubbleIncoming

    // Decodificar ByteArray → ImageBitmap de forma perezosa; para otros tipos (URI, String)
    // se delega en AsyncImage de Coil.
    val imageBitmap = remember(imageModel) {
        (imageModel as? ByteArray)?.let { bytes ->
            try {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (_: Exception) { null }
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 240.dp)
                .clip(shape)
                .background(bubbleColor)
                .clickable(onClick = onTap),
        ) {
            Column {
                when {
                    imageBitmap != null -> {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = "Imagen recibida",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .size(200.dp),
                        )
                    }
                    imageModel != null -> {
                        // URI o String: delegar en Coil
                        AsyncImage(
                            model = imageModel,
                            contentDescription = "Imagen recibida",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .size(200.dp),
                        )
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .size(200.dp)
                                .background(colors.surfaceRaised),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Imagen",
                                style = PrivatiTypography.caption,
                                color = colors.inkMuted,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = sizeLabel ?: "",
                        style = PrivatiTypography.micro,
                        color = timeColor,
                    )
                    Text(
                        text = time,
                        style = PrivatiTypography.micro,
                        color = timeColor,
                    )
                }
            }
        }
    }
}

@Preview(name = "Claro", showBackground = true)
@Preview(name = "Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ImageBubblePreview() {
    PrivatiTheme {
        Column(
            modifier = Modifier
                .background(PrivatiTheme.colors.surfaceMuted)
                .padding(PrivatiSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PrivatiSpacing.bubbleGap),
        ) {
            ImageBubble(isOwn = false, sizeLabel = "212 KB · por red local", time = "14:32")
            ImageBubble(isOwn = true, sizeLabel = "340 KB · por red local", time = "14:34")
        }
    }
}
