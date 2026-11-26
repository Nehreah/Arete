package com.renea.arete_social.feature.miausnake.levels

import com.renea.arete_social.feature.miausnake.core.Cell
import com.renea.arete_social.feature.miausnake.core.Direction
import com.renea.arete_social.feature.miausnake.core.Edge
import com.renea.arete_social.feature.miausnake.core.EdgeMode
import com.renea.arete_social.feature.miausnake.core.Level
import com.renea.arete_social.feature.miausnake.core.Side
import com.renea.arete_social.feature.miausnake.core.SpeedCurve

/**
 * Especificación completa de un mapa cargado desde archivo JSON.
 */
data class LevelSpec(
    val id: String,
    val name: String,
    val pack: Int,
    val levelVersion: Int,
    val cols: Int,
    val rows: Int,
    val tileSize: Int,
    val edge: Edge,
    val spawn: Cell,
    val spawnDir: Direction,
    val seed: Long,
    val goalBites: Int,
    val parMs: Long,
    val startMs: Int,
    val minMs: Int,
    val tau: Int,
    val growth: Int,
    val difficulty: Int,
    val walls: Set<Cell>
) {
    val number: Int = id.removePrefix("maze_").toIntOrNull() ?: pack
    val speed: SpeedCurve = SpeedCurve.build(startMs, minMs, tau)

    fun sideIsWrap(side: Side): Boolean {
        if (edge.default == EdgeMode.WRAP) return true
        return side in edge.wrapSides || side.opposite in edge.wrapSides
    }

    fun edgeLabel(): String = when {
        edge.default == EdgeMode.WRAP -> "Borde wrap"
        edge.wrapSides.isNotEmpty() -> "Borde parcial"
        edge.gaps.isNotEmpty() -> "Túneles"
        else -> "Borde sólido"
    }

    fun toLevel(mode: GameMode, relaxed: Boolean = false, forceFixedSeed: Boolean = false): Level {
        val effectiveSpeed = if (relaxed) {
            SpeedCurve.build((startMs * 1.3 + 0.5).toInt(), (minMs * 1.3 + 0.5).toInt(), tau)
        } else {
            speed
        }
        val goal = if (mode == GameMode.META) goalBites else null
        val fixedSeed = if (forceFixedSeed) seed else null
        return Level(
            id = id,
            version = levelVersion,
            cols = cols,
            rows = rows,
            edge = edge,
            walls = walls,
            spawn = spawn,
            spawnDir = spawnDir,
            goalBites = goal,
            fixedSeed = fixedSeed,
            speed = effectiveSpeed
        )
    }
}
