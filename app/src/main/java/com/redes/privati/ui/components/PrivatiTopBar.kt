package com.redes.privati.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.redes.privati.data.ConnectionStatus
import com.redes.privati.ui.icons.PrivatiIcons
import com.redes.privati.ui.theme.PrivatiTheme
import com.redes.privati.ui.theme.PrivatiTypography

/**
 * Barra superior del sistema de diseño Privati.
 * Soporta título, subtítulo, botón de navegación de retroceso y acciones adicionales.
 *
 * @param modifier Modificador externo.
 * @param title Título principal de la pantalla.
 * @param subtitle Subtítulo opcional (p. ej. «5 en la sala»).
 * @param onNavigateBack Callback del botón de retroceso; si es nulo, no se muestra.
 * @param actions Acciones adicionales en el extremo derecho (iconos).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivatiTopBar(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    onNavigateBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = PrivatiTheme.colors

    TopAppBar(
        modifier = modifier,
        title = {
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = title,
                    style = PrivatiTypography.heading,
                    color = colors.ink,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = PrivatiTypography.caption,
                        color = colors.inkMuted,
                    )
                }
            }
        },
        navigationIcon = {
            if (onNavigateBack != null) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = PrivatiIcons.Back,
                        contentDescription = "Volver",
                        tint = colors.ink,
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colors.surface,
            titleContentColor = colors.ink,
        ),
    )
}

@Preview(name = "Claro", showBackground = true)
@Preview(name = "Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PrivatiTopBarPreview() {
    PrivatiTheme {
        Column {
            PrivatiTopBar(title = "Sala de prueba", subtitle = "3 en la sala", onNavigateBack = {})
            PrivatiTopBar(title = "Panel del host")
        }
    }
}
