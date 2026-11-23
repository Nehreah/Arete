package com.renea.arete_social.feature.miausnake.core

import com.renea.arete_social.feature.miausnake.core.bot.BfsBot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GhostTest {
    private val nivel = TestLevels.open(goalBites = 12)

    @Test fun `la sombra reproduce la serpiente en cada tick`() {
        val s = BfsBot.play(nivel, seed = 7L)
        val rep = s.replay()
        val estados = ArrayList<GameState>()
        resimulate(rep, nivel) { estados.add(it) }!!
        val g = buildGhost(rep, nivel)!!
        assertEquals(estados.size - 1, g.ticks)
        val buf = IntArray(nivel.cols * nivel.rows)
        for ((t, st) in estados.withIndex()) {
            val n = g.fill(t, buf)
            assertEquals(st.snake.map { it.y * nivel.cols + it.x }, buf.take(n))
        }
    }

    @Test fun `tickAt sigue el tiempo virtual`() {
        val rep = BfsBot.play(nivel, seed = 7L).replay()
        val g = buildGhost(rep, nivel)!!
        assertEquals(0, g.tickAt(0))
        assertEquals(0, g.tickAt(nivel.speed.tickMs(0) - 1L))
        assertEquals(1, g.tickAt(nivel.speed.tickMs(0).toLong()))
        assertEquals(g.ticks, g.tickAt(Long.MAX_VALUE))
        assertTrue(g.totalMs > 0)
    }

    @Test fun `una repeticion ilegal no da sombra`() {
        val rep = BfsBot.play(nivel, seed = 7L).replay()
        assertNull(buildGhost(rep.copy(inputs = listOf(TurnInput(0, Direction.LEFT))), nivel))
    }
}
