package com.redes.privati.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.redes.privati.ui.icons.PrivatiIcons
import com.redes.privati.ui.theme.PrivatiShapes
import com.redes.privati.ui.theme.PrivatiSpacing
import com.redes.privati.ui.theme.PrivatiTheme
import com.redes.privati.ui.theme.PrivatiTypography

/**
 * Tarjeta violeta de invitación NFC mostrada en el panel del host.
 * Muestra el código de sala y la IP:puerto para que el cliente pueda conectarse por NFC o manualmente.
 *
 * @param roomCode    Código de sala de 4 caracteres.
 * @param hostIp      Dirección IP del host en la red local.
 * @param port        Puerto TCP del servidor.
 * @param nfcActivo   Si verdadero, muestra instrucciones NFC; si falso, solo el código e IP.
 * @param modifier    Modificador externo.
 */
@Composable
fun NfcInviteCard(
    roomCode: String,
    hostIp: String,
    port: Int,
    nfcActivo: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = PrivatiTheme.colors
    val white = Color.White
    val whiteAlpha = white.copy(alpha = 0.70f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PrivatiShapes.card)
            .background(colors.primary)
            .padding(PrivatiSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PrivatiSpacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(PrivatiShapes.pill)
                .background(white.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = PrivatiIcons.Nfc,
                contentDescription = null,
                tint = white,
                modifier = Modifier.size(28.dp),
            )
        }

        if (nfcActivo) {
            Text(
                text = "Junta tu móvil por detrás",
                style = PrivatiTypography.bodyStrong,
                color = white,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "El cliente recibirá los datos de la sala automáticamente.",
                style = PrivatiTypography.caption,
                color = whiteAlpha,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                text = "NFC no disponible",
                style = PrivatiTypography.bodyStrong,
                color = white,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Comparte el código o la IP manualmente.",
                style = PrivatiTypography.caption,
                color = whiteAlpha,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(PrivatiSpacing.xs))

        Box(
            modifier = Modifier
                .clip(PrivatiShapes.pill)
                .background(white.copy(alpha = 0.18f))
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Text(
                text = roomCode.map { it.toString() }.joinToString("  "),
                style = PrivatiTypography.code,
                color = white,
                textAlign = TextAlign.Center,
            )
        }

        Text(
            text = "$hostIp:$port",
            style = PrivatiTypography.caption,
            color = whiteAlpha,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(name = "Claro", showBackground = true)
@Preview(name = "Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NfcInviteCardPreview() {
    PrivatiTheme {
        NfcInviteCard(
            roomCode = "X7KP",
            hostIp = "192.168.1.45",
            port = 9000,
            nfcActivo = true,
            modifier = Modifier.padding(PrivatiSpacing.screenHorizontal),
        )
    }
}
