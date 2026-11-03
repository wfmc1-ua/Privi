package com.redes.privati.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.redes.privati.ui.icons.PrivatiIcons
import com.redes.privati.ui.theme.PrivatiShapes
import com.redes.privati.ui.theme.PrivatiSizes
import com.redes.privati.ui.theme.PrivatiTheme
import com.redes.privati.ui.theme.PrivatiTypography

/** Variantes de botón del sistema de diseño Privati. */
enum class ButtonVariant {
    /** Fondo primary con sombra violeta. Máximo uno por pantalla. */
    Primary,

    /** Fondo primary-tint, texto primary-ink. Acción secundaria. */
    Secondary,

    /** Fondo danger-tint, texto danger-ink. Acciones destructivas. */
    Danger,

    /** Icono circular sobre fondo primary-wash. Sin texto. */
    Icon,
}

/**
 * Botón del sistema de diseño Privati.
 *
 * @param modifier Modificador externo.
 * @param text Etiqueta del botón (ignorada en variante [ButtonVariant.Icon]).
 * @param onClick Callback al pulsar.
 * @param variant Variante visual; por defecto [ButtonVariant.Primary].
 * @param enabled Si el botón responde a la interacción.
 * @param icon Icono que se muestra en la variante [ButtonVariant.Icon] o como
 *             inicio de las otras variantes cuando no es nulo.
 */
@Composable
fun PrivatiButton(
    modifier: Modifier = Modifier,
    text: String = "",
    onClick: () -> Unit,
    variant: ButtonVariant = ButtonVariant.Primary,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = PrivatiTheme.colors

    when (variant) {
        ButtonVariant.Icon -> {
            Box(
                modifier = modifier
                    .size(PrivatiSizes.iconButton),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(
                    onClick = onClick,
                    enabled = enabled,
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = text,
                            tint = if (enabled) colors.primary else colors.inkMuted,
                        )
                    }
                }
            }
        }

        else -> {
            val (containerColor, contentColor) = when (variant) {
                ButtonVariant.Primary -> colors.primary to Color.White
                ButtonVariant.Secondary -> colors.primaryTint to colors.primaryInk
                ButtonVariant.Danger -> colors.dangerTint to colors.dangerInk
                ButtonVariant.Icon -> colors.primaryWash to colors.primary
            }

            val shadowModifier = if (variant == ButtonVariant.Primary && enabled) {
                Modifier.shadow(
                    elevation = 10.dp,
                    shape = PrivatiShapes.pill,
                    ambientColor = colors.primary.copy(alpha = 0.35f),
                    spotColor = colors.primary.copy(alpha = 0.35f),
                )
            } else Modifier

            Button(
                onClick = onClick,
                enabled = enabled,
                shape = PrivatiShapes.pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = containerColor,
                    contentColor = contentColor,
                    disabledContainerColor = colors.surfaceRaised,
                    disabledContentColor = colors.inkMuted,
                ),
                modifier = modifier
                    .then(shadowModifier)
                    .fillMaxWidth()
                    .height(PrivatiSizes.ctaHeight),
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = text,
                    style = PrivatiTypography.bodyStrong,
                )
            }
        }
    }
}

@Preview(name = "Claro", showBackground = true)
@Preview(name = "Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PrivatiButtonPreview() {
    PrivatiTheme {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.then(Modifier),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            PrivatiButton(text = "Crear sala", onClick = {}, variant = ButtonVariant.Primary)
            PrivatiButton(text = "Unirme", onClick = {}, variant = ButtonVariant.Secondary)
            PrivatiButton(text = "Cerrar sala", onClick = {}, variant = ButtonVariant.Danger)
            PrivatiButton(onClick = {}, variant = ButtonVariant.Icon, icon = PrivatiIcons.Send, text = "Enviar")
        }
    }
}
