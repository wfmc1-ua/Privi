package com.redes.privati.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Punto de acceso estático a los tokens del tema Privati desde cualquier composable.
 * Usar dentro de la jerarquía de [PrivatiTheme].
 *
 * Ejemplo: `val colors = PrivatiTheme.colors`
 */
object PrivatiTheme {
    /** Colores del tema actual (claro u oscuro). */
    val colors: PrivatiColors
        @Composable @ReadOnlyComposable
        get() = LocalPrivatiColors.current

    /** Estilos tipográficos de Privati. */
    val typography: PrivatiTypography
        get() = PrivatiTypography

    /** Formas del sistema de diseño. */
    val shapes: PrivatiShapes
        get() = PrivatiShapes

    /** Espaciados del sistema de diseño. */
    val spacing: PrivatiSpacing
        get() = PrivatiSpacing

    /** Tamaños fijos del sistema de diseño. */
    val sizes: PrivatiSizes
        get() = PrivatiSizes
}

/**
 * Tema de Privati. Envuelve el contenido con los colores, tipografía y formas
 * del sistema de diseño y proporciona [MaterialTheme] para compatibilidad con M3.
 *
 * @param darkTheme Si es verdadero aplica el esquema oscuro.
 * @param content Contenido de la aplicación.
 */
@Composable
fun PrivatiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val privatiColors = if (darkTheme) PrivatiColorsDark else PrivatiColorsLight

    // Esquema Material3 mapeado desde los tokens Privati para compatibilidad
    val materialColorScheme = if (darkTheme) {
        darkColorScheme(
            primary = privatiColors.primary,
            surface = privatiColors.surface,
            background = privatiColors.surfaceMuted,
            error = privatiColors.danger,
            onPrimary = androidx.compose.ui.graphics.Color.White,
            onSurface = privatiColors.ink,
            onBackground = privatiColors.ink,
        )
    } else {
        lightColorScheme(
            primary = privatiColors.primary,
            surface = privatiColors.surface,
            background = privatiColors.surfaceMuted,
            error = privatiColors.danger,
            onPrimary = androidx.compose.ui.graphics.Color.White,
            onSurface = privatiColors.ink,
            onBackground = privatiColors.ink,
        )
    }

    CompositionLocalProvider(
        LocalPrivatiColors provides privatiColors,
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = PrivatiMaterialTypography,
            content = content,
        )
    }
}
