package com.renea.arete_social.feature.miausnake.levels

import com.renea.arete_social.feature.miausnake.core.Cell
import com.renea.arete_social.feature.miausnake.core.Direction
import com.renea.arete_social.feature.miausnake.core.Edge
import com.renea.arete_social.feature.miausnake.core.EdgeMode
import com.renea.arete_social.feature.miausnake.core.Gap
import com.renea.arete_social.feature.miausnake.core.Side
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** Parser seguro de niveles en formato JSON usando kotlinx.serialization (independiente de Android). */
object LevelParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(jsonStr: String): LevelSpec {
        val root = json.parseToJsonElement(jsonStr).jsonObject
        val id = root["id"]!!.jsonPrimitive.content
        val name = root["name"]!!.jsonPrimitive.content
        val pack = root["pack"]!!.jsonPrimitive.int
        val levelVersion = root["levelVersion"]?.jsonPrimitive?.int ?: 1

        val grid = root["grid"]!!.jsonObject
        val cols = grid["columns"]!!.jsonPrimitive.int
        val rows = grid["rows"]!!.jsonPrimitive.int
        val tileSize = root["tileSize"]?.jsonPrimitive?.int ?: 16

        val edgeObj = root["edge"]!!.jsonObject
        val edgeDefault = EdgeMode.valueOf(edgeObj["default"]!!.jsonPrimitive.content)
        val wrapSides = edgeObj["wrapSides"]?.jsonArray?.map {
            Side.valueOf(it.jsonPrimitive.content)
        }?.toSet() ?: emptySet()
        val gaps = edgeObj["gaps"]?.jsonArray?.map {
            val g = it.jsonObject
            Gap(
                Side.valueOf(g["side"]!!.jsonPrimitive.content),
                g["from"]!!.jsonPrimitive.int,
                g["to"]!!.jsonPrimitive.int
            )
        } ?: emptyList()
        val edge = Edge(default = edgeDefault, wrapSides = wrapSides, gaps = gaps)

        val spawnObj = root["spawn"]!!.jsonObject
        val spawn = Cell(spawnObj["x"]!!.jsonPrimitive.int, spawnObj["y"]!!.jsonPrimitive.int)
        val spawnDir = Direction.valueOf(spawnObj["direction"]!!.jsonPrimitive.content)

        val seed = root["seed"]!!.jsonPrimitive.long
        val goalObj = root["goal"]!!.jsonObject
        val goalBites = goalObj["bites"]!!.jsonPrimitive.int
        val parMs = goalObj["parMs"]!!.jsonPrimitive.long

        val speedObj = root["speed"]!!.jsonObject
        val startMs = speedObj["startMs"]!!.jsonPrimitive.int
        val minMs = speedObj["minMs"]!!.jsonPrimitive.int
        val tau = speedObj["tau"]!!.jsonPrimitive.int
        val growth = speedObj["growth"]?.jsonPrimitive?.int ?: 1

        val difficulty = root["difficulty"]!!.jsonPrimitive.int

        val rowsArray = root["rows"]!!.jsonArray
        val walls = HashSet<Cell>()
        for (y in 0 until rowsArray.size) {
            val line = rowsArray[y].jsonPrimitive.content
            for (x in 0 until line.length) {
                if (line[x] == '#') {
                    walls.add(Cell(x, y))
                }
            }
        }

        return LevelSpec(
            id = id,
            name = name,
            pack = pack,
            levelVersion = levelVersion,
            cols = cols,
            rows = rows,
            tileSize = tileSize,
            edge = edge,
            spawn = spawn,
            spawnDir = spawnDir,
            seed = seed,
            goalBites = goalBites,
            parMs = parMs,
            startMs = startMs,
            minMs = minMs,
            tau = tau,
            growth = growth,
            difficulty = difficulty,
            walls = walls
        )
    }
}
