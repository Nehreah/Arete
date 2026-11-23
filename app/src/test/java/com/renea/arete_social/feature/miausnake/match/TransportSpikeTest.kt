package com.renea.arete_social.feature.miausnake.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportSpikeTest {
    private val good = List(40) { 100L + it }

    @Test fun `una buena medicion es apta y se abre la puerta`() {
        val r = SpikeReport.evaluate(1_000, 40, good, 3, 3)
        assertTrue(r.summary(), r.passed)
        assertTrue(NearbyGate.isOpen(r, 2_000))
    }

    @Test fun `perdida, latencia o paquetes grandes fallidos la tumban`() {
        assertFalse(SpikeReport.evaluate(0, 40, good.take(30), 3, 3).passed)          // 75 % de entrega
        assertFalse(SpikeReport.evaluate(0, 40, List(40) { 900L }, 3, 3).passed)      // mediana alta
        assertTrue(SpikeReport.evaluate(0, 40, List(40) { if (it < 2) 3000L else 100L }, 3, 3).passed)     // 5 % lentos: p95 aún ok
        assertFalse(SpikeReport.evaluate(0, 40, List(40) { if (it < 4) 3000L else 100L }, 3, 3).passed)    // 10 % lentos: p95 > 2 s
        assertFalse(SpikeReport.evaluate(0, 40, good, 3, 2).passed)                   // un grande no llegó
        assertFalse(SpikeReport.evaluate(0, 0, emptyList(), 3, 0).passed)
    }

    @Test fun `la medicion caduca y no se acepta del futuro`() {
        val r = SpikeReport.evaluate(1_000, 40, good, 3, 3)
        assertFalse(NearbyGate.isOpen(r, 1_000 + NearbyGate.VALID_MS + 1))
        assertFalse(NearbyGate.isOpen(r, 500))
        assertFalse(NearbyGate.isOpen(null, 1_000))
    }

    @Test fun `texto ida y vuelta y basura`() {
        val r = SpikeReport.evaluate(1_000, 40, good, 3, 3)
        val back = SpikeReport.fromText(r.toText())!!
        assertEquals(r.passed, back.passed); assertEquals(r.medianMs, back.medianMs)
        assertNull(SpikeReport.fromText("v1|x")); assertNull(SpikeReport.fromText(null)); assertNull(SpikeReport.fromText("v1|1|2|9|1|1|1|3|1|1"))
    }
}
