package com.renea.arete_social.feature.miausnake.ui.theme

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.renea.arete_social.feature.miausnake.core.Direction
import com.renea.arete_social.feature.miausnake.ui.render.BodyShape
import com.renea.arete_social.feature.miausnake.ui.render.Mood
import com.renea.arete_social.feature.miausnake.ui.render.WallEdges

/**
 * Tema de la Fase 2: solo rectángulos, círculos y triángulos. Cero PNG, cero audio.
 *
 * Geometría: todo se define en fracciones de la celda `s`. La cabeza y la cola se dibujan «mirando
 * hacia ARRIBA» y se giran 90° múltiplos alrededor del centro de la celda.
 */
class ProgrammaticTheme(override val palette: GamePalette = GamePalette.Olive) : GameTheme {

    // Grosor del cuerpo (fracción de la celda).
    private val bodyW = 0.76f

    private fun angleOf(d: Direction): Float = when (d) {
        Direction.UP -> 0f
        Direction.RIGHT -> 90f
        Direction.DOWN -> 180f
        Direction.LEFT -> 270f
    }

    override fun DrawScope.floor(board: Rect, cols: Int, rows: Int, cell: Float) {
        drawRect(palette.floorA, board.topLeft, board.size)
        val sz = Size(cell, cell)
        for (y in 0 until rows) {
            var x = (y + 1) and 1 // damero: celdas con (x + y) impar
            while (x < cols) {
                drawRect(palette.floorB, Offset(board.left + x * cell, board.top + y * cell), sz)
                x += 2
            }
        }
    }

    override fun DrawScope.wall(cell: Rect, edges: WallEdges) {
        val w = cell.width
        val t = maxOf(1f, (w / 10f).toInt().toFloat())
        drawRect(palette.wall, cell.topLeft, cell.size)
        if (edges.top) drawRect(palette.wallEdge, cell.topLeft, Size(w, t))
        if (edges.bottom) drawRect(palette.wallEdge, Offset(cell.left, cell.bottom - t), Size(w, t))
        if (edges.left) drawRect(palette.wallEdge, cell.topLeft, Size(t, cell.height))
        if (edges.right) drawRect(palette.wallEdge, Offset(cell.right - t, cell.top), Size(t, cell.height))
        val c = (w * 0.2f).toInt().toFloat()
        drawRect(palette.wallEdge.copy(alpha = 0.45f), Offset(cell.center.x - c / 2f, cell.center.y - c / 2f), Size(c, c))
    }

    override fun DrawScope.food(cell: Rect) {
        val s = cell.width
        val c = cell.center
        drawCircle(palette.food, radius = s * 0.32f, center = c)
        drawCircle(palette.foodHighlight, radius = s * 0.09f, center = Offset(c.x - s * 0.11f, c.y - s * 0.11f))
    }

    override fun DrawScope.tunnel(cell: Rect, dir: Direction) {
        val s = cell.width
        drawRect(palette.panel.copy(alpha = 0.55f), cell.topLeft, cell.size)
        rotate(angleOf(dir), pivot = cell.center) {
            val path = Path().apply {
                moveTo(cell.left + 0.50f * s, cell.top + 0.16f * s)
                lineTo(cell.left + 0.80f * s, cell.top + 0.64f * s)
                lineTo(cell.left + 0.20f * s, cell.top + 0.64f * s)
                close()
            }
            drawPath(path, palette.tunnel)
        }
    }

    override fun DrawScope.body(cell: Rect, shape: BodyShape) {
        val s = cell.width
        val t = (s * bodyW).toInt().toFloat()
        val p = ((s - t) / 2f).toInt().toFloat() // margen a cada lado del grosor
        val l = cell.left
        val tp = cell.top
        val half = (s / 2f).toInt().toFloat()
        when (shape) {
            BodyShape.HORIZONTAL -> {
                drawRect(palette.body, Offset(l, tp + p), Size(s, t))
                stripe(Offset(l + half - s * 0.06f, tp + p), Size(s * 0.12f, t))
            }
            BodyShape.VERTICAL -> {
                drawRect(palette.body, Offset(l + p, tp), Size(t, s))
                stripe(Offset(l + p, tp + half - s * 0.06f), Size(t, s * 0.12f))
            }
            else -> {
                // Núcleo redondeado (el codo exterior queda redondo) + dos brazos hasta el borde.
                drawRoundRect(palette.body, Offset(l + p, tp + p), Size(t, t), CornerRadius(t * 0.28f))
                val up = shape == BodyShape.CORNER_NE || shape == BodyShape.CORNER_NW
                val right = shape == BodyShape.CORNER_NE || shape == BodyShape.CORNER_SE
                if (up) drawRect(palette.body, Offset(l + p, tp), Size(t, half + 1f))
                else drawRect(palette.body, Offset(l + p, tp + half - 1f), Size(t, s - half + 1f))
                if (right) drawRect(palette.body, Offset(l + half - 1f, tp + p), Size(s - half + 1f, t))
                else drawRect(palette.body, Offset(l, tp + p), Size(half + 1f, t))
            }
        }
    }

