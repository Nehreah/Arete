package com.renea.arete_social.feature.miausnake.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Colores del tema programático. Los cinco de la paleta oliva del plan (#171B14, #35432A,
 * #A6B77A, #D8E69B, #E6C85F) más unos pocos derivados para legibilidad: ningún estado se
 * comunica solo con el color (la comida es un círculo, la pared lleva borde claro, el túnel una flecha).
 */
class GamePalette(
    val background: Color,
    val floorA: Color,
    val floorB: Color,
    val wall: Color,
    val wallEdge: Color,
    val body: Color,
    val bodyShade: Color,
    val head: Color,
    val eye: Color,
    val food: Color,
    val foodHighlight: Color,
    val frameSolid: Color,
    val frameWrap: Color,
    val tunnel: Color,
    val panel: Color,
    val text: Color,
    val textDim: Color,
    /** Sombra del rival en los retos (cian; magenta en contraste alto). */
    val ghost: Color = Color(0xFF6FD3E8)
) {
    companion object {
        val Olive = GamePalette(
            background = Color(0xFF171B14),
            floorA = Color(0xFF1B2117),
            floorB = Color(0xFF20281B),
            wall = Color(0xFF35432A),
            wallEdge = Color(0xFF6E8253),
            body = Color(0xFFA6B77A),
            bodyShade = Color(0xFF8A9C5F),
            head = Color(0xFFD8E69B),
            eye = Color(0xFF171B14),
            food = Color(0xFFE6C85F),
            foodHighlight = Color(0xFFF6EBB0),
            frameSolid = Color(0xFFA6B77A),
            frameWrap = Color(0xFF4A5C3A),
            tunnel = Color(0xFFE6C85F),
            panel = Color(0xFF35432A),
            text = Color(0xFFD8E69B),
            textDim = Color(0xFFA6B77A)
        )

        /** Contraste alto (ajustes): fondo negro, trazos blancos y comida amarilla. */
        val HighContrast = GamePalette(
            background = Color(0xFF000000),
            floorA = Color(0xFF0A0A0A),
            floorB = Color(0xFF151515),
            wall = Color(0xFF555555),
            wallEdge = Color(0xFFFFFFFF),
            body = Color(0xFF00E676),
            bodyShade = Color(0xFF00B85C),
            head = Color(0xFFFFFFFF),
            eye = Color(0xFF000000),
            food = Color(0xFFFFEB3B),
            foodHighlight = Color(0xFFFFFFFF),
            frameSolid = Color(0xFFFFFFFF),
            frameWrap = Color(0xFFB0B0B0),
            tunnel = Color(0xFFFFEB3B),
            panel = Color(0xFF2B2B2B),
            text = Color(0xFFFFFFFF),
            textDim = Color(0xFFD0D0D0),
            ghost = Color(0xFFFF4DFF)
        )
    }
}
