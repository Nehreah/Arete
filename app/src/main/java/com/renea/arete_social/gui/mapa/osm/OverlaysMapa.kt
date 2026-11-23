package com.renea.arete_social.gui.mapa.osm

import android.graphics.DashPathEffect
import android.graphics.Paint
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.PI

/**
 * Traduce las listas declarativas ([MarcadorMapa], [LineaMapa]) a overlays de OSMDroid, que son objetos
 * imperativos. Compara con lo ya dibujado y solo añade, quita o reemplaza lo que cambió, así no hay
 * parpadeos ni se reconstruyen decenas de marcadores en cada recomposición.
 *
 * Orden de dibujo: overlays fijos (toques, rotación) → líneas → marcadores → ubicación propia.
 */
internal class ConciliadorOverlays(
    private val mapView: MapView,
    private val iconos: IconosMapa,
    private val densidad: Float
) {
    /** Se actualiza en cada recomposición: los marcadores llaman siempre a la última versión. */
    var alClickMarcador: (String) -> Unit = {}

    private var fijos = 0
    private val marcadores = HashMap<String, Pair<MarcadorMapa, Marker>>()
    private val lineas = HashMap<String, Pair<LineaMapa, Polyline>>()
    private var propia: Pair<PuntoMapa, Marker>? = null
    private var circuloPrecision: Polygon? = null

    /** Marca cuántos overlays fijos hay ya añadidos (las líneas se insertan justo después). */
    fun fijarOverlaysBase() {
        fijos = mapView.overlays.size
    }

    fun conciliar(nuevasLineas: List<LineaMapa>, nuevosMarcadores: List<MarcadorMapa>, ubicacion: PuntoMapa?) {
        var cambio = false
        cambio = conciliarLineas(nuevasLineas) or cambio
        cambio = conciliarMarcadores(nuevosMarcadores) or cambio
        cambio = conciliarUbicacion(ubicacion) or cambio
        if (cambio) mapView.invalidate()
    }

    private fun conciliarLineas(nuevas: List<LineaMapa>): Boolean {
        var cambio = false
        val ids = nuevas.mapTo(HashSet()) { it.id }
        val obsoletas = lineas.keys.filter { it !in ids }
        obsoletas.forEach { lineas.remove(it)?.let { (_, p) -> mapView.overlays.remove(p) } ; cambio = true }
        nuevas.forEach { linea ->
            val actual = lineas[linea.id]
            if (actual?.first == linea) return@forEach
            actual?.let { mapView.overlays.remove(it.second) }
            val poli = crearLinea(linea)
            mapView.overlays.add(fijos.coerceAtMost(mapView.overlays.size), poli)
            lineas[linea.id] = linea to poli
            cambio = true
        }
        return cambio
    }

    private fun conciliarMarcadores(nuevos: List<MarcadorMapa>): Boolean {
        var cambio = false
        val ids = nuevos.mapTo(HashSet()) { it.id }
        val obsoletos = marcadores.keys.filter { it !in ids }
        obsoletos.forEach { marcadores.remove(it)?.let { (_, m) -> mapView.overlays.remove(m) } ; cambio = true }
        nuevos.forEach { dato ->
            val actual = marcadores[dato.id]
            if (actual?.first == dato) return@forEach
            actual?.let { mapView.overlays.remove(it.second) }
            val marker = crearMarcador(dato)
            // Siempre antes de la ubicación propia (si existe) para que el punto azul quede encima.
            val indice = propia?.second?.let { mapView.overlays.indexOf(it) }?.takeIf { it >= 0 }
                ?: mapView.overlays.size
            mapView.overlays.add(indice, marker)
            marcadores[dato.id] = dato to marker
            cambio = true
        }
        return cambio
    }

    private fun conciliarUbicacion(ubicacion: PuntoMapa?): Boolean {
        val actual = propia
        if (actual?.first == ubicacion) return false

        // Quitar el círculo de precisión anterior
        circuloPrecision?.let { mapView.overlays.remove(it) }
        circuloPrecision = null
        // Quitar el marcador anterior
        actual?.let { mapView.overlays.remove(it.second) }
        propia = null

        if (ubicacion != null) {
            // 1. Círculo de precisión (debajo del punto azul)
            val radioM = max(ubicacion.precisionMetros ?: RADIO_PRECISION_POR_DEFECTO, RADIO_PRECISION_MINIMO)
            val circulo = crearCirculoPrecision(ubicacion.latitud, ubicacion.longitud, radioM.toDouble())
            mapView.overlays.add(circulo)
            circuloPrecision = circulo

            // 2. Punto azul encima
            val marker = Marker(mapView).apply {
                position = GeoPoint(ubicacion.latitud, ubicacion.longitud)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = iconos.puntoUbicacion()
                title = "Mi ubicación"
                setInfoWindow(null)
            }
            mapView.overlays.add(marker)
            propia = ubicacion to marker
        }
        return true
    }

    /**
     * Aproxima un círculo geodésico de [radioMetros] metros como un polígono de [PASOS] vértices.
     * Usa la fórmula plana (válida para radios < 500 m) con corrección de longitud por latitud.
     * Color: azul GPS semitransparente, sin borde.
     */
    private fun crearCirculoPrecision(lat: Double, lng: Double, radioMetros: Double): Polygon {
        val gradosPorMetroLat = 1.0 / 111_320.0
        val gradosPorMetroLng = gradosPorMetroLat / cos(lat * PI / 180.0)
        val puntos = ArrayList<GeoPoint>(PASOS + 1)
        for (i in 0..PASOS) {
            val angulo = 2.0 * PI * i / PASOS
            puntos.add(
                GeoPoint(
                    lat + radioMetros * gradosPorMetroLat * Math.sin(angulo),
                    lng + radioMetros * gradosPorMetroLng * Math.cos(angulo)
                )
            )
        }
        return Polygon().apply {
            points = puntos
            fillPaint.apply {
                color = COLOR_CIRCULO_RELLENO
                style = Paint.Style.FILL
            }
            outlinePaint.apply {
                color = COLOR_CIRCULO_BORDE
                strokeWidth = 0f
                style = Paint.Style.STROKE
            }
        }
    }

    private fun crearMarcador(dato: MarcadorMapa): Marker = Marker(mapView).apply {
        position = GeoPoint(dato.posicion.latitud, dato.posicion.longitud)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        icon = iconos.pin(dato.color)
        title = dato.titulo
        snippet = dato.descripcion
        if (dato.abreInfo) {
            // Comportamiento por defecto de OSMDroid: el toque abre el globo con título y descripción.
        } else {
            setInfoWindow(null)
            setOnMarkerClickListener { _, _ ->
                alClickMarcador(dato.id)
                true
            }
        }
    }

    private fun crearLinea(dato: LineaMapa): Polyline = Polyline().apply {
        setPoints(dato.puntos.map { GeoPoint(it.latitud, it.longitud) })
        outlinePaint.apply {
            color = dato.colorArgb
            strokeWidth = dato.anchoDp * densidad
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
            pathEffect = if (dato.punteada) {
                DashPathEffect(floatArrayOf(6f * densidad, 4f * densidad), 0f)
            } else null
        }
    }

    /** Quita todo lo dinámico (al destruir la vista). */
    fun limpiar() {
        val todos: List<Overlay> = lineas.values.map { it.second } + marcadores.values.map { it.second } +
            listOfNotNull(propia?.second, circuloPrecision)
        mapView.overlays.removeAll(todos.toSet())
        lineas.clear(); marcadores.clear(); propia = null; circuloPrecision = null
    }

    private companion object {
        const val PASOS = 48
        const val COLOR_CIRCULO_RELLENO = 0x220A84FF
        const val COLOR_CIRCULO_BORDE = 0x440A84FF
        const val RADIO_PRECISION_POR_DEFECTO = 25f
        const val RADIO_PRECISION_MINIMO = 10f
    }
}
