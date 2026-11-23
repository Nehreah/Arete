package com.renea.arete_social.feature.miausnake.ui.render

import com.renea.arete_social.feature.miausnake.core.Cell
import com.renea.arete_social.feature.miausnake.core.Direction
import com.renea.arete_social.feature.miausnake.core.EdgeMode
import com.renea.arete_social.feature.miausnake.core.Level
import com.renea.arete_social.feature.miausnake.core.Side

/**
 * Dirección que va de [from] a [to] (celdas vecinas), resolviendo también el salto por el borde
 * abierto o el túnel. Sin reservas de memoria: se llama varias veces por segmento y frame.
 * Si las celdas no son vecinas devuelve RIGHT (no debería ocurrir: lo vigila RenderModelTest).
 */
fun dirTo(lv: Level, from: Cell, to: Cell): Direction {
    var dx = to.x - from.x
    var dy = to.y - from.y
    if (dx > 1) dx -= lv.cols else if (dx < -1) dx += lv.cols
    if (dy > 1) dy -= lv.rows else if (dy < -1) dy += lv.rows
    return when {
        dx == 1 -> Direction.RIGHT
        dx == -1 -> Direction.LEFT
        dy == 1 -> Direction.DOWN
        dy == -1 -> Direction.UP
        else -> Direction.RIGHT
    }
}

/** Forma del segmento que une las direcciones [a] y [b] (en cualquier orden). */
fun bodyShape(a: Direction, b: Direction): BodyShape {
    val up = a == Direction.UP || b == Direction.UP
    val down = a == Direction.DOWN || b == Direction.DOWN
    val left = a == Direction.LEFT || b == Direction.LEFT
    val right = a == Direction.RIGHT || b == Direction.RIGHT
    return when {
        left && right -> BodyShape.HORIZONTAL
        up && down -> BodyShape.VERTICAL
        up && right -> BodyShape.CORNER_NE
        up && left -> BodyShape.CORNER_NW
        down && right -> BodyShape.CORNER_SE
        down && left -> BodyShape.CORNER_SW
        left || right -> BodyShape.HORIZONTAL
        else -> BodyShape.VERTICAL
    }
}

class WallPiece(val cell: Cell, val edges: WallEdges)

/** Celda del borde por la que se entra/sale de un túnel, con la dirección de salida. */
data class TunnelMark(val cell: Cell, val dir: Direction)

/**
 * Lo que del mapa no cambia durante la partida, calculado UNA vez (no por frame):
 * paredes con sus bordes expuestos y marcas de túnel.
 */
class RenderModel(val level: Level) {
    val isSolid: Boolean = level.edge.default == EdgeMode.SOLID

    val walls: List<WallPiece> = level.walls
        .sortedWith(compareBy({ it.y }, { it.x }))
        .map { WallPiece(it, edgesOf(it)) }

    val tunnels: List<TunnelMark> = buildTunnels()

    // Fuera del tablero NO cuenta como suelo: el borde exterior no se resalta.
    private fun open(x: Int, y: Int): Boolean {
        val c = Cell(x, y)
        return level.inBounds(c) && !level.isWall(c)
    }

    private fun edgesOf(c: Cell) = WallEdges(
        top = open(c.x, c.y - 1), bottom = open(c.x, c.y + 1),
        left = open(c.x - 1, c.y), right = open(c.x + 1, c.y)
    )

    private fun buildTunnels(): List<TunnelMark> {
        if (!isSolid) return emptyList()
        val out = LinkedHashSet<TunnelMark>()
        fun add(side: Side, pos: Int) {
            val (cell, dir) = when (side) {
                Side.TOP -> Cell(pos, 0) to Direction.UP
                Side.BOTTOM -> Cell(pos, level.rows - 1) to Direction.DOWN
                Side.LEFT -> Cell(0, pos) to Direction.LEFT
                Side.RIGHT -> Cell(level.cols - 1, pos) to Direction.RIGHT
            }
            if (level.inBounds(cell) && !level.isWall(cell)) out.add(TunnelMark(cell, dir))
        }
        fun span(side: Side) = if (side == Side.TOP || side == Side.BOTTOM) level.cols else level.rows
        for (g in level.edge.gaps) {
            for (side in listOf(g.side, g.side.opposite)) {
                for (pos in maxOf(g.from, 0)..minOf(g.to, span(side) - 1)) add(side, pos)
            }
        }
        for (s in level.edge.wrapSides) {
            for (side in listOf(s, s.opposite)) for (pos in 0 until span(side)) add(side, pos)
        }
        return out.toList()
    }
}
