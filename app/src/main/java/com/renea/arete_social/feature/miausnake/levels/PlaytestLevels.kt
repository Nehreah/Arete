package com.renea.arete_social.feature.miausnake.levels

import com.renea.arete_social.feature.miausnake.core.Cell
import com.renea.arete_social.feature.miausnake.core.Direction
import com.renea.arete_social.feature.miausnake.core.Edge
import com.renea.arete_social.feature.miausnake.core.EdgeMode
import com.renea.arete_social.feature.miausnake.core.Gap
import com.renea.arete_social.feature.miausnake.core.Level
import com.renea.arete_social.feature.miausnake.core.Side
import com.renea.arete_social.feature.miausnake.core.SpeedCurve
import com.renea.arete_social.feature.miausnake.ui.spike.CellSpec

/**
 * DOS mapas de prueba de la Fase 2 (1 WRAP + 1 SOLID), generados para la rejilla del spike.
 * NO son mapas del juego: en la Fase 4 los mapas pasan a JSON versionado con validador. Las
 * curvas de velocidad son provisionales (se calibran en la Fase 4).
 */
enum class PlaytestMap(val title: String) {
    /** Sin bordes ni obstáculos, con meta: prueba la envoltura y la pantalla de victoria. */
    WRAP("Pradera (WRAP)"),

    /** Borde mortal, dos barras y túnel arriba/abajo, modo infinito: prueba paredes, túnel y derrota. */
    SOLID("Jardín (SOLID)")
}

object PlaytestLevels {
    const val WRAP_GOAL_BITES = 12

    fun create(map: PlaytestMap, spec: CellSpec): Level = when (map) {
        PlaytestMap.WRAP -> wrap(spec)
        PlaytestMap.SOLID -> solid(spec)
    }

    private fun idOf(base: String, spec: CellSpec) = "playtest-$base-${spec.cols}x${spec.rows}"

    private fun wrap(spec: CellSpec): Level = Level(
        id = idOf("wrap", spec), version = 1, cols = spec.cols, rows = spec.rows,
        edge = Edge(EdgeMode.WRAP), walls = emptySet(),
        spawn = Cell(spec.cols / 2, spec.rows / 2), spawnDir = Direction.RIGHT,
        goalBites = WRAP_GOAL_BITES, fixedSeed = null,
        speed = SpeedCurve.build(startMs = 220, minMs = 110, tau = 14)
    )

    private fun solid(spec: CellSpec): Level {
        val cols = spec.cols
        val rows = spec.rows
        val y1 = rows / 3
        val y2 = 2 * rows / 3
        val walls = buildSet {
            for (x in 2 until cols / 2 - 1) add(Cell(x, y1))
            for (x in cols / 2 + 1 until cols - 2) add(Cell(x, y2))
        }
        // Túnel de 2 celdas arriba/abajo: el lado opuesto se abre solo.
        val gap = Gap(Side.TOP, cols / 2 - 1, cols / 2)
        return Level(
            id = idOf("solid", spec), version = 1, cols = cols, rows = rows,
            edge = Edge(EdgeMode.SOLID, gaps = listOf(gap)), walls = walls,
            spawn = Cell(cols / 2, rows - 4), spawnDir = Direction.UP,
            goalBites = null, fixedSeed = null,
            speed = SpeedCurve.build(startMs = 200, minMs = 95, tau = 18)
        )
    }
}
