package com.renea.arete_social.gui.mapa

import androidx.compose.ui.geometry.Offset
import com.renea.arete_social.feature.mapa.mimis.EntornoPaseo
import com.renea.arete_social.feature.mapa.mimis.Punto
import com.renea.arete_social.gui.mapa.osm.AngulosMapa
import com.renea.arete_social.gui.mapa.osm.ProyeccionMapa
import com.renea.arete_social.gui.mapa.osm.PuntoMapa
import kotlin.math.cos
import kotlin.random.Random

/**
 * Puente entre la simulación de Mimis y el mapa.
 *
 * Traduce entre el plano local de la simulación (metros alrededor de un origen ficticio) y las
 * coordenadas del mapa, usando la [ProyeccionMapa] de la cámara. Así cada Mimi queda anclado al suelo
 * y se mueve junto con el mapa cuando el usuario arrastra, gira o hace zoom.
 *
 * El origen es el punto donde estaba la cámara al empezar; no es la ubicación de nadie.
 */
class EntornoPaseoMapa : EntornoPaseo {

    private var proyeccion: ProyeccionMapa? = null
    private var latOrigen = Double.NaN
    private var lngOrigen = 0.0
    private var metrosPorGradoLng = METROS_POR_GRADO_LAT
    private var anchoPx = 0f
    private var altoPx = 0f

    override var velocidadBase: Float = 0f
        private set
    override var bearingRad: Float = 0f
        private set
    override var centro: Punto = Punto(0f, 0f)
        private set
    override var separacionMinima: Float = 0f
        private set

    /**
     * Pone al día la cámara a partir de su proyección (el centro y el giro salen de ella).
     * Devuelve false si el mapa todavía no puede proyectar.
     */
    fun actualizar(
        nuevaProyeccion: ProyeccionMapa,
        anchoPantallaPx: Int,
        altoPantallaPx: Int,
        densidad: Float
    ): Boolean {
        if (anchoPantallaPx <= 0 || altoPantallaPx <= 0) return false
        proyeccion = nuevaProyeccion
        anchoPx = anchoPantallaPx.toFloat()
        altoPx = altoPantallaPx.toFloat()

        if (latOrigen.isNaN()) {
            latOrigen = nuevaProyeccion.latCentro
            lngOrigen = nuevaProyeccion.lngCentro
            metrosPorGradoLng = METROS_POR_GRADO_LAT * cos(Math.toRadians(latOrigen))
        }
        // La proyección guarda la orientación de OSMDroid (= -bearing); la simulación espera el bearing.
        val bearing = AngulosMapa.bearingDesdeOrientacion(nuevaProyeccion.orientacionGrados)
        bearingRad = Math.toRadians(bearing.toDouble()).toFloat()

        // Metros por píxel en el centro de la pantalla, para que el paso se vea igual a cualquier zoom.
        val metrosPorPx = nuevaProyeccion.metrosPorPixel.toFloat()
        if (metrosPorPx <= 0f) return false

        velocidadBase = VELOCIDAD_DP_POR_SEGUNDO * densidad * metrosPorPx
        separacionMinima = SEPARACION_DP * densidad * metrosPorPx
        centro = aMetros(nuevaProyeccion.aGeo(anchoPx / 2f, altoPx / 2f))
        return true
    }

    /** Dónde cae en pantalla (px) un punto del plano local. Null si el mapa aún no está listo. */
    fun aPantalla(proy: ProyeccionMapa, x: Float, y: Float): Offset? {
        if (latOrigen.isNaN()) return null
        val geo = aGeo(x, y)
        val p = proy.aPantalla(geo.latitud, geo.longitud)
        return Offset(p.x, p.y)
    }

    override fun estaEnZona(x: Float, y: Float): Boolean =
        dentro(x, y, ZONA_MIN_X, ZONA_MAX_X, ZONA_MIN_Y, ZONA_MAX_Y)

    override fun estaVisible(x: Float, y: Float): Boolean =
        dentro(x, y, -MARGEN_VISIBLE, 1f + MARGEN_VISIBLE, -MARGEN_VISIBLE, 1f + MARGEN_VISIBLE)

    override fun puntoEnZona(random: Random): Punto? {
        val proy = proyeccion ?: return null
        if (latOrigen.isNaN()) return null
        val sx = anchoPx * (ZONA_MIN_X + random.nextFloat() * (ZONA_MAX_X - ZONA_MIN_X))
        val sy = altoPx * (ZONA_MIN_Y + random.nextFloat() * (ZONA_MAX_Y - ZONA_MIN_Y))
        return aMetros(proy.aGeo(sx, sy))
    }

    private fun dentro(x: Float, y: Float, minX: Float, maxX: Float, minY: Float, maxY: Float): Boolean {
        val proy = proyeccion ?: return false
        val p = aPantalla(proy, x, y) ?: return false
        val fx = p.x / anchoPx
        val fy = p.y / altoPx
        return fx in minX..maxX && fy in minY..maxY
    }

    private fun aMetros(l: PuntoMapa): Punto = Punto(
        ((l.longitud - lngOrigen) * metrosPorGradoLng).toFloat(),
        ((l.latitud - latOrigen) * METROS_POR_GRADO_LAT).toFloat()
    )

    private fun aGeo(x: Float, y: Float): PuntoMapa = PuntoMapa(
        latOrigen + y / METROS_POR_GRADO_LAT,
        lngOrigen + x / metrosPorGradoLng
    )

    private companion object {
        const val METROS_POR_GRADO_LAT = 111_320.0

        // Zona preferida (fracción de la pantalla): deja libres los botones y la barra inferior.
        const val ZONA_MIN_X = 0.07f
        const val ZONA_MAX_X = 0.93f
        const val ZONA_MIN_Y = 0.20f
        const val ZONA_MAX_Y = 0.80f

        // Un Mimi que se aleja más de esto fuera de la pantalla se reubica.
        const val MARGEN_VISIBLE = 0.10f

        const val VELOCIDAD_DP_POR_SEGUNDO = 30f
        const val SEPARACION_DP = 40f
    }
}
