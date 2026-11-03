package com.redes.privati.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.redes.privati.ui.theme.AvatarPalettesDark
import com.redes.privati.ui.theme.AvatarPalettesLight
import com.redes.privati.ui.theme.PrivatiShapes
import com.redes.privati.ui.theme.PrivatiSizes
import com.redes.privati.ui.theme.PrivatiTheme
import com.redes.privati.ui.theme.PrivatiTypography
import kotlin.math.abs

/**
 * Avatar circular con iniciales del participante sobre un fondo de color.
 * El color se determina de forma determinista a partir del [participantId],
 * de modo que el mismo participante siempre tiene el mismo color en todos los
 * dispositivos de la sala.
 *
 * @param modifier Modificador externo.
 * @param participantId Identificador único del participante (UUID).
 * @param name Nombre completo; se extraen las iniciales (máx. 2 caracteres).
 * @param size Diámetro del avatar; usa [PrivatiSizes.avatarLg/Md/Sm] por convención.
 */
@Composable
fun Avatar(
    modifier: Modifier = Modifier,
    participantId: String,
    name: String,
    size: Dp = PrivatiSizes.avatarLg,
) {
    val colors = PrivatiTheme.colors
    val palettes = if (colors.isDark) AvatarPalettesDark else AvatarPalettesLight
    val index = abs(participantId.hashCode()) % palettes.size
    val (bgColor, inkColor) = palettes[index]

    val initials = name
        .trim()
        .split(" ")
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifEmpty { "?" }

    Box(
        modifier = modifier
            .size(size)
            .clip(PrivatiShapes.pill)
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = PrivatiTypography.label,
            color = inkColor,
        )
    }
}

@Preview(name = "Claro", showBackground = true)
@Preview(name = "Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AvatarPreview() {
    PrivatiTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Avatar(participantId = "a1b2", name = "Ana García", size = PrivatiSizes.avatarLg)
            Avatar(participantId = "c3d4", name = "Pedro", size = PrivatiSizes.avatarMd)
            Avatar(participantId = "e5f6", name = "María José", size = PrivatiSizes.avatarSm)
        }
    }
}
