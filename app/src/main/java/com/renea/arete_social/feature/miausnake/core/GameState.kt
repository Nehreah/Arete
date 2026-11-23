package com.renea.arete_social.feature.miausnake.core

enum class GameStatus { Ready, Running, Paused, Won, GameOver }

/**
 * Estado inmutable de una partida. La cabeza es `snake[0]`, la cola el último.
 * `elapsedMs` es tiempo VIRTUAL (suma de los ticks jugados): base de medallas y verificación.
 */
data class GameState(
    val snake: List<Cell>,
    val direction: Direction,
    val queue: List<Direction>,
    val food: Cell,
    val foodEaten: Int,
    val score: Int,
    val tick: Long,
    val elapsedMs: Long,
    val rng: Long,
    val status: GameStatus
) {
    val isFinished: Boolean get() = status == GameStatus.Won || status == GameStatus.GameOver
}

/** Cola de 2 giros. Cada giro se compara contra el último encolado (o la dirección actual). */
fun enqueue(queue: List<Direction>, current: Direction, d: Direction): List<Direction> {
    val ref = queue.lastOrNull() ?: current
    return if (queue.size >= 2 || d == ref || d == ref.opposite) queue else queue + d
}

fun GameState.pause(): GameState = if (status == GameStatus.Running) copy(status = GameStatus.Paused) else this
fun GameState.resume(): GameState = if (status == GameStatus.Paused) copy(status = GameStatus.Running) else this