    private fun DrawScope.stripe(topLeft: Offset, size: Size) =
        drawRect(palette.bodyShade, topLeft, size)

    override fun DrawScope.tail(cell: Rect, towardNeck: Direction) {
        val s = cell.width
        val t = (s * bodyW).toInt().toFloat()
        val p = ((s - t) / 2f).toInt().toFloat()
        rotate(angleOf(towardNeck), pivot = cell.center) {
            // Cápsula a lo largo de la celda; el lado del cuello (arriba) se cuadra para empalmar.
            drawRoundRect(palette.body, Offset(cell.left + p, cell.top), Size(t, s), CornerRadius(t / 2f))
            drawRect(palette.body, Offset(cell.left + p, cell.top), Size(t, s / 2f))
            drawRect(palette.bodyShade, Offset(cell.left + p, cell.top + s * 0.30f), Size(t, s * 0.10f))
        }
    }

    override fun DrawScope.head(cell: Rect, dir: Direction, mood: Mood) {
        val s = cell.width
        val ox = cell.left
        val oy = cell.top
        rotate(angleOf(dir), pivot = cell.center) {
            // Orejas: dos triángulos dentro de la celda.
            drawPath(ear(ox, oy, s, 0.12f, 0.14f, 0.38f), palette.head)
            drawPath(ear(ox, oy, s, 0.88f, 0.86f, 0.62f), palette.head)
            // Cabeza.
            drawRoundRect(
                palette.head, Offset(ox + 0.08f * s, oy + 0.20f * s),
                Size(0.84f * s, 0.74f * s), CornerRadius(0.20f * s)
            )
            val ew = 0.14f * s
            val lx = ox + 0.26f * s
            val rx = ox + 0.60f * s
            when (mood) {
                Mood.NORMAL -> {
                    drawRect(palette.eye, Offset(lx, oy + 0.42f * s), Size(ew, 0.16f * s))
                    drawRect(palette.eye, Offset(rx, oy + 0.42f * s), Size(ew, 0.16f * s))
                    drawRect(palette.food, Offset(ox + 0.45f * s, oy + 0.64f * s), Size(0.10f * s, 0.07f * s))
                }
                Mood.EATING -> {
                    // Ojos entrecerrados y boca abierta.
                    drawRect(palette.eye, Offset(lx, oy + 0.48f * s), Size(ew, 0.05f * s))
                    drawRect(palette.eye, Offset(rx, oy + 0.48f * s), Size(ew, 0.05f * s))
                    drawRect(palette.eye, Offset(ox + 0.38f * s, oy + 0.68f * s), Size(0.24f * s, 0.16f * s))
                }
                Mood.DEAD -> {
                    val w = maxOf(1f, 0.05f * s)
                    for (x in floatArrayOf(lx, rx)) {
                        val y = oy + 0.42f * s
                        drawLine(palette.eye, Offset(x, y), Offset(x + ew, y + ew), w)
                        drawLine(palette.eye, Offset(x + ew, y), Offset(x, y + ew), w)
                    }
                }
            }
        }
    }

    /** Oreja: base en el lado de la cabeza [bx] y punta hacia arriba. [tipX] y [innerX] en fracción de s. */
    private fun ear(ox: Float, oy: Float, s: Float, bx: Float, tipX: Float, innerX: Float) = Path().apply {
        moveTo(ox + bx * s, oy + 0.30f * s)
        lineTo(ox + tipX * s, oy + 0.04f * s)
        lineTo(ox + innerX * s, oy + 0.24f * s)
        close()
    }

    override fun DrawScope.boardFrame(board: Rect, solid: Boolean, cell: Float) {
        val w = maxOf(2f, (cell * 0.10f).toInt().toFloat())
        val half = w / 2f
        val tl = Offset(board.left + half, board.top + half)
        val sz = Size(board.width - w, board.height - w)
        if (solid) {
            drawRect(palette.frameSolid, tl, sz, style = Stroke(width = w))
        } else {
            drawRect(
                palette.frameWrap, tl, sz,
                style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(cell * 0.5f, cell * 0.5f)))
            )
        }
    }
}
