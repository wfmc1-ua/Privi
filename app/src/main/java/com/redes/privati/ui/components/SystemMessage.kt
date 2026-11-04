package com.redes.privati.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.redes.privati.ui.theme.PrivatiSpacing
import com.redes.privati.ui.theme.PrivatiTheme
import com.redes.privati.ui.theme.PrivatiTypography

/**
 * Aviso de sistema centrado en el hilo de conversación.
 * Se usa para eventos como «Ana se ha unido», «Pedro ha salido», etc.
 * Solo se muestra en el chat sala (spec §4).
 *
 * @param modifier Modificador externo.
 * @param text Texto del aviso de sistema.
 * @param time Hora opcional en formato HH:mm; se muestra a continuación del texto si se provee.
 */
@Composable
fun SystemMessage(
    modifier: Modifier = Modifier,
    text: String,
    time: String? = null,
) {
    val colors = PrivatiTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = PrivatiSpacing.xs),
        horizontalArrangement = Arrangement.Center,
    ) {
        val displayText = if (time != null) "$text · $time" else text
        Text(
            text = displayText,
            style = PrivatiTypography.caption,
            color = colors.inkMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(name = "Claro", showBackground = true)
@Preview(name = "Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SystemMessagePreview() {
    PrivatiTheme {
        Column(
            modifier = Modifier.padding(PrivatiSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SystemMessage(text = "Ana se ha unido", time = "16:20")
            SystemMessage(text = "Pedro ha salido")
            SystemMessage(text = "Wesley se unió por NFC · 16:20")
        }
    }
}
