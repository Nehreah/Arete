package com.renea.arete_social.feature.miausnake.core

import com.renea.arete_social.feature.miausnake.core.bot.BfsBot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BfsBotTest {
    @Test fun `completa un mapa vacio con wrap hasta la meta`() {
        val lv = TestLevels.open(cols = 20, rows = 28, goalBites = 12)
        for (seed in listOf(1L, 7L, 42L, 12345L)) {
            val s = BfsBot.play(lv, seed)
            assertEquals("seed $seed", GameStatus.Won, s.state.status)
            assertEquals(12, s.state.foodEaten)
        }
    }

    @Test fun `completa un mapa vacio con borde solido`() {
        val lv = TestLevels.open(cols = 20, rows = 28, edge = EdgeMode.SOLID, goalBites = 12)
        for (seed in listOf(1L, 7L, 42L)) {
            assertEquals("seed $seed", GameStatus.Won, BfsBot.play(lv, seed).state.status)
        }
    }

    @Test fun `completa un tablero pequeno y mas exigente`() {
        val lv = TestLevels.open(cols = 8, rows = 8, goalBites = 10)
        for (seed in listOf(1L, 7L, 42L, 12345L)) {
            assertEquals("seed $seed", GameStatus.Won, BfsBot.play(lv, seed).state.status)
        }
    }

    /** Golden de repeticion: dos ejecuciones identicas y con los numeros fijados. */
    @Test fun `la repeticion es identica en dos ejecuciones y coincide con el golden`() {
        val lv = TestLevels.open(cols = 20, rows = 28, goalBites = 12)
        val a = BfsBot.play(lv, 1L)
        val b = BfsBot.play(lv, 1L)
        assertEquals(a.replay(), b.replay())
        assertEquals(a.state, b.state)
        assertEquals(156L, a.state.tick)
        assertEquals(24, a.replay().inputs.size)
        assertEquals(120, a.state.score)
        val c = BfsBot.play(lv, 7L)
        assertEquals(138L, c.state.tick)
        assertEquals(26, c.replay().inputs.size)
    }

    @Test fun `el tiempo virtual coincide con la suma de ticks`() {
        val lv = TestLevels.open(cols = 20, rows = 28, goalBites = 12)
        val s = BfsBot.play(lv, 1L).state
        assertTrue(s.elapsedMs >= s.tick * 95L)
        assertTrue(s.elapsedMs <= s.tick * 200L)
    }
}
