package com.renea.arete_social.feature.miausnake.core

import com.renea.arete_social.feature.miausnake.core.bot.BfsBot
import com.renea.arete_social.feature.miausnake.levels.GameMode
import com.renea.arete_social.feature.miausnake.levels.MapAssets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResimulateTest {
    private val level = MapAssets.catalog.byId("maze_02")!!.toLevel(GameMode.META)

    @Test fun `re-simular la repeticion del bot da su estado final`() {
        val session = BfsBot.play(level, seed = 0L)
        val replay = session.replay()
        val fin = resimulate(replay, level)
        assertNotNull(fin)
        assertEquals(session.state.status, fin!!.status)
        assertEquals(session.state.score, fin.score)
        assertEquals(session.state.elapsedMs, fin.elapsedMs)
        assertTrue(verify(replay, level, fin.score))
    }

    @Test fun `una repeticion de otro mapa o version no se re-simula`() {
        val replay = BfsBot.play(level, seed = 0L).replay()
        val otro = MapAssets.catalog.byId("maze_03")!!.toLevel(GameMode.META)
        assertNull(resimulate(replay, otro))
        assertNull(resimulate(replay.copy(levelVersion = replay.levelVersion + 1), level))
    }

    @Test fun `un giro ilegal invalida la repeticion`() {
        val replay = BfsBot.play(level, seed = 0L).replay()
        val roto = replay.copy(inputs = listOf(TurnInput(0L, level.spawnDir)) + replay.inputs)
        assertNull(resimulate(roto, level))
    }
}
