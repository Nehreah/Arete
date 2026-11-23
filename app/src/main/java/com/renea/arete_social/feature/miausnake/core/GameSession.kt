package com.renea.arete_social.feature.miausnake.core

/**
 * Partida en curso con grabación de repetición. Es el único objeto mutable del core.
 * La UI llama [turn] con cada gesto y [tick] cuando el acumulador de tiempo lo pida.
 */
class GameSession(val level: Level, val seed: Long) {
    var state: GameState = GameEngine.initial(level, seed)
        private set
    private val turns = ArrayList<TurnInput>()

    fun turn(d: Direction) {
        state = state.copy(queue = enqueue(state.queue, state.direction, d))
    }

    fun tick() {
        if (state.status != GameStatus.Running) return
        val consumed = state.queue.firstOrNull()
        if (consumed != null && consumed != state.direction) turns.add(TurnInput(state.tick, consumed))
        state = state.applyTick(level)
    }

    fun pause() { state = state.pause() }
    fun resume() { state = state.resume() }

    fun replay(): Replay = Replay(level.id, level.version, RULES_VERSION, seed, turns.toList())
}
