package com.renea.arete_social.feature.miausnake.core

/** Un paso. null = muerte (pared o borde sólido). Resuelve los cuatro modos de borde. */
fun step(c: Cell, d: Direction, lv: Level): Cell? {
    val n = Cell(c.x + d.dx, c.y + d.dy)
    if (lv.inBounds(n)) return if (lv.isWall(n)) null else n
    if (!lv.edge.isOpen(d.exitSide(), n)) return null
    val w = lv.wrap(n)
    return if (lv.isWall(w)) null else w
}

object GameEngine {
    fun initial(lv: Level, seed: Long): GameState {
        val snake = List(3) { i ->
            lv.wrap(Cell(lv.spawn.x - lv.spawnDir.dx * i, lv.spawn.y - lv.spawnDir.dy * i))
        }
        require(snake.none { lv.isWall(it) } && snake.toSet().size == 3) { "spawn sin espacio para 3 segmentos" }
        val occupied = snake.toHashSet()
        val (food, rng) = spawnFood(
            lv.floorCells.filter { it !in occupied },
            Xorshift64Star.seed(lv.fixedSeed ?: seed)
        )
        return GameState(
            snake = snake, direction = lv.spawnDir, queue = emptyList(),
            food = requireNotNull(food) { "mapa sin espacio para comida" },
            foodEaten = 0, score = 0, tick = 0L, elapsedMs = 0L, rng = rng,
            status = GameStatus.Running
        )
    }
}

/** Orden de un tick: giro -> step() -> comida/crecimiento -> colisión -> mover -> puntuar. */
fun GameState.applyTick(lv: Level): GameState {
    if (status != GameStatus.Running) return this
    val dir = queue.firstOrNull() ?: direction
    val next = step(snake.first(), dir, lv) ?: return copy(status = GameStatus.GameOver)
    val eats = next == food
    // La cola se libera solo si no come: entrar en la celda que deja la cola es válido.
    val body = if (eats) snake else snake.dropLast(1)
    if (next in body) return copy(status = GameStatus.GameOver)
    val newSnake = listOf(next) + body
    val eaten = foodEaten + if (eats) 1 else 0
    val base = copy(
        snake = newSnake, direction = dir, queue = queue.drop(1), foodEaten = eaten,
        score = score + if (eats) 10 else 0, tick = tick + 1,
        elapsedMs = elapsedMs + lv.speed.tickMs(foodEaten)
    )
    if (!eats) return base
    if (lv.goalBites != null && eaten >= lv.goalBites) return base.copy(status = GameStatus.Won)
    val occupied = newSnake.toHashSet()
    val (cell, rng2) = spawnFood(lv.floorCells.filter { it !in occupied }, rng)
    return if (cell == null) base.copy(rng = rng2, status = GameStatus.Won) // tablero lleno = victoria
    else base.copy(food = cell, rng = rng2)
}
