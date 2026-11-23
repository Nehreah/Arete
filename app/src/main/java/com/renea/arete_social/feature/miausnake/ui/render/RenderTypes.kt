package com.renea.arete_social.feature.miausnake.ui.render

/** Expresión de la cabeza. La decide el controlador, no el motor. */
enum class Mood { NORMAL, EATING, DEAD }

/** Pieza de cuerpo según los dos vecinos que une. CORNER_NE = une ARRIBA y DERECHA, etc. */
enum class BodyShape { HORIZONTAL, VERTICAL, CORNER_NE, CORNER_NW, CORNER_SE, CORNER_SW }

/** Qué lados de una pared dan a suelo (ahí el tema dibuja el borde claro). */
data class WallEdges(val top: Boolean, val bottom: Boolean, val left: Boolean, val right: Boolean)
