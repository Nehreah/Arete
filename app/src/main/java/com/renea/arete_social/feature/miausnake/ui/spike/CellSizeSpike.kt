package com.renea.arete_social.feature.miausnake.ui.spike

import com.renea.arete_social.feature.miausnake.ui.render.BoardLayout

/**
 * Spike de tamaño de celda (Fase 2): 16 vs 24 px lógicos de referencia sobre un viewport de 320 px.
 * El tamaño decide la REJILLA lógica; el tema programático dibuja a cualquier tamaño de celda.
 *
 *  - PX16 → 20 × 28 celdas (punto de partida del plan).
 *  - PX24 → 13 × 18 celdas (así lo dice el plan si gana 24).
 */
enum class CellSpec(val referencePx: Int, val cols: Int, val rows: Int) {
    PX16(16, 20, 28),
    PX24(24, 13, 18)
}

/** Teléfono de referencia para la tabla del spike. Medidas en dp. */
data class SpikeDevice(val name: String, val widthDp: Float, val heightDp: Float, val density: Float)

data class SpikeResult(
    val device: SpikeDevice,
    val spec: CellSpec,
    val cellPx: Int,
    val cellDp: Float,
    val boardWidthDp: Float,
    val boardHeightDp: Float
)

object CellSizeSpike {
    /** HUD (56 dp) + cruceta (3 × 64 dp + márgenes) que se descuentan de la altura: el caso más estrecho. */
    const val RESERVED_HEIGHT_DP = 256f

    val devices = listOf(
        SpikeDevice("Pequeño 320×568 @2.0", 320f, 568f, 2.0f),
        SpikeDevice("Compacto 360×640 @2.0", 360f, 640f, 2.0f),
        SpikeDevice("Típico 360×800 @2.75", 360f, 800f, 2.75f),
        SpikeDevice("Alto 412×915 @2.625", 412f, 915f, 2.625f),
        SpikeDevice("Grande 450×1000 @3.0", 450f, 1000f, 3.0f)
    )

    fun measure(d: SpikeDevice, spec: CellSpec, reservedHeightDp: Float = RESERVED_HEIGHT_DP): SpikeResult {
        val layout = BoardLayout.compute(
            availWidth = d.widthDp * d.density,
            availHeight = (d.heightDp - reservedHeightDp) * d.density,
            cols = spec.cols, rows = spec.rows
        )
        return SpikeResult(
            device = d, spec = spec, cellPx = layout.cellPx,
            cellDp = layout.cellPx / d.density,
            boardWidthDp = layout.boardWidth / d.density,
            boardHeightDp = layout.boardHeight / d.density
        )
    }

    fun table(): List<SpikeResult> = devices.flatMap { d -> CellSpec.entries.map { measure(d, it) } }
}
