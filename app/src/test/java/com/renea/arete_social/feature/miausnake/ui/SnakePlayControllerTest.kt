package com.renea.arete_social.feature.miausnake.ui

import com.renea.arete_social.feature.miausnake.core.Direction
import com.renea.arete_social.feature.miausnake.core.GameStatus
import com.renea.arete_social.feature.miausnake.levels.PlaytestLevels
import com.renea.arete_social.feature.miausnake.levels.PlaytestMap
import com.renea.arete_social.feature.miausnake.ui.play.PlayEvent
import com.renea.arete_social.feature.miausnake.ui.play.PlayPhase
import com.renea.arete_social.feature.miausnake.ui.play.SnakePlayController
import com.renea.arete_social.feature.miausnake.ui.render.Mood
import com.renea.arete_social.feature.miausnake.ui.spike.CellSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnakePlayControllerTest {
    private val ms = 1_000_000L
    private var now = 0L

    private fun controller(map: PlaytestMap = PlaytestMap.WRAP, seed: Long = 7L) =
        SnakePlayController(PlaytestLevels.create(map, CellSpec.PX24), seed, countdownStepNs = 100 * ms)

    /** Avanza n frames de [dtMs] ms. */
    private fun SnakePlayController.frames(n: Int, dtMs: Long = 20) = repeat(n) {
        now += dtMs * ms
        onFrame(now, dtMs * ms)
    }

    private fun SnakePlayController.startPlaying(): SnakePlayController {
        frames(3, 100) // 3 pasos de cuenta atrás de 100 ms
        assertEquals(PlayPhase.Playing, phase)
        return this
    }

    @Test fun `empieza con cuenta atrás 3-2-1 y luego juega`() {
        val c = controller()
        assertEquals(PlayPhase.Countdown, c.phase)
        assertEquals(3, c.countdown)
        c.frames(1, 100)
        assertEquals(2, c.countdown)
        c.frames(1, 100)
        assertEquals(1, c.countdown)
        c.frames(1, 100)
        assertEquals(PlayPhase.Playing, c.phase)
    }

    @Test fun `durante la cuenta atras la serpiente no avanza`() {
        val c = controller()
        c.frames(2, 100)
        assertEquals(0L, c.session.state.tick)
    }

    @Test fun `jugando, los ticks avanzan segun la curva de velocidad`() {
        val c = controller().startPlaying()
        c.frames(20, 20) // 400 ms con tick de 220 ms → 1 tick
        assertEquals(1L, c.session.state.tick)
        assertTrue(c.alpha in 0f..1f)
    }

    @Test fun `pausar congela la partida y los giros se ignoran`() {
        val c = controller().startPlaying()
        c.frames(5, 20)
        val tick = c.session.state.tick
        c.pause()
        assertEquals(PlayPhase.Paused, c.phase)
        assertEquals(GameStatus.Paused, c.session.state.status)
        c.turn(Direction.UP)
        assertTrue(c.session.state.queue.isEmpty())
        c.frames(50, 20)
        assertEquals(tick, c.session.state.tick)
    }

    @Test fun `pausar no reanuda sola y reanudar hace cuenta atras`() {
        val c = controller().startPlaying()
        c.pause()
        c.frames(10, 100)
        assertEquals(PlayPhase.Paused, c.phase)
        c.requestResume()
        assertEquals(PlayPhase.Countdown, c.phase)
        assertEquals(3, c.countdown)
        c.frames(3, 100)
        assertEquals(PlayPhase.Playing, c.phase)
        assertEquals(GameStatus.Running, c.session.state.status)
    }

    @Test fun `pausar durante la cuenta atras tambien funciona`() {
        val c = controller()
        c.frames(1, 100)
        c.pause()
        assertEquals(PlayPhase.Paused, c.phase)
        c.requestResume()
        c.frames(3, 100)
        assertEquals(PlayPhase.Playing, c.phase)
    }

    @Test fun `un delta enorme al volver del segundo plano no adelanta la partida`() {
        val c = controller().startPlaying()
        c.frames(1, 20)
        c.pause()
        c.requestResume()
        c.frames(3, 100)
        val tick = c.session.state.tick
        now += 60_000 * ms
        c.onFrame(now, 60_000 * ms) // un frame con 60 s de retraso
        assertTrue("avanzó ${c.session.state.tick - tick} ticks", c.session.state.tick - tick <= 2)
    }

    @Test fun `snapPose evita el salto hacia atras tras reanudar`() {
        val c = controller()
        assertTrue(c.snapPose)
        c.startPlaying()
        c.frames(15, 20) // primer tick
        assertFalse(c.snapPose)
        c.pause()
        c.requestResume()
        assertTrue(c.snapPose)
        c.frames(3, 100)
        assertTrue(c.snapPose)
        c.frames(15, 20)
        assertFalse(c.snapPose)
    }

    @Test fun `prevTail guarda la cola anterior al ultimo tick`() {
        val c = controller().startPlaying()
        val tailBefore = c.session.state.snake.last()
        c.frames(12, 20)
        assertEquals(1L, c.session.state.tick)
        assertEquals(tailBefore, c.prevTail)
    }

    @Test fun `chocar termina la partida, avisa y pone cara de derrota`() {
        val c = controller(PlaytestMap.SOLID).startPlaying()
        val events = ArrayList<PlayEvent>()
        c.onEvent = { events.add(it) }
        c.turn(Direction.LEFT) // el spawn está en una fila libre: choca con el borde izquierdo
        var guard = 0
        while (c.phase != PlayPhase.Finished && guard++ < 3000) c.frames(1, 20)
        assertEquals(PlayPhase.Finished, c.phase)
        assertEquals(GameStatus.GameOver, c.session.state.status)
        assertTrue(events.contains(PlayEvent.Crash))
        c.frames(1, 20)
        assertEquals(Mood.DEAD, c.mood)
        assertEquals(1f, c.alpha, 0f)
        // Terminada: ya no acepta giros.
        c.turn(Direction.UP)
        assertEquals(Direction.LEFT, c.session.state.direction)
    }

    @Test fun `en pausa solo se redibuja cuando algo cambia`() {
        val c = controller().startPlaying()
        c.pause()
        c.frames(1, 16)
        val f = c.frame
        assertTrue(f > 0L)
        c.frames(5, 16)
        assertEquals(f, c.frame)
    }

    @Test fun `FrameStats del controlador registra cada frame`() {
        val c = controller()
        c.frames(30, 16)
        assertEquals(30, c.frameStats.summary().samples)
    }
}
