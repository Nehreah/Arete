package com.renea.arete_social.feature.miausnake.ui.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.renea.arete_social.feature.miausnake.core.Cell
import com.renea.arete_social.feature.miausnake.core.GhostTrack

/**
 * Lo necesario para dibujar la sombra del rival. [tick] lo fija la pantalla de partida en cada
 * frame (a partir del tiempo virtual de quien juega); [buffer] se reutiliza para no reservar memoria.
 */
class GhostDraw(val track: GhostTrack, val color: Color, val buffer: IntArray) {
    var tick: Int = 0
}

/**
 * La sombra es un color translúcido sin sprite: el cuerpo son cuadrados más pequeños que la celda
 * y la cabeza lleva además un contorno, así se distingue de la serpiente propia sin depender solo del color.
 */
fun DrawScope.drawGhost(g: GhostDraw, layout: BoardLayout) {
    val cols = g.track.cols
    val n = g.track.fill(g.tick, g.buffer)
    for (k in n - 1 downTo 0) {
        val idx = g.buffer[k]
        val r = layout.cellRect(Cell(idx % cols, idx / cols))
        val inset = r.width * 0.18f
        val topLeft = Offset(r.left + inset, r.top + inset)
        val size = Size(r.width - 2f * inset, r.height - 2f * inset)
        val head = k == 0
        drawRect(g.color.copy(alpha = if (head) 0.75f else 0.38f), topLeft, size)
        if (head) drawRect(g.color, topLeft, size, style = Stroke(width = maxOf(1f, r.width * 0.09f)))
    }
}
