package com.renea.arete_social.feature.miausnake.core

/** Mapas mínimos para las pruebas del motor. No son mapas del juego. */
object TestLevels {
    fun open(
        cols: Int = 20,
        rows: Int = 28,
        edge: EdgeMode = EdgeMode.WRAP,
        goalBites: Int? = null,
        walls: Set<Cell> = emptySet(),
        spawn: Cell = Cell(cols / 2, rows / 2),
        spawnDir: Direction = Direction.RIGHT,
        edgeRules: Edge = Edge(edge)
    ): Level = Level(
        id = "test_${cols}x$rows", version = 1, cols = cols, rows = rows, edge = edgeRules,
        walls = walls, spawn = spawn, spawnDir = spawnDir, goalBites = goalBites, fixedSeed = null,
        speed = SpeedCurve.build(startMs = 200, minMs = 95, tau = 18)
    )

    fun state(
        snake: List<Cell>, direction: Direction, food: Cell,
        queue: List<Direction> = emptyList(), rng: Long = 1L
    ) = GameState(
        snake = snake, direction = direction, queue = queue, food = food, foodEaten = 0, score = 0,
        tick = 0, elapsedMs = 0, rng = rng, status = GameStatus.Running
    )
}
