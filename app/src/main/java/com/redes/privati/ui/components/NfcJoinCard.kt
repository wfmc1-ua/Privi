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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.redes.privati.ui.theme.PrivatiShapes
import com.redes.privati.ui.theme.PrivatiSpacing
import com.redes.privati.ui.theme.PrivatiTheme
import com.redes.privati.ui.theme.PrivatiTypography

/**
 * Tarjeta mostrada al cliente en la pantalla de unirse.
 * Siempre visible; muestra instrucciones NFC si [nfcActivo], o aviso de no disponible si no.
 *
 * @param nfcActivo Si verdadero, muestra la animación y el estado "esperando…".
 */
@Composable
fun NfcJoinCard(
    nfcActivo: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = PrivatiTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PrivatiShapes.card)
            .background(colors.primarySubtle)
            .padding(PrivatiSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PrivatiSpacing.sm),
    ) {
        NfcWaves(isActive = nfcActivo, size = 120.dp)

        Text(
            text = if (nfcActivo) "Acerca tu móvil al del anfitrión"
                   else "NFC no disponible en este dispositivo",
            style = PrivatiTypography.bodyStrong,
            color = colors.ink,
            textAlign = TextAlign.Center,
        )

        Text(
            text = if (nfcActivo) "Espalda con espalda. Recibirás la sala por NFC y entrarás solo, sin escribir nada."
                   else "Usa el código de sala o la IP del anfitrión para conectarte.",
            style = PrivatiTypography.caption,
            color = colors.inkSecondary,
            textAlign = TextAlign.Center,
        )

        if (nfcActivo) {
            Row(
                modifier = Modifier
                    .clip(PrivatiShapes.pill)
                    .background(colors.primaryTint)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PrivatiSpacing.xs),
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(PrivatiShapes.pill)
                        .background(colors.primary),
                )
                Text(
                    text = "NFC activado · esperando…",
                    style = PrivatiTypography.caption,
                    color = colors.primaryInk,
                )
            }
        }
    }
}

@Preview(name = "Claro", showBackground = true)
@Preview(name = "Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NfcJoinCardPreview() {
    PrivatiTheme {
        NfcJoinCard(modifier = Modifier.padding(PrivatiSpacing.screenHorizontal))
    }
}
