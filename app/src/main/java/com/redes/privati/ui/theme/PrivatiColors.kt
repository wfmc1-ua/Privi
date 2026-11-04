package com.redes.privati.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Paleta de colores del sistema de diseño Privati.
 * Usada exclusivamente a través de [PrivatiTheme.colors] dentro de composables.
 *
 * @param surface Fondo de pantalla y burbuja entrante.
 * @param surfaceMuted Fondo de campos y del hilo de chat.
 * @param surfaceRaised Burbuja entrante en oscuro y avatar de sistema.
 * @param ink Texto principal.
 * @param inkSecondary Texto de apoyo.
 * @param inkMuted Horas y metadatos.
 * @param primary Color de marca: CTAs, insignias, barra de navegación.
 * @param bubbleOut Fondo de la burbuja propia.
 * @param primaryTint Fondo del botón secundario.
 * @param primaryInk Texto del botón secundario.
 * @param primarySubtle Fondo de fila destacada.
 * @param primaryWash Fondo de botón de icono redondo.
 * @param border Bordes de 1 dp.
 * @param divider Separadores de 1 dp.
 * @param success Indicador de estado en línea (solo puntos, nunca texto).
 * @param danger Acciones destructivas: cerrar sala, expulsar.
 * @param dangerTint Fondo de botón destructivo.
 * @param dangerInk Texto de botón destructivo.
 * @param isDark Indica si este esquema es para modo oscuro.
 */
data class PrivatiColors(
    val surface: Color,
    val surfaceMuted: Color,
    val surfaceRaised: Color,
    val ink: Color,
    val inkSecondary: Color,
    val inkMuted: Color,
    val primary: Color,
    val bubbleOut: Color,
    val primaryTint: Color,
    val primaryInk: Color,
    val primarySubtle: Color,
    val primaryWash: Color,
    val border: Color,
    val divider: Color,
    val success: Color,
    val danger: Color,
    val dangerTint: Color,
    val dangerInk: Color,
    /** Acción Silenciar al deslizar un chat. */
    val warning: Color,
    /** Texto sobre fondo warning. */
    val warningInk: Color,
    val isDark: Boolean,
) {
    companion object {
        /** Accede a los colores del tema actual desde cualquier composable. */
        val current: PrivatiColors
            @Composable @ReadOnlyComposable
            get() = LocalPrivatiColors.current
    }
}

internal val LocalPrivatiColors = staticCompositionLocalOf { PrivatiColorsLight }

/** Esquema de colores para modo claro. */
internal val PrivatiColorsLight = PrivatiColors(
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFF7F7FA),
    surfaceRaised = Color(0xFFF1F1F4),
    ink = Color(0xFF1B1B1F),
    inkSecondary = Color(0xFF4A4A55),
    inkMuted = Color(0xFF6B6B76),
    primary = Color(0xFF6C5CE7),
    bubbleOut = Color(0xFF5A49D9),
    primaryTint = Color(0xFFEDEAFF),
    primaryInk = Color(0xFF3F2FB0),
    primarySubtle = Color(0xFFF6F4FF),
    primaryWash = Color(0xFFF4F2FF),
    border = Color(0xFFE4E4EA),
    divider = Color(0xFFEEEEF2),
    success = Color(0xFF22A05B),
    danger = Color(0xFFD12F45),
    dangerTint = Color(0xFFFDECEE),
    dangerInk = Color(0xFFB0263A),
    warning = Color(0xFFF5B04B),
    warningInk = Color(0xFF1B1B1F),
    isDark = false,
)

/** Esquema de colores para modo oscuro. */
internal val PrivatiColorsDark = PrivatiColors(
    surface = Color(0xFF131318),
    surfaceMuted = Color(0xFF1B1B22),
    surfaceRaised = Color(0xFF26262F),
    ink = Color(0xFFF2F2F5),
    inkSecondary = Color(0xFFB8B8C4),
    inkMuted = Color(0xFF9494A0),
    primary = Color(0xFF6C5CE7),
    bubbleOut = Color(0xFF5A49D9),
    primaryTint = Color(0xFF2A2550),
    primaryInk = Color(0xFFC9C2FF),
    primarySubtle = Color(0xFF1E1B33),
    primaryWash = Color(0xFF221E3A),
    border = Color(0xFF33333D),
    divider = Color(0xFF26262E),
    success = Color(0xFF3DC27A),
    danger = Color(0xFFD12F45),
    dangerTint = Color(0xFF3A1A20),
    dangerInk = Color(0xFFFF8A98),
    warning = Color(0xFFF5B04B),
    warningInk = Color(0xFF1B1B1F),
    isDark = true,
)

/**
 * Paletas de color para los avatares: fondo y tinta para cada índice 0-5.
 * Selección: abs(participantId.hashCode()) % 6
 */
internal val AvatarPalettesLight = listOf(
    Pair(Color(0xFFFFD9C7), Color(0xFF7A3418)), // melocotón
    Pair(Color(0xFFD6DEFF), Color(0xFF26358A)), // azul
    Pair(Color(0xFFFFE9A8), Color(0xFF6B4E00)), // amarillo
    Pair(Color(0xFFCFEEDD), Color(0xFF185C38)), // verde
    Pair(Color(0xFFE7E2FF), Color(0xFF3F2FB0)), // lila
    Pair(Color(0xFFFFDCE8), Color(0xFF8A1F48)), // rosa
)

internal val AvatarPalettesDark = listOf(
    Pair(Color(0xFF4A2A1E), Color(0xFFFFC7AE)),
    Pair(Color(0xFF232D5C), Color(0xFFC2CEFF)),
    Pair(Color(0xFF44380F), Color(0xFFFFE08A)),
    Pair(Color(0xFF173A28), Color(0xFFA9E3C3)),
    Pair(Color(0xFF2C2560), Color(0xFFC9C2FF)),
    Pair(Color(0xFF4A1F31), Color(0xFFFFBFD6)),
)
