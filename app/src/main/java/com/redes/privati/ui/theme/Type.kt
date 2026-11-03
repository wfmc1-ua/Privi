package com.redes.privati.ui.theme

import androidx.compose.material3.Typography

/**
 * Tipografía de Material3 mapeada desde [PrivatiTypography].
 * Se pasa a [MaterialTheme] dentro de [PrivatiTheme] para que los componentes
 * de Material3 (TextField, Scaffold…) hereden los estilos de Privati.
 */
internal val PrivatiMaterialTypography = Typography(
    displayLarge = PrivatiTypography.display,
    titleLarge = PrivatiTypography.title1,
    titleMedium = PrivatiTypography.title2,
    headlineSmall = PrivatiTypography.heading,
    bodyLarge = PrivatiTypography.bodyStrong,
    bodyMedium = PrivatiTypography.body,
    bodySmall = PrivatiTypography.bodySm,
    labelLarge = PrivatiTypography.label,
    labelMedium = PrivatiTypography.caption,
    labelSmall = PrivatiTypography.micro,
)
