package com.renea.arete_social.feature.miausnake.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedCurveTest {
    private val muestras = listOf(0, 1, 2, 4, 8, 12, 20, 40, 100, 200)

    /** Golden: la tabla de enteros debe ser idéntica en todos los teléfonos. */
    @Test fun `golden pack 1 260 a 140`() {
        val c = SpeedCurve.build(260, 140, 18)
        assertEquals(listOf(260, 254, 247, 236, 217, 202, 180, 153, 140, 140), muestras.map { c.tickMs(it) })
    }

    @Test fun `golden pack 4 200 a 95`() {
        val c = SpeedCurve.build(200, 95, 18)
        assertEquals(listOf(200, 194, 189, 179, 162, 149, 130, 106, 95, 95), muestras.map { c.tickMs(it) })
    }

    @Test fun `la tabla nunca sube ni baja del minimo`() {
        val t = SpeedCurve.build(240, 120, 15).toList()
        for (i in 1 until t.size) assertTrue(t[i] <= t[i - 1])
        assertTrue(t.all { it >= 120 })
    }

    @Test fun `fuera de rango se acota`() {
        val c = SpeedCurve.build(200, 95, 18)
        assertEquals(200, c.tickMs(-5))
        assertEquals(95, c.tickMs(10_000))
    }

    @Test fun `zonas usan enteros y no bajan del minimo`() {
        val c = SpeedCurve.build(200, 95, 18)
        assertEquals(280, c.zoneMs(0, 140))
        assertEquals(140, c.zoneMs(0, 70))
        assertEquals(95, c.zoneMs(200, 70))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `el piso jugable se exige`() { SpeedCurve.build(200, 94, 18) }

    @Test(expected = IllegalArgumentException::class)
    fun `tau debe ser positivo`() { SpeedCurve.build(200, 95, 0) }
}
