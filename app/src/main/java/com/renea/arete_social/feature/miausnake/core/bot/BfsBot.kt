package com.renea.arete_social.feature.miausnake.core.bot

import com.renea.arete_social.feature.miausnake.core.Cell
import com.renea.arete_social.feature.miausnake.core.Direction
import com.renea.arete_social.feature.miausnake.core.GameSession
import com.renea.arete_social.feature.miausnake.core.GameState
import com.renea.arete_social.feature.miausnake.core.Level
import com.renea.arete_social.feature.miausnake.core.ReplayCodec
import com.renea.arete_social.feature.miausnake.core.step

/**
 * Bot BFS determinista. Va a la comida por el camino más corto SOLO si después de comer la
 * cabeza aún alcanza la cola (no se encierra); si no, elige el movimiento que conserva la cola
 * alcanzable y deja más área libre. Base del bot de equidad y del tiempo par (Fase 4).
 */
object BfsBot {
    private class Move(val dir: Direction, val cell: Cell)

    fun nextDirection(state: GameState, level: Level): Direction {
        val snake = state.snake
        val head = snake.first()
        val blocked = snake.dropLast(1).toHashSet() // la cola se libera al moverse
        val back = state.direction.opposite

        val path = shortestPath(head, state.food, level, blocked, back)
        if (path != null && path.isNotEmpty()) {
            val after = (path.map { it.cell }.reversed() + snake).take(snake.size + 1)
            if (canReachTail(after, level)) return path.first().dir
        }

        var best: Direction? = null
        var bestTail = false
        var bestArea = -1
        for (d in Direction.entries) {
            if (d == back) continue
            val n = step(head, d, level) ?: continue
            if (n in blocked) continue
            val moved = listOf(n) + snake.dropLast(1)
            val tail = canReachTail(moved, level)
            val libre = area(n, level, blocked + n)
            val better = best == null || (tail && !bestTail) || (tail == bestTail && libre > bestArea)
            if (better) {
                best = d; bestTail = tail; bestArea = libre
            }
        }
        return best ?: state.direction // sin salida: morirá igualmente
    }

    /** Juega la partida completa con el bot y devuelve la sesión (con su repetición). */
    fun play(level: Level, seed: Long, maxTicks: Long = ReplayCodec.MAX_TICK): GameSession {
        val session = GameSession(level, seed)
        while (!session.state.isFinished && session.state.tick < maxTicks) {
            val d = nextDirection(session.state, level)
            if (d != session.state.direction) session.turn(d)
            session.tick()
        }
        return session
    }

    private fun shortestPath(
        start: Cell, goal: Cell, level: Level, blocked: Set<Cell>, forbiddenFirst: Direction
    ): List<Move>? {
        val parent = HashMap<Cell, Pair<Cell, Direction>>()
        val seen = HashSet<Cell>()
        seen.add(start)
        val queue = ArrayDeque<Cell>()
        queue.addLast(start)
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            if (c == goal) break
            for (d in Direction.entries) {
                if (c == start && d == forbiddenFirst) continue
                val n = step(c, d, level) ?: continue
                if (n in seen || n in blocked) continue
                seen.add(n)
                parent[n] = c to d
                queue.addLast(n)
            }
        }
        if (goal !in seen) return null
        val path = ArrayList<Move>()
        var c = goal
        while (c != start) {
            val (p, d) = parent.getValue(c)
            path.add(Move(d, c))
            c = p
        }
        path.reverse()
        return path
    }

    private fun canReachTail(snake: List<Cell>, level: Level): Boolean {
        val head = snake.first()
        val tail = snake.last()
        val body = snake.dropLast(1).toHashSet()
        val seen = HashSet<Cell>()
        seen.add(head)
        val queue = ArrayDeque<Cell>()
        queue.addLast(head)
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            for (d in Direction.entries) {
                val n = step(c, d, level) ?: continue
                if (n == tail) return true
                if (n in body || n in seen) continue
                seen.add(n)
                queue.addLast(n)
            }
        }
        return false
    }

    private fun area(start: Cell, level: Level, blocked: Set<Cell>): Int {
        val seen = HashSet<Cell>()
        seen.add(start)
        val queue = ArrayDeque<Cell>()
        queue.addLast(start)
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            for (d in Direction.entries) {
                val n = step(c, d, level) ?: continue
                if (n in blocked || n in seen) continue
                seen.add(n)
                queue.addLast(n)
            }
        }
        return seen.size
    }
}
