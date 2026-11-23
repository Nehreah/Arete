package com.renea.arete_social.feature.miausnake.ui

import com.renea.arete_social.feature.miausnake.ui.render.BoardLayout
import com.renea.arete_social.feature.miausnake.ui.spike.CellSizeSpike
import com.renea.arete_social.feature.miausnake.ui.spike.CellSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardLayoutTest {
    @Test fun `la celda es entera y par y el tablero cabe`() {
        val l = BoardLayout.compute(1080f, 1500f, cols = 13, rows = 18)
        assertEquals(0, l.cellPx % 2)
        assertTrue(l.boardWidth <= 1080f && l.boardHeight <= 1500f)
        assertEquals(82, l.cellPx) // min(1080/13, 1500/18) = 83,3 → 83 → par 82
    }

    @Test fun `el tablero queda centrado con origen entero`() {
        val l = BoardLayout.compute(1080f, 1500f, cols = 13, rows = 18)
        assertEquals(0f, l.originX % 1f, 0f)
        assertEquals(0f, l.originY % 1f, 0f)
        assertEquals((1080f - l.boardWidth) / 2f, l.originX, 1f)
        assertEquals((1500f - l.boardHeight) / 2f, l.originY, 1f)
    }

    @Test fun `la altura manda cuando el area es estrecha en vertical`() {
        val l = BoardLayout.compute(2000f, 560f, cols = 20, rows = 28)
        assertEquals(20, l.cellPx) // 560 / 28 = 20
    }

    @Test fun `un area diminuta no da celdas de menos de 2 px ni origen negativo`() {
        val l = BoardLayout.compute(5f, 5f, cols = 20, rows = 28)
        assertEquals(BoardLayout.MIN_CELL_PX, l.cellPx)
        assertTrue(l.originX >= 0f && l.originY >= 0f)
    }

    @Test fun `cellRect cae sobre la rejilla sin posiciones subpixel`() {
        val l = BoardLayout.compute(1080f, 1500f, cols = 13, rows = 18)
        val r = l.cellRect(com.renea.arete_social.feature.miausnake.core.Cell(3, 4))
        assertEquals(l.originX + 3 * l.cellPx, r.left, 0f)
        assertEquals(l.cellPx.toFloat(), r.width, 0f)
        assertEquals(0f, r.left % 1f, 0f)
    }

    // --- Spike 16 vs 24 ---
    @Test fun `spike - en un telefono tipico 24 px da una celda legible y 16 px una cabeza de unos 17 dp`() {
        val typical = CellSizeSpike.devices.first { it.name.startsWith("Típico") }
        val s16 = CellSizeSpike.measure(typical, CellSpec.PX16)
        val s24 = CellSizeSpike.measure(typical, CellSpec.PX24)
        assertEquals(17.5f, s16.cellDp, 0.5f) // «a 3× la cabeza de 16 px mide ≈17 dp»
        assertTrue("24 px debe ser claramente mayor", s24.cellDp > s16.cellDp * 1.4f)
        assertTrue(s24.boardWidthDp <= typical.widthDp && s16.boardWidthDp <= typical.widthDp)
    }

    @Test fun `spike - ninguna combinacion de la tabla se sale de la pantalla`() {
        for (r in CellSizeSpike.table()) {
            assertTrue(r.device.name, r.boardWidthDp <= r.device.widthDp + 0.01f)
            assertTrue(r.device.name, r.boardHeightDp <= r.device.heightDp - CellSizeSpike.RESERVED_HEIGHT_DP + 0.01f)
        }
    }
}
