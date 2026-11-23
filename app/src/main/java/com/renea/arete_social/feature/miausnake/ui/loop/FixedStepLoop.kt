package com.renea.arete_social.feature.miausnake.ui.loop

/**
 * Acumulador de paso fijo (Fase 2). Kotlin puro: no conoce Compose ni Android, así que se prueba en JVM.
 *
 * El render corre a la frecuencia de la pantalla; la serpiente solo cambia de celda cuando el
 * acumulador junta [tickNs] nanosegundos. [alpha] (0..1) es el avance hacia el siguiente tick, para
 * la interpolación visual.
 *
 * Protecciones: el delta se recorta a [maxDtNs] (tras una pausa no se encadenan ticks), hay un
 * máximo de [maxTicksPerAdvance] ticks por frame y el bucle se detiene en cuanto [isActive] da
 * false (por ejemplo, al morir en mitad de un avance).
 */
class FixedStepLoop(
    private val tickNs: () -> Long,
    private val onTick: () -> Unit,
    private val isActive: () -> Boolean = { true },
    private val maxDtNs: Long = 250_000_000L,
    private val maxTicksPerAdvance: Int = 4
) {
    private var acc = 0L

    /** Avance 0..1 hacia el siguiente tick. */
    var alpha: Float = 0f
        private set

    fun advance(dtNs: Long) {
        if (dtNs <= 0L) return
        acc += minOf(dtNs, maxDtNs)
        var ticks = 0
        while (isActive()) {
            val t = tickNs()
            if (t <= 0L || acc < t) break
            acc -= t
            onTick()
            if (++ticks >= maxTicksPerAdvance) {
                acc = 0L // no se acumula deuda: mejor perder tiempo que encadenar ticks
                break
            }
        }
        val t = tickNs()
        alpha = if (t > 0L) (acc.toFloat() / t.toFloat()).coerceIn(0f, 1f) else 0f
    }

    /** Vacía el acumulador (al empezar, reanudar o reiniciar). */
    fun reset() {
        acc = 0L
        alpha = 0f
    }
}
