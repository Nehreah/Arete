package com.renea.arete_social.feature.miausnake.ui.theme

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.renea.arete_social.feature.miausnake.core.Direction
import com.renea.arete_social.feature.miausnake.ui.render.BodyShape
import com.renea.arete_social.feature.miausnake.ui.render.Mood
import com.renea.arete_social.feature.miausnake.ui.render.WallEdges

/**
 * Capa visual intercambiable. El renderer SOLO conoce esta interfaz: en la Fase 2 la implementa
 * [ProgrammaticTheme] (formas); en la Fase 8 llegará `PixelArtTheme` con respaldo en esta.
 *
 * Diferencia con la sección «Capa visual» del plan: se añaden [floor] y [boardFrame] (el suelo y el
 * marco también deben poder cambiar de tema) y `tail` recibe la dirección HACIA EL CUELLO.
 */
interface GameTheme {
    val palette: GamePalette

    /** Suelo de todo el tablero (con su damero). `cell` = lado de celda en px. */
    fun DrawScope.floor(board: Rect, cols: Int, rows: Int, cell: Float)

    /** Cabeza mirando hacia [dir]. */
    fun DrawScope.head(cell: Rect, dir: Direction, mood: Mood)

    fun DrawScope.body(cell: Rect, shape: BodyShape)

    /** [towardNeck]: dirección que va de la cola hacia el segmento anterior. */
    fun DrawScope.tail(cell: Rect, towardNeck: Direction)

    fun DrawScope.food(cell: Rect)

    fun DrawScope.wall(cell: Rect, edges: WallEdges)

    /** Marca de túnel en una celda del borde; [dir] es la dirección de salida. */
    fun DrawScope.tunnel(cell: Rect, dir: Direction)

    /** Marco del tablero: continuo si el borde mata, punteado si es de envoltura. */
    fun DrawScope.boardFrame(board: Rect, solid: Boolean, cell: Float)
}
