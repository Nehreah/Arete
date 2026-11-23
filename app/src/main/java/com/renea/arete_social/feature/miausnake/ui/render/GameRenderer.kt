package com.renea.arete_social.feature.miausnake.ui.render

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import com.renea.arete_social.feature.miausnake.core.Cell
import com.renea.arete_social.feature.miausnake.core.GameState
import com.renea.arete_social.feature.miausnake.ui.theme.GameTheme

/**
 * Dibuja un snapshot del [GameState]. No modifica ninguna regla y solo habla con [GameTheme].
 * Orden de capas: fondo → suelo → paredes → túneles → comida → sombra del rival (retos) → cuerpo → cola → cabeza → marco.
 *
 * Interpolación (efecto elástico): el render va UN tick por detrás de la lógica. Con [alpha] 0..1
 * (avance dentro del tick) la cabeza se desliza hacia su celda y la cola se retrae desde la celda
 * que acaba de dejar ([prevTail]). Se desactiva en el tick 0 y con la partida terminada.
 *
 * @param prevTail cola ANTES del último tick (null si no hay). Si es igual a la cola actual, la
 *   serpiente acaba de crecer y la cola no se mueve.
 */
fun DrawScope.drawGame(
    model: RenderModel,
    state: GameState,
    layout: BoardLayout,
    theme: GameTheme,
    alpha: Float,
    prevTail: Cell?,
    mood: Mood,
    interpolate: Boolean,
    ghost: GhostDraw? = null
) {
    val lv = model.level
    val cell = layout.cellPx.toFloat()
    val board = layout.boardRect()

    drawRect(theme.palette.background)

    with(theme) {
        floor(board, lv.cols, lv.rows, cell)
        for (w in model.walls) wall(layout.cellRect(w.cell), w.edges)
        for (t in model.tunnels) tunnel(layout.cellRect(t.cell), t.dir)
        food(layout.cellRect(state.food))
        if (ghost != null) drawGhost(ghost, layout)

        val snake = state.snake
        val last = snake.lastIndex
        val interp = interpolate && state.tick > 0L && !state.isFinished
        val p = alpha.coerceIn(0f, 1f)

        // La cabeza puede asomar fuera del tablero al deslizarse por un borde abierto: se recorta.
        clipRect(board.left, board.top, board.right, board.bottom) {
            for (i in 1 until last) {
                val toTail = dirTo(lv, snake[i], snake[i + 1])
                val toHead = dirTo(lv, snake[i], snake[i - 1])
                body(layout.cellRect(snake[i]), bodyShape(toTail, toHead))
            }

            val tailCell = snake[last]
            val towardNeck = dirTo(lv, tailCell, snake[last - 1])
            if (interp && prevTail != null && prevTail != tailCell) {
                // La celda nueva de la cola ya es un tramo de cuerpo; la punta vieja se retrae hacia ella.
                val towardOld = dirTo(lv, tailCell, prevTail)
                body(layout.cellRect(tailCell), bodyShape(towardNeck, towardOld))
                val old = layout.cellRect(prevTail)
                val toNew = towardOld.opposite
                clipRect(old.left, old.top, old.right, old.bottom) {
                    tail(old.translate(toNew.dx * p * cell, toNew.dy * p * cell), toNew)
                }
            } else {
                tail(layout.cellRect(tailCell), towardNeck)
            }

            val back = if (interp) 1f - p else 0f
            val dir = state.direction
            head(layout.cellRect(snake[0]).translate(-dir.dx * back * cell, -dir.dy * back * cell), dir, mood)
        }

        boardFrame(board, model.isSolid, cell)
    }
}
