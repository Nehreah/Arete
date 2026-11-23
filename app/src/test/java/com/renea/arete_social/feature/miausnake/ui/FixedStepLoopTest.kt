package com.renea.arete_social.feature.miausnake.ui

import com.renea.arete_social.feature.miausnake.ui.loop.FixedStepLoop
import com.renea.arete_social.feature.miausnake.ui.loop.FrameStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixedStepLoopTest {
    private val ms = 1_000_000L

    @Test fun `no hay tick hasta juntar el tiempo y alpha avanza`() {
        var ticks = 0
        val loop = FixedStepLoop({ 100 * ms }, { ticks++ })
        loop.advance(40 * ms)
        assertEquals(0, ticks)
        assertEquals(0.4f, loop.alpha, 0.001f)
        loop.advance(70 * ms)
        assertEquals(1, ticks)
        assertEquals(0.1f, loop.alpha, 0.001f)
    }

    @Test fun `el tiempo sobrante se conserva entre frames`() {
        var ticks = 0
        val loop = FixedStepLoop({ 100 * ms }, { ticks++ })
        repeat(10) { loop.advance(33 * ms) } // 330 ms
        assertEquals(3, ticks)
    }

    @Test fun `un delta enorme tras una pausa no encadena ticks`() {
        var ticks = 0
        val loop = FixedStepLoop({ 100 * ms }, { ticks++ })
        loop.advance(10_000 * ms) // recortado a 250 ms
        assertEquals(2, ticks)
    }

    @Test fun `hay un maximo de ticks por avance y no se arrastra deuda`() {
        var ticks = 0
        val loop = FixedStepLoop({ 10 * ms }, { ticks++ }, maxDtNs = 1_000 * ms, maxTicksPerAdvance = 4)
        loop.advance(500 * ms)
        assertEquals(4, ticks)
        loop.advance(5 * ms)
        assertEquals(4, ticks)
    }

    @Test fun `se detiene en cuanto deja de estar activo`() {
        var ticks = 0
        var active = true
        val loop = FixedStepLoop({ 10 * ms }, { ticks++; if (ticks == 2) active = false }, { active })
        loop.advance(200 * ms)
        assertEquals(2, ticks)
    }

    @Test fun `la duracion del tick se vuelve a leer en cada iteracion`() {
        var tick = 100L
        var ticks = 0
        val loop = FixedStepLoop({ tick * ms }, { ticks++; tick = 50 })
        loop.advance(200 * ms) // 100 + 50 + 50
        assertEquals(3, ticks)
    }

    @Test fun `reset vacia el acumulador`() {
        var ticks = 0
        val loop = FixedStepLoop({ 100 * ms }, { ticks++ })
        loop.advance(90 * ms)
        loop.reset()
        loop.advance(20 * ms)
        assertEquals(0, ticks)
        assertEquals(0.2f, loop.alpha, 0.001f)
    }

    @Test fun `delta no positivo se ignora`() {
        var ticks = 0
        val loop = FixedStepLoop({ 100 * ms }, { ticks++ })
        loop.advance(0)
        loop.advance(-5)
        assertEquals(0, ticks)
    }

    // --- FrameStats ---
    @Test fun `FrameStats calcula fps medios y marca 60 fps estables`() {
        val st = FrameStats()
        repeat(120) { st.record(16_666_667L) }
        val s = st.summary()
        assertEquals(60f, s.avgFps, 0.5f)
        assertEquals(0, s.jankFrames)
        assertTrue(s.isStable60)
    }

    @Test fun `FrameStats detecta tirones y no da por estable un juego lento`() {
        val st = FrameStats()
        repeat(100) { st.record(16_666_667L) }
        repeat(20) { st.record(60 * ms) }
        val s = st.summary()
        assertEquals(20, s.jankFrames)
        assertFalse(s.isStable60)
        assertEquals(60f, s.worstMs, 0.01f)
    }

    @Test fun `FrameStats es un anillo acotado y no estable sin muestras suficientes`() {
        val st = FrameStats(capacity = 10)
        repeat(100) { st.record(16_666_667L) }
        assertEquals(10, st.summary().samples)
        assertFalse(st.summary().isStable60)
        st.clear()
        assertEquals(0, st.summary().samples)
    }
}
