package com.renea.arete_social.gui.mapa.osm

import kotlin.math.cos
import kotlin.math.sin

/** Punto en píxeles dentro del área del mapa (origen arriba a la izquierda). */
data class PuntoPantalla(val x: Float, val y: Float)

/** Conversiones de ángulo entre el `bearing` (estilo Google) y la `mapOrientation` de OSMDroid. */
object AngulosMapa {
    /** `mapOrientation = -bearing`: se centraliza aquí para no repetir el signo por el código. */
    fun orientacionDesdeBearing(bearing: Float): Float = -bearing

    fun bearingDesdeOrientacion(orientacion: Float): Float = normalizar360(-orientacion)

    /** Lleva cualquier ángulo al rango [0, 360). */
    fun normalizar360(grados: Float): Float {
        val r = grados % 360f
        return if (r < 0f) r + 360f else r
    }

    /** Giro más corto (con signo, en (-180, 180]) para pasar de [desde] a [hasta]. */
    fun diferenciaMasCorta(desde: Float, hasta: Float): Float {
        var d = (hasta - desde) % 360f
        if (d > 180f) d -= 360f
        if (d <= -180f) d += 360f
        return d
    }
}

/**
 * Proyección geográfica ↔ píxeles de pantalla, en Kotlin puro (sin Android ni OSMDroid).
 *
 * Es inmutable: [EstadoCamaraOsm] crea una nueva solo cuando la cámara cambia y la deja en caché, así
 * leerla en cada cuadro de la capa de Mimis no cuesta nada (no se pide `mapView.projection`, que
 * crea un objeto nuevo en cada llamada). Se usa una aproximación local (equirectangular alrededor del
 * centro): en el área visible de un mapa de calles el error es despreciable.
 *
 * Convenciones: el centro del mapa está en el centro del área; [orientacionGrados] es la
 * `mapOrientation` de OSMDroid (giro horario del mapa; = -bearing). Con orientación 0 el norte queda arriba.
 */
class ProyeccionMapa(
    val latCentro: Double,
    val lngCentro: Double,
    /** Píxeles por grado de longitud (= tamaño del mundo en px / 360). Solo depende del zoom. */
    val pxPorGradoLng: Double,
    val orientacionGrados: Float,
    val anchoPx: Int,
    val altoPx: Int
) {
    private val pxPorGradoLat: Double = pxPorGradoLng / cos(Math.toRadians(latCentro))
    private val coseno = cos(Math.toRadians(orientacionGrados.toDouble()))
    private val seno = sin(Math.toRadians(orientacionGrados.toDouble()))

    /** Metros por píxel en el centro (útil para velocidades y separaciones independientes del zoom). */
    val metrosPorPixel: Double = METROS_POR_GRADO_LAT / pxPorGradoLat

    fun aPantalla(latitud: Double, longitud: Double): PuntoPantalla {
        val dx = diferenciaLongitud(longitud - lngCentro) * pxPorGradoLng
        val dy = -(latitud - latCentro) * pxPorGradoLat          // el norte es "arriba" (y negativa)
        val x = anchoPx / 2.0 + dx * coseno - dy * seno
        val y = altoPx / 2.0 + dx * seno + dy * coseno
        return PuntoPantalla(x.toFloat(), y.toFloat())
    }

    fun aGeo(xPx: Float, yPx: Float): PuntoMapa {
        val rx = xPx - anchoPx / 2.0
        val ry = yPx - altoPx / 2.0
        val dx = rx * coseno + ry * seno
        val dy = -rx * seno + ry * coseno
        return PuntoMapa(
            latitud = latCentro - dy / pxPorGradoLat,
            longitud = lngCentro + dx / pxPorGradoLng
        )
    }

    private fun diferenciaLongitud(d: Double): Double = when {
        d > 180.0 -> d - 360.0
        d < -180.0 -> d + 360.0
        else -> d
    }

    companion object {
        const val METROS_POR_GRADO_LAT = 111_320.0
    }
}
