package com.renea.arete_social.feature.mapa.mimis

import kotlin.random.Random

/** Punto en el plano local del paseo, en METROS (x = este, y = norte). Coordenadas ficticias. */
data class Punto(val x: Float, val y: Float)

/**
 * Lo que la simulación necesita saber del mundo donde pasean los Mimis. Así la lógica no depende
 * de Google Maps ni de Compose: la pantalla implementa esto con la proyección del mapa, y las
 * pruebas lo hacen con un plano simple.
 */
interface EntornoPaseo {
    /** Metros por segundo que recorre un Mimi "normal" con el zoom actual. */
    val velocidadBase: Float

    /** Giro del mapa en radianes (0 = norte arriba). Sirve para saber hacia qué lado mira el gato EN PANTALLA. */
    val bearingRad: Float

    /** Centro de la zona preferida de paseo. */
    val centro: Punto

    /** Distancia mínima deseada entre Mimis al aparecer. */
    val separacionMinima: Float

    /** ¿Está el punto dentro de la zona preferida de paseo (la parte central de la pantalla)? */
    fun estaEnZona(x: Float, y: Float): Boolean

    /** ¿Se ve el punto en pantalla (con un pequeño margen)? Si no, el Mimi puede reubicarse sin que nadie lo note. */
    fun estaVisible(x: Float, y: Float): Boolean

    /** Un punto al azar dentro de la zona preferida, o null si el mapa aún no está listo. */
    fun puntoEnZona(random: Random): Punto?
}
