package com.renea.arete_social.feature.miausnake.core

/** Mapa ya validado en forma (los validadores de jugabilidad llegan en la Fase 4). */
class Level(
    val id: String,
    val version: Int,
    val cols: Int,
    val rows: Int,
    val edge: Edge,
    val walls: Set<Cell>,
    val spawn: Cell,
    val spawnDir: Direction,
    val goalBites: Int?,
    val fixedSeed: Long?,
    val speed: SpeedCurve
) {
    init {
        require(cols > 0 && rows > 0) { "tablero vacío" }
        require(walls.all { inBounds(it) }) { "pared fuera del tablero" }
        require(inBounds(spawn) && !isWall(spawn)) { "spawn inválido" }
        require(goalBites == null || goalBites > 0) { "meta inválida" }
    }

    fun inBounds(c: Cell): Boolean = c.x in 0 until cols && c.y in 0 until rows
    fun isWall(c: Cell): Boolean = c in walls
    fun wrap(c: Cell): Cell = Cell(((c.x % cols) + cols) % cols, ((c.y % rows) + rows) % rows)

    /** Orden fila a fila: determinista, base del reparto de comida. */
    val floorCells: List<Cell> by lazy {
        val res = ArrayList<Cell>(cols * rows)
        for (y in 0 until rows) for (x in 0 until cols) {
            val c = Cell(x, y)
            if (!isWall(c)) res.add(c)
        }
        res
    }
}
