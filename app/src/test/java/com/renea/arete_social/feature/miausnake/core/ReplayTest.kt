package com.renea.arete_social.feature.miausnake.core

import com.renea.arete_social.feature.miausnake.core.bot.BfsBot
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class ReplayTest {
    private val nivel = TestLevels.open(cols = 20, rows = 28, goalBites = 12)

    @Test fun `la codificacion de repeticiones es reversible`() {
        val turns = listOf(TurnInput(3, Direction.UP), TurnInput(10, Direction.LEFT), TurnInput(400, Direction.DOWN))
        assertEquals(turns, ReplayCodec.decode(ReplayCodec.encode(turns)))
    }

    /** Golden del formato: (3,UP) (10,LEFT) (400,DOWN) -> 0C 1E 99 0C. */
    @Test fun `golden de bytes`() {
        val turns = listOf(TurnInput(3, Direction.UP), TurnInput(10, Direction.LEFT), TurnInput(400, Direction.DOWN))
        assertArrayEquals(byteArrayOf(0x0C, 0x1E, 0x99.toByte(), 0x0C), ReplayCodec.encode(turns))
    }

    @Test fun `vacio se codifica y decodifica`() {
        assertEquals(0, ReplayCodec.encode(emptyList()).size)
        assertEquals(emptyList<TurnInput>(), ReplayCodec.decode(ByteArray(0)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `encode rechaza giros desordenados`() {
        ReplayCodec.encode(listOf(TurnInput(10, Direction.UP), TurnInput(5, Direction.LEFT)))
    }

    @Test fun `decode rechaza varint truncado`() {
        assertNull(ReplayCodec.decode(byteArrayOf(0x80.toByte())))
    }

    @Test fun `decode rechaza varint interminable`() {
        assertNull(ReplayCodec.decode(ByteArray(20) { 0xFF.toByte() }))
    }

    @Test fun `decode rechaza demasiados giros`() {
        assertNull(ReplayCodec.decode(ByteArray(11) { 0x04 }, maxTurns = 10))
        assertNotNull(ReplayCodec.decode(ByteArray(10) { 0x04 }, maxTurns = 10))
    }

    @Test fun `decode rechaza ticks absurdos y tamanos excesivos`() {
        val lejano = ReplayCodec.encode(listOf(TurnInput(ReplayCodec.MAX_TICK + 1, Direction.UP)))
        assertNull(ReplayCodec.decode(lejano))
        assertNull(ReplayCodec.decode(ByteArray(ReplayCodec.MAX_BYTES + 1)))
    }

    @Test fun `datos aleatorios nunca lanzan excepcion`() {
        val rnd = Random(12345)
        repeat(2_000) {
            val b = ByteArray(rnd.nextInt(64)).also { rnd.nextBytes(it) }
            ReplayCodec.decode(b) // null o lista; lo importante es no lanzar
        }
    }

    @Test fun `la repeticion del bot se verifica`() {
        val s = BfsBot.play(nivel, seed = 7L)
        assertEquals(GameStatus.Won, s.state.status)
        assertTrue(verify(s.replay(), nivel, s.state.score))
    }

    @Test fun `una repeticion sobrevive al codec`() {
        val s = BfsBot.play(nivel, seed = 42L)
        val r = s.replay()
        val vuelta = r.copy(inputs = ReplayCodec.decode(ReplayCodec.encode(r.inputs))!!)
        assertTrue(verify(vuelta, nivel, s.state.score))
    }

    @Test fun `puntuacion declarada falsa se rechaza`() {
        val s = BfsBot.play(nivel, seed = 7L)
        assertFalse(verify(s.replay(), nivel, s.state.score + 10))
    }

    @Test fun `version de reglas, de mapa o de mapa distinto se rechazan`() {
        val s = BfsBot.play(nivel, seed = 7L)
        val r = s.replay()
        assertFalse(verify(r.copy(rulesVersion = RULES_VERSION + 1), nivel, s.state.score))
        assertFalse(verify(r.copy(levelVersion = 2), nivel, s.state.score))
        assertFalse(verify(r.copy(levelId = "otro"), nivel, s.state.score))
    }

    @Test fun `giros despues del final se rechazan`() {
        val s = BfsBot.play(nivel, seed = 7L)
        val r = s.replay()
        val extra = r.copy(inputs = r.inputs + TurnInput(s.state.tick + 50, Direction.UP))
        assertFalse(verify(extra, nivel, s.state.score))
    }

    @Test fun `giro opuesto, nulo o desordenado se rechaza`() {
        // La serpiente nace mirando RIGHT en el tick 0.
        val opuesto = Replay(nivel.id, 1, RULES_VERSION, 1L, listOf(TurnInput(0, Direction.LEFT)))
        assertFalse(verify(opuesto, nivel, 0))
        val nulo = Replay(nivel.id, 1, RULES_VERSION, 1L, listOf(TurnInput(0, Direction.RIGHT)))
        assertFalse(verify(nulo, nivel, 0))
        val desordenado = Replay(nivel.id, 1, RULES_VERSION, 1L,
            listOf(TurnInput(5, Direction.UP), TurnInput(3, Direction.LEFT)))
        assertFalse(verify(desordenado, nivel, 0))
    }

    @Test fun `una partida sin terminar no se verifica`() {
        val vacia = Replay(nivel.id, 1, RULES_VERSION, 1L, emptyList())
        assertFalse(verify(vacia, nivel, 0, maxTicks = 100))
    }
}
