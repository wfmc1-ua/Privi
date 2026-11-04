package com.redes.privati.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.redes.privati.ui.theme.PrivatiShapes
import com.redes.privati.ui.theme.PrivatiSpacing
import com.redes.privati.ui.theme.PrivatiTheme
import com.redes.privati.ui.theme.PrivatiTypography

/**
 * Pestañas en forma de píldora con contador de no leídos.
 * La pestaña seleccionada se rellena con el color primary; las demás son translúcidas.
 *
 * @param modifier Modificador externo.
 * @param tabs Lista de pares (etiqueta, contadorNoLeídos). El contador se oculta si es 0.
 * @param selectedIndex Índice de la pestaña actualmente seleccionada.
 * @param onSelect Callback con el índice pulsado.
 */
@Composable
fun PillTabs(
    modifier: Modifier = Modifier,
    tabs: List<Pair<String, Int>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val colors = PrivatiTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceMuted)
            .padding(horizontal = PrivatiSpacing.screenHorizontal, vertical = PrivatiSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(PrivatiSpacing.sm),
    ) {
        tabs.forEachIndexed { index, (label, count) ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(PrivatiShapes.pill)
                    .background(if (selected) colors.primary else colors.primaryTint)
                    .clickable { onSelect(index) }
                    .padding(horizontal = PrivatiSpacing.md, vertical = PrivatiSpacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = label,
                        style = PrivatiTypography.label,
                        color = if (selected) colors.surface else colors.primaryInk,
                    )
                    if (count > 0) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(
                                    if (selected) colors.surface.copy(alpha = 0.3f) else colors.primary,
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = count.coerceAtMost(99).toString(),
                                style = PrivatiTypography.micro,
                                color = if (selected) colors.surface else colors.surface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Claro", showBackground = true)
@Preview(name = "Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PillTabsPreview() {
    PrivatiTheme {
        var selected by remember { mutableIntStateOf(1) }
        PillTabs(
            tabs = listOf("Personal" to 2, "Todos" to 5, "Grupos" to 0),
            selectedIndex = selected,
            onSelect = { selected = it },
        )
    }
}
