package com.redes.privati.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// TODO: Reemplazar por Inter cargado con ui-text-google-fonts cuando se configure el proveedor.
private val PrivatiFont = FontFamily.SansSerif

/**
 * Estilos tipográficos del sistema de diseño Privati.
 * Accesible directamente como singleton: [PrivatiTypography.body], [PrivatiTypography.heading], etc.
 */
object PrivatiTypography {
    /** 30 sp · Bold · −0.03 em — títulos de primer nivel, pantalla de bienvenida. */
    val display = TextStyle(
        fontFamily = PrivatiFont,
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.03).em,
    )

    /** 28 sp · Bold — títulos de pantalla. */
    val title1 = TextStyle(
        fontFamily = PrivatiFont,
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
    )

    /** 22 sp · Bold — subtítulos de sección. */
    val title2 = TextStyle(
        fontFamily = PrivatiFont,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
    )

    /** 18 sp · Bold — encabezados de tarjeta o grupo. */
    val heading = TextStyle(
        fontFamily = PrivatiFont,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
    )

    /** 16 sp · SemiBold — texto enfatizado en cuerpo. */
    val bodyStrong = TextStyle(
        fontFamily = PrivatiFont,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
    )

    /** 15 sp · Normal — texto de burbujas y párrafos (interlineado 1.4 en burbujas). */
    val body = TextStyle(
        fontFamily = PrivatiFont,
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
    )

    /** 14 sp · Normal — texto secundario, descripciones cortas. */
    val bodySm = TextStyle(
        fontFamily = PrivatiFont,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
    )

    /** 13 sp · Medium — etiquetas, estados, botones pequeños. */
    val label = TextStyle(
        fontFamily = PrivatiFont,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
    )

    /** 12 sp · Medium — horas, metadatos, píldoras. */
    val caption = TextStyle(
        fontFamily = PrivatiFont,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
    )

    /** 11 sp · SemiBold — textos muy pequeños, contadores. */
    val micro = TextStyle(
        fontFamily = PrivatiFont,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )

    /** 18 sp · SemiBold · Monospace · 0.2 em — código de sala. */
    val code = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (0.2).em,
    )
}
