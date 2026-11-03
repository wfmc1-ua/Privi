package com.redes.privati.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Formas del sistema de diseño Privati.
 * Radios definidos en el spec: 6 dp (cola de burbuja), 18 dp (burbuja),
 * 28 dp (tarjetas), píldora completa (botones, campos, avatares).
 */
object PrivatiShapes {
    /** Radio de la esquina que forma la cola de la burbuja. */
    val bubbleTail = RoundedCornerShape(6.dp)

    /** Burbuja de mensaje. */
    val bubble = RoundedCornerShape(18.dp)

    /** Burbuja entrante: cola abajo-izquierda. */
    val bubbleIncoming = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomEnd = 18.dp,
        bottomStart = 6.dp,
    )

    /** Burbuja propia: cola abajo-derecha. */
    val bubbleOutgoing = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomEnd = 6.dp,
        bottomStart = 18.dp,
    )

    /** Tarjetas y paneles. */
    val card = RoundedCornerShape(28.dp)

    /** Píldora completa: botones, campos de texto, pestañas, avatares. */
    val pill = CircleShape
}
