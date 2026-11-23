package com.renea.arete_social.domain.util

import com.renea.arete_social.domain.model.Ubicacion
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val RADIO_TIERRA_METROS = 6_371_000.0

/**
 * Vive en el dominio porque es lógica pura sin dependencias externas,
 * utilizada por el radar de proximidad y por la malla local.
 */
fun distanciaMetros(origen: Ubicacion, destino: Ubicacion): Double {
    val dLat = Math.toRadians(destino.latitud - origen.latitud)
    val dLon = Math.toRadians(destino.longitud - origen.longitud)
    val lat1 = Math.toRadians(origen.latitud)
    val lat2 = Math.toRadians(destino.latitud)

    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return RADIO_TIERRA_METROS * c
}
