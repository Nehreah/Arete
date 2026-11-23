package com.renea.arete_social.feature.miausnake.ui.input

import com.renea.arete_social.feature.miausnake.core.Direction
import kotlin.math.abs

/**
 * Convierte el arrastre del dedo en giros ENCADENABLES: emite una dirección cada vez que el dedo
 * recorre [threshold] y reinicia el acumulado, sin necesidad de levantarlo. Kotlin puro (JVM-testable).
 */
class SwipeAccumulator(private val threshold: Float) {
    init {
        require(threshold > 0f) { "umbral inválido" }
    }

    private var tx = 0f
    private var ty = 0f

    fun reset() {
        tx = 0f
        ty = 0f
    }

    /** Suma un desplazamiento; devuelve una dirección si se alcanzó el umbral. */
    fun add(dx: Float, dy: Float): Direction? {
        tx += dx
        ty += dy
        if (abs(tx) + abs(ty) < threshold) return null
        val d = if (abs(tx) > abs(ty)) {
            if (tx > 0f) Direction.RIGHT else Direction.LEFT
        } else {
            if (ty > 0f) Direction.DOWN else Direction.UP
        }
        reset()
        return d
    }
}
