package com.renea.arete_social.domain.util

import com.renea.arete_social.domain.model.ZonaRegional
import kotlin.math.abs
import kotlin.math.cos

/**
 * Rectángulo de coordenadas que contiene una zona. Sirve para acotar consultas espaciales a lo que
 * cae dentro de la caja (filtros de latitud/longitud) y afinar después en el teléfono
 * con la distancia real (Haversine).
 */
data class CajaGeografica(
    val latMin: Double,
    val latMax: Double,
    val lngMin: Double,
    val lngMax: Double
)

private const val METROS_POR_GRADO_LATITUD = 111_320.0

/** Caja que contiene un círculo de [radioMetros] alrededor de ([latitud], [longitud]). */
fun cajaAlrededorDe(latitud: Double, longitud: Double, radioMetros: Double): CajaGeografica {
    val deltaLat = radioMetros / METROS_POR_GRADO_LATITUD
    // Los grados de longitud se achican hacia los polos; se evita dividir por ~0 cerca de ellos.
    val cosLat = abs(cos(Math.toRadians(latitud))).coerceAtLeast(0.01)
    val deltaLng = radioMetros / (METROS_POR_GRADO_LATITUD * cosLat)
    return CajaGeografica(
        latMin = (latitud - deltaLat).coerceAtLeast(-90.0),
        latMax = (latitud + deltaLat).coerceAtMost(90.0),
        lngMin = (longitud - deltaLng).coerceAtLeast(-180.0),
        lngMax = (longitud + deltaLng).coerceAtMost(180.0)
    )
}

/** Caja de una zona regional (con un pequeño margen para no perder lo que queda justo en el borde). */
fun ZonaRegional.caja(): CajaGeografica =
    cajaAlrededorDe(region.ubicacion.latitud, region.ubicacion.longitud, radioMaxMetros * 1.02)
