package com.renea.arete_social.feature.miausnake.ui.loop

/** Resumen de las últimas muestras de frame. */
data class FrameSummary(
    val samples: Int,
    val avgFps: Float,
    val avgMs: Float,
    val worstMs: Float,
    /** Frames más lentos que el umbral de tirón. */
    val jankFrames: Int
) {
    /** Criterio del gate de la Fase 2: media >= 57 fps y menos del 2 % de tirones. */
    val isStable60: Boolean
        get() = samples >= 60 && avgFps >= 57f && jankFrames * 50 <= samples
}

/**
 * Medidor de frames para el spike en teléfono real (gate: «60 fps estables»).
 * Anillo de tamaño fijo: no genera basura por frame. Kotlin puro.
 */
class FrameStats(private val capacity: Int = 240, private val jankNs: Long = 25_000_000L) {
    private val ring = LongArray(capacity)
    private var count = 0
    private var next = 0

    fun record(dtNs: Long) {
        if (dtNs <= 0L) return
        ring[next] = dtNs
        next = (next + 1) % capacity
        if (count < capacity) count++
    }

    fun clear() {
        count = 0
        next = 0
    }

    fun summary(): FrameSummary {
        if (count == 0) return FrameSummary(0, 0f, 0f, 0f, 0)
        var sum = 0L
        var worst = 0L
        var jank = 0
        for (i in 0 until count) {
            val v = ring[i]
            sum += v
            if (v > worst) worst = v
            if (v > jankNs) jank++
        }
        val avgNs = sum.toDouble() / count
        return FrameSummary(
            samples = count,
            avgFps = (1_000_000_000.0 / avgNs).toFloat(),
            avgMs = (avgNs / 1_000_000.0).toFloat(),
            worstMs = (worst / 1_000_000.0).toFloat(),
            jankFrames = jank
        )
    }
}
