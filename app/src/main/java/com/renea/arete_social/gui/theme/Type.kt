package com.renea.arete_social.gui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)

/**
 * Estilos para texto que se LEE con calma (cartas, cuentos, poemas). La tipografía con serifa
 * viene con el sistema, así que funciona sin conexión y sin añadir archivos de fuente.
 */
object TipografiaLectura {
    private val familia = androidx.compose.ui.text.font.FontFamily.Serif

    val cuerpo = TextStyle(fontFamily = familia, fontSize = 15.sp, lineHeight = 25.sp, letterSpacing = 0.1.sp)
    val vistaPrevia = TextStyle(fontFamily = familia, fontSize = 13.sp, lineHeight = 19.sp)
    val titulo = TextStyle(fontFamily = familia, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 23.sp)
    val firma = TextStyle(
        fontFamily = familia,
        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
}
