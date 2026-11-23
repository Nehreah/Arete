package com.renea.arete_social.feature.miausnake.ui.render

import androidx.compose.ui.geometry.Rect
import com.renea.arete_social.feature.miausnake.core.Cell

/**
 * Dónde se dibuja el tablero dentro del área disponible. La celda mide un número ENTERO y PAR de
 * píxeles (par para que girar la cabeza 90° no deje bordes a medio píxel) y el tablero se centra
 * con origen entero: nada se dibuja en posiciones subpíxel.
 */
class BoardLayout(
    val cols: Int,
    val rows: Int,
    val cellPx: Int,
    val originX: Float,
    val originY: Float
) {
    val boardWidth: Float get() = (cols * cellPx).toFloat()
    val boardHeight: Float get() = (rows * cellPx).toFloat()

    fun boardRect(): Rect = Rect(originX, originY, originX + boardWidth, originY + boardHeight)

    fun cellRect(c: Cell): Rect {
        val l = originX + c.x * cellPx
        val t = originY + c.y * cellPx
        return Rect(l, t, l + cellPx, t + cellPx)
    }

    companion object {
        const val MIN_CELL_PX = 2

        fun compute(availWidth: Float, availHeight: Float, cols: Int, rows: Int): BoardLayout {
            require(cols > 0 && rows > 0) { "tablero vacío" }
            val raw = minOf(availWidth / cols, availHeight / rows).toInt()
            val cell = maxOf(MIN_CELL_PX, raw and 1.inv())
            val ox = maxOf(0f, ((availWidth - cols * cell) / 2f).toInt().toFloat())
            val oy = maxOf(0f, ((availHeight - rows * cell) / 2f).toInt().toFloat())
            return BoardLayout(cols, rows, cell, ox, oy)
        }
    }
}
