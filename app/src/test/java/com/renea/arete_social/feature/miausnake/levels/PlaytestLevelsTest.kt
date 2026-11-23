package com.renea.arete_social.feature.miausnake.levels

import com.renea.arete_social.feature.miausnake.core.Cell
import com.renea.arete_social.feature.miausnake.core.Direction
import com.renea.arete_social.feature.miausnake.core.EdgeMode
import com.renea.arete_social.feature.miausnake.core.GameEngine
import com.renea.arete_social.feature.miausnake.core.GameStatus
import com.renea.arete_social.feature.miausnake.core.Level
import com.renea.arete_social.feature.miausnake.core.bot.BfsBot
import com.renea.arete_social.feature.miausnake.core.step
import com.renea.arete_social.feature.miausnake.core.verify
import com.renea.arete_social.feature.miausnake.ui.spike.CellSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaytestLevelsTest {
    private val all = CellSpec.entries.flatMap { spec -> PlaytestMap.entries.map { it to spec } }

    private fun reachable(lv: Level): Set<Cell> {
        val seen = HashSet<Cell>()
        val stack = ArrayList<Cell>()
        seen.add(lv.spawn); stack.add(lv.spawn)
        while (stack.isNotEmpty()) {
            val c = stack.removeAt(stack.lastIndex)
            for (d in Direction.entries) {
                val n = step(c, d, lv) ?: continue
                if (seen.add(n)) stack.add(n)
            }
        }
        return seen
    }

    @Test fun `hay 2 mapas, uno WRAP y uno SOLID, con ids distintos por rejilla`() {
        assertEquals(2, PlaytestMap.entries.size)
        assertEquals(EdgeMode.WRAP, PlaytestLevels.create(PlaytestMap.WRAP, CellSpec.PX24).edge.default)
        assertEquals(EdgeMode.SOLID, PlaytestLevels.create(PlaytestMap.SOLID, CellSpec.PX24).edge.default)
        assertEquals(4, all.map { (m, s) -> PlaytestLevels.create(m, s).id }.toSet().size)
    }

    @Test fun `las rejillas son las del spike`() {
        for ((m, s) in all) {
            val lv = PlaytestLevels.create(m, s)
            assertEquals(s.cols, lv.cols)
            assertEquals(s.rows, lv.rows)
        }
        assertEquals(20 to 28, CellSpec.PX16.cols to CellSpec.PX16.rows)
        assertEquals(13 to 18, CellSpec.PX24.cols to CellSpec.PX24.rows)
    }

    @Test fun `todo el suelo es alcanzable desde el spawn (tambien por el tunel)`() {
        for ((m, s) in all) {
            val lv = PlaytestLevels.create(m, s)
            assertEquals("${lv.id}: suelo inalcanzable", lv.floorCells.toSet(), reachable(lv))
        }
    }

    @Test fun `el motor arranca en todos los mapas`() {
        for ((m, s) in all) for (seed in 1L..5L) {
            val st = GameEngine.initial(PlaytestLevels.create(m, s), seed)
            assertEquals(GameStatus.Running, st.status)
            assertEquals(3, st.snake.size)
        }
    }

    @Test fun `WRAP envuelve y SOLID mata en el borde`() {
        for (s in CellSpec.entries) {
            val wrap = PlaytestLevels.create(PlaytestMap.WRAP, s)
            assertEquals(Cell(wrap.cols - 1, 5), step(Cell(0, 5), Direction.LEFT, wrap))
            val solid = PlaytestLevels.create(PlaytestMap.SOLID, s)
            assertNull(step(Cell(0, 5), Direction.LEFT, solid))
            assertNull(step(Cell(solid.cols - 1, 5), Direction.RIGHT, solid))
        }
    }

    @Test fun `el tunel del jardin comunica arriba con abajo`() {
        for (s in CellSpec.entries) {
            val lv = PlaytestLevels.create(PlaytestMap.SOLID, s)
            var c = lv.spawn
            var cruzo = false
            repeat(lv.rows + 2) {
                val n = step(c, Direction.UP, lv)
                assertNotNull("${lv.id}: el recorrido recto hacia arriba debe poder cruzar", n)
                if (n!!.y > c.y) cruzo = true
                c = n
            }
            assertTrue("${lv.id}: no atravesó el túnel", cruzo)
        }
    }

    @Test fun `el WRAP tiene meta y el SOLID es infinito`() {
        for (s in CellSpec.entries) {
            assertEquals(PlaytestLevels.WRAP_GOAL_BITES, PlaytestLevels.create(PlaytestMap.WRAP, s).goalBites)
            assertNull(PlaytestLevels.create(PlaytestMap.SOLID, s).goalBites)
        }
    }

    @Test fun `el bot completa el WRAP y la repeticion se verifica`() {
        for (s in CellSpec.entries) for (seed in 1L..3L) {
            val lv = PlaytestLevels.create(PlaytestMap.WRAP, s)
            val session = BfsBot.play(lv, seed)
            assertEquals("${lv.id} seed=$seed", GameStatus.Won, session.state.status)
            assertEquals(PlaytestLevels.WRAP_GOAL_BITES, session.state.foodEaten)
            assertTrue(verify(session.replay(), lv, session.state.score))
        }
    }

    @Test fun `el bot come varias veces en el SOLID sin morir de inmediato`() {
        for (s in CellSpec.entries) for (seed in 1L..3L) {
            val lv = PlaytestLevels.create(PlaytestMap.SOLID, s)
            val session = BfsBot.play(lv, seed, maxTicks = 400)
            assertTrue("${lv.id} seed=$seed comió ${session.state.foodEaten}", session.state.foodEaten >= 3)
        }
    }
}
