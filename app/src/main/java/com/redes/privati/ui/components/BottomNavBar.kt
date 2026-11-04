package com.redes.privati.ui.components

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.redes.privati.ui.icons.PrivatiIcons
import com.redes.privati.ui.theme.PrivatiShapes
import com.redes.privati.ui.theme.PrivatiSpacing
import com.redes.privati.ui.theme.PrivatiTheme
import com.redes.privati.ui.theme.PrivatiTypography

/** Pestañas de la barra de navegación inferior. */
enum class BottomNavPane {
    CONTACTS, CHATS, SETTINGS
}

/**
 * Barra de navegación inferior flotante en forma de píldora primary.
 * Contiene tres elementos: Contactos, Chats y Ajustes.
 *
 * @param modifier Modificador externo (usar para posicionamiento flotante).
 * @param selected Pestaña actualmente activa.
 * @param onSelect Callback con la nueva pestaña seleccionada.
 */
@Composable
fun BottomNavBar(
    modifier: Modifier = Modifier,
    selected: BottomNavPane,
    onSelect: (BottomNavPane) -> Unit,
) {
    val colors = PrivatiTheme.colors

    Row(
        modifier = modifier
            .clip(PrivatiShapes.pill)
            .background(colors.primary)
            .padding(horizontal = PrivatiSpacing.sm, vertical = PrivatiSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavItem(
            label = "Contactos",
            icon = { tint ->
                Icon(
                    imageVector = PrivatiIcons.Person,
                    contentDescription = "Contactos",
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
            },
            selected = selected == BottomNavPane.CONTACTS,
            onClick = { onSelect(BottomNavPane.CONTACTS) },
        )
        NavItem(
            label = "Chats",
            icon = { tint ->
                Icon(
                    imageVector = PrivatiIcons.Group,
                    contentDescription = "Chats",
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
            },
            selected = selected == BottomNavPane.CHATS,
            onClick = { onSelect(BottomNavPane.CHATS) },
        )
        NavItem(
            label = "Ajustes",
            icon = { tint ->
                Icon(
                    imageVector = PrivatiIcons.Settings,
                    contentDescription = "Ajustes",
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
            },
            selected = selected == BottomNavPane.SETTINGS,
            onClick = { onSelect(BottomNavPane.SETTINGS) },
        )
    }
}

@Composable
private fun NavItem(
    label: String,
    icon: @Composable (tint: Color) -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = PrivatiTheme.colors
    val tint by animateColorAsState(
        targetValue = if (selected) colors.surface else colors.surface.copy(alpha = 0.6f),
        label = "nav-tint",
    )

    Column(
        modifier = Modifier
            .clip(PrivatiShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = PrivatiSpacing.md, vertical = PrivatiSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        icon(tint)
        Text(
            text = label,
            style = PrivatiTypography.micro,
            color = tint,
        )
    }
}

@Preview(name = "Claro", showBackground = true)
@Preview(name = "Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BottomNavBarPreview() {
    PrivatiTheme {
        var selected by remember { mutableStateOf(BottomNavPane.CHATS) }
        BottomNavBar(selected = selected, onSelect = { selected = it })
    }
}
