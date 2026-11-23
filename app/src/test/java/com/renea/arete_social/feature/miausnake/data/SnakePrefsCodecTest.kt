package com.renea.arete_social.feature.miausnake.data

import com.renea.arete_social.feature.miausnake.levels.GameMode
import com.renea.arete_social.feature.miausnake.levels.ProgressBook
import com.renea.arete_social.feature.miausnake.ui.play.ControlMode
import com.renea.arete_social.feature.miausnake.ui.play.PlaySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnakePrefsCodecTest {
    @Test fun `ida y vuelta`() {
        val p = SnakePrefs(
            PlaySettings(ControlMode.DPAD, leftHanded = true, vibration = false, highContrast = true, relaxed = true),
            GameMode.INFINITE, "maze_17"
        )
        assertEquals(p, SnakePrefsCodec.decode(SnakePrefsCodec.encode(p)))
    }

    @Test fun `sin datos se usan los valores por defecto`() {
        assertEquals(SnakePrefs(), SnakePrefsCodec.decode(null))
        assertEquals(SnakePrefs(), SnakePrefsCodec.decode(""))
        assertEquals(SnakePrefs(), SnakePrefsCodec.decode("basura sin igual"))
    }

    @Test fun `los valores rotos caen al defecto campo a campo`() {
        val p = SnakePrefsCodec.decode("control=TELEPATIA\nleft=1\nmode=Q\nmap=../../etc")
        assertEquals(ControlMode.SWIPE, p.settings.control)
        assertTrue(p.settings.leftHanded)
        assertTrue(p.settings.vibration)
        assertEquals(GameMode.META, p.mode)
        assertNull(p.lastMapId)
    }

    @Test fun `lo de depuracion no se guarda`() {
        val p = SnakePrefs(PlaySettings(interpolate = false, showDebug = true))
        val back = SnakePrefsCodec.decode(SnakePrefsCodec.encode(p))
        assertTrue(back.settings.interpolate)
        assertFalse(back.settings.showDebug)
    }

    @Test fun `el almacen en memoria devuelve lo guardado`() {
        val s = InMemorySnakeStore()
        assertEquals(SnakePrefs(), s.loadPrefs())
        assertEquals(ProgressBook.EMPTY.toText(), s.loadProgress().toText())
        s.savePrefs(SnakePrefs(mode = GameMode.INFINITE))
        assertEquals(GameMode.INFINITE, s.loadPrefs().mode)
    }
}
