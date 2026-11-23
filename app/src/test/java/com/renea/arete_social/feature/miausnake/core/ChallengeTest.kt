package com.renea.arete_social.feature.miausnake.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeTest {
    @Test fun `el codigo ida y vuelta`() {
        val c = Challenge.parse("MIAU1-17-I-7F3A9C")
        assertEquals(Challenge(1, 17, 'I', 0x7F3A9CL), c)
        assertEquals("MIAU1-17-I-7F3A9C", c!!.toCode())
    }

    @Test fun `la semilla se rellena a 6 digitos y admite espacios alrededor`() {
        assertEquals("MIAU1-3-M-000ABC", Challenge(1, 3, 'M', 0xABCL).toCode())
        assertEquals(Challenge(1, 3, 'M', 0xABCL), Challenge.parse("  MIAU1-3-M-000ABC \n"))
    }

    @Test fun `codigos corruptos se rechazan`() {
        listOf(
            "", "MIAU", "MIAU1-0-I-7F3A9C", "MIAU1-21-I-7F3A9C", "MIAU1-5-X-7F3A9C",
            "MIAU1-5-I-7f3a9c", "MIAU1-5-I-7F3A9", "MIAU99999999999-5-I-7F3A9C", "miau1-5-I-7F3A9C"
        ).forEach { assertNull(it, Challenge.parse(it)) }
    }

    @Test fun `el reto diario es estable durante el dia UTC y cambia al siguiente`() {
        val base = 1_800_000_000_000L
        val a = Challenge.daily(base)
        assertEquals(10, a.mapNo)
        assertEquals(3798655L, a.seed)
        val manana = Challenge.daily(base + 86_399_999L)
        assertEquals(manana, Challenge.daily(base + 86_400_000L))
        assertNotEquals(a, manana)
        assertTrue(manana.mapNo in 1..20)
        assertEquals('I', a.mode)
    }
}
