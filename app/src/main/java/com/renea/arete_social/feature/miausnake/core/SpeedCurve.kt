package com.renea.arete_social.feature.miausnake.core

/** Piso jugable: 3 ticks >= 285 ms (reacción humana + entrada). */
const val MIN_PLAYABLE_TICK_MS = 95

/**
 * Velocidad determinista: tabla de enteros precalculada con [StrictMath] (misma en todos los
 * teléfonos). No depende de FPS ni de reloj de pared.
 */
class SpeedCurve(private val table: IntArray, val minMs: Int) {
    init {
        require(table.isNotEmpty()) { "tabla vacía" }
        require(minMs >= MIN_PLAYABLE_TICK_MS) { "minMs=$minMs por debajo del piso jugable" }
    }

    fun tickMs(eaten: Int): Int = table[minOf(maxOf(eaten, 0), table.lastIndex)]

    /** zonePercent: 140 = cojín, 70 = hierba. Nunca baja del mínimo del pack. */
    fun zoneMs(eaten: Int, zonePercent: Int): Int = maxOf(minMs, tickMs(eaten) * zonePercent / 100)

    fun toList(): List<Int> = table.toList()

    companion object {
        fun build(startMs: Int, minMs: Int, tau: Int, maxN: Int = 200): SpeedCurve {
            require(tau > 0) { "tau debe ser positivo" }
            require(startMs >= minMs) { "startMs debe ser >= minMs" }
            require(maxN >= 0) { "maxN negativo" }
            val table = IntArray(maxN + 1) { n ->
                (minMs + (startMs - minMs) * StrictMath.exp(-n.toDouble() / tau) + 0.5).toInt()
            }
            return SpeedCurve(table, minMs)
        }
    }
}
