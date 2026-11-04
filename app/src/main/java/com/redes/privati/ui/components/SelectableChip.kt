package com.redes.privati.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.redes.privati.ui.icons.PrivatiIcons
import com.redes.privati.ui.theme.PrivatiShapes
import com.redes.privati.ui.theme.PrivatiSpacing
import com.redes.privati.ui.theme.PrivatiTheme
import com.redes.privati.ui.theme.PrivatiTypography

/**
 * Chip seleccionable para elegir miembros al crear un grupo.
 * Fondo tono primario cuando está seleccionado; fondo neutro cuando no lo está.
 *
 * @param modifier Modificador externo.
 * @param text Etiqueta del chip (nombre del participante).
 * @param selected Si el chip está en estado seleccionado.
 * @param onClick Callback al pulsar el chip para alternar la selección.
 */
@Composable
fun SelectableChip(
    modifier: Modifier = Modifier,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = PrivatiTheme.colors
    val bgColor = if (selected) colors.primaryTint else colors.surfaceRaised
    val textColor = if (selected) colors.primaryInk else colors.inkSecondary

    Row(
        modifier = modifier
            .clip(PrivatiShapes.pill)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = PrivatiSpacing.md, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PrivatiSpacing.xs),
    ) {
        if (selected) {
            Icon(
                imageVector = PrivatiIcons.Check,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = text,
            style = PrivatiTypography.label,
            color = textColor,
        )
    }
}

@Preview(name = "Claro", showBackground = true)
@Preview(name = "Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SelectableChipPreview() {
    PrivatiTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            SelectableChip(text = "Ana", selected = false, onClick = {})
            SelectableChip(text = "Carlos", selected = true, onClick = {})
            SelectableChip(text = "Pedro", selected = false, onClick = {})
        }
    }
}
