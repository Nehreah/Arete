package com.renea.arete_social.feature.miausnake.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrngTest {
    @Test fun `la semilla cero se sustituye`() {
        assertEquals(GOLDEN, Xorshift64Star.seed(0L))
        assertEquals(42L, Xorshift64Star.seed(42L))
    }

    @Test fun `golden de la secuencia`() {
        val a = Xorshift64Star.next(42L)
        val b = Xorshift64Star.next(a)
        assertEquals(1409286176L, a)
        assertEquals(47299340976525346L, b)
        assertEquals(6255019084209693600L, Xorshift64Star.output(a))
    }

    @Test fun `GOLDEN es 0x9E3779B97F4A7C15`() {
        assertEquals(0x9E3779B97F4A7C15uL.toLong(), GOLDEN)
    }

    @Test fun `spawn golden y sin sitio devuelve null`() {
        val libres = (0 until 10).map { Cell(it, 0) }
        val (c, rng) = spawnFood(libres, 42L)
        assertEquals(Cell(0, 0), c)
        assertEquals(1409286176L, rng)
        val (nada, igual) = spawnFood(emptyList(), 42L)
        assertNull(nada)
        assertEquals(42L, igual)
    }

    @Test fun `el indice siempre cae dentro de la lista`() {
        val libres = (0 until 7).map { Cell(it, it) }
        var rng = Xorshift64Star.seed(99L)
        repeat(500) {
            val (c, r) = spawnFood(libres, rng)
            assertTrue(c in libres)
            rng = r
        }
    }
}
