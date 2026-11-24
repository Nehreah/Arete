package com.renea.arete_social.gui.mapa.osm

import android.graphics.Point
import android.view.MotionEvent
import android.view.View
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Cámara del mapa observable desde Compose (sustituye a `CameraPositionState`).
 *
 * Lo que expone es lo que hoy usa el código: posición ([centro], [zoom], [bearing]), si se mueve
 * ([enMovimiento], [inicioPorGesto]), animaciones ([animar], [mover]) y la proyección en caché
 * ([proyeccion]). No hay inclinación (tilt): OSMDroid es un mapa 2D.
 *
 * Funciona aunque todavía no exista la vista: lo pedido se guarda y se aplica al adjuntarla.
 * Todo debe llamarse desde el hilo principal.
 */
@Stable
class EstadoCamaraOsm(
    centroInicial: PuntoMapa,
    zoomInicial: Double = ZOOM_POR_DEFECTO,
    bearingInicial: Float = 0f
) {
    private var mapView: MapView? = null

    var centro: PuntoMapa by mutableStateOf(centroInicial)
        private set
    var zoom: Double by mutableDoubleStateOf(zoomInicial)
        private set

    /** Hacia dónde mira la cámara, en grados [0, 360) y con el sentido del `bearing` de Google. */
    var bearing: Float by mutableFloatStateOf(AngulosMapa.normalizar360(bearingInicial))
        private set

    /** True mientras el usuario arrastra/pellizca/gira o corre una animación de [animar]. */
    var enMovimiento: Boolean by mutableStateOf(false)
        private set

    /** True cuando el movimiento lo inició el usuario (equivale a `CameraMoveStartedReason.GESTURE`). */
    var inicioPorGesto: Boolean by mutableStateOf(false)
        private set

    /**
     * Sube en cada cambio de cámara. La capa de Mimis lo lee dentro de `graphicsLayer {}` para
     * reposicionarse sin recomponer (si se omite, los Mimis se quedan pegados a la pantalla).
     */
    var version: Long by mutableLongStateOf(0L)
        private set

    /** Proyección vigente (en caché). Null hasta que el mapa tenga tamaño. */
    var proyeccion: ProyeccionMapa? by mutableStateOf(null)
        private set

    private var gestoActivo = false
    private var downX = 0f
    private var downY = 0f
    private var pxPorGradoLng = Double.NaN
    private var zoomCalibrado = Double.NaN

    /** Coloca la cámara de inmediato. Los parámetros nulos conservan el valor actual. */
    fun mover(punto: PuntoMapa, zoom: Double? = null, bearing: Float? = null) {
        val z = zoom ?: this.zoom
        val b = AngulosMapa.normalizar360(bearing ?: this.bearing)
        centro = punto
        this.zoom = z
        this.bearing = b
        mapView?.let { aplicar(it, punto, z, b) }
    }

    /**
     * Anima centro, zoom y bearing a la vez (equivale a `animate(newCameraPosition(...))`).
     * Si el usuario toca el mapa a mitad, la animación se detiene, como en Google Maps. También se
     * detiene si la corrutina se cancela. Sin vista adjunta equivale a [mover].
     */
    suspend fun animar(
        destino: PuntoMapa,
        zoom: Double? = null,
        bearing: Float? = null,
        duracionMs: Long = 900L
    ) {
        val vista = mapView
        if (vista == null || duracionMs <= 0L) {
            mover(destino, zoom, bearing)
            return
        }
        val c0 = centro
        val z0 = this.zoom
        val b0 = this.bearing
        val z1 = zoom ?: z0
        val giro = if (bearing == null) 0f else AngulosMapa.diferenciaMasCorta(b0, bearing)

        enMovimiento = true
        inicioPorGesto = false
        try {
            val inicio = withFrameNanos { it }
            while (true) {
                val ahora = withFrameNanos { it }
                if (gestoActivo) return
                val t = ((ahora - inicio) / 1_000_000.0 / duracionMs).coerceIn(0.0, 1.0)
                val e = suavizar(t)
                aplicar(
                    vista,
                    PuntoMapa(
                        c0.latitud + (destino.latitud - c0.latitud) * e,
                        c0.longitud + (destino.longitud - c0.longitud) * e
                    ),
                    z0 + (z1 - z0) * e,
                    AngulosMapa.normalizar360(b0 + giro * e.toFloat())
                )
                if (t >= 1.0) return
            }
        } finally {
            if (!gestoActivo) enMovimiento = false
        }
    }

    suspend fun zoomIn(paso: Double = 1.0, duracionMs: Long = 250L) {
        val nuevoZoom = (zoom + paso).coerceIn(ZOOM_MINIMO, ZOOM_MAXIMO)
        animar(destino = centro, zoom = nuevoZoom, bearing = bearing, duracionMs = duracionMs)
    }

    suspend fun zoomOut(paso: Double = 1.0, duracionMs: Long = 250L) {
        val nuevoZoom = (zoom - paso).coerceIn(ZOOM_MINIMO, ZOOM_MAXIMO)
        animar(destino = centro, zoom = nuevoZoom, bearing = bearing, duracionMs = duracionMs)
    }

    suspend fun orientarAlNorte(duracionMs: Long = 350L) {
        animar(destino = centro, zoom = zoom, bearing = 0f, duracionMs = duracionMs)
    }

    // ---- uso interno (lo llama OsmMapa) ----

    internal fun adjuntar(vista: MapView) {
        mapView = vista
        aplicar(vista, centro, zoom, bearing)
    }

    internal fun desadjuntar(vista: MapView) {
        if (mapView === vista) mapView = null
        proyeccion = null
    }

    /** Lee la cámara real de la vista. Se llama en cada dibujo del mapa: solo escribe si algo cambió. */
    internal fun sincronizarDesdeMapa() {
        val vista = mapView ?: return
        if (vista.width <= 0 || vista.height <= 0) return
        val c = vista.mapCenter
        val z = vista.zoomLevelDouble
        val b = AngulosMapa.bearingDesdeOrientacion(vista.mapOrientation)

        if (z != zoomCalibrado || pxPorGradoLng.isNaN()) calibrar(vista, c.latitude, c.longitude, z)
        val previa = proyeccion
        val sinCambios = previa != null &&
            previa.latCentro == c.latitude && previa.lngCentro == c.longitude &&
            this.zoom == z && this.bearing == b &&
            previa.anchoPx == vista.width && previa.altoPx == vista.height
        if (sinCambios) return

        centro = PuntoMapa(c.latitude, c.longitude)
        zoom = z
        bearing = b
        proyeccion = ProyeccionMapa(
            latCentro = c.latitude,
            lngCentro = c.longitude,
            pxPorGradoLng = pxPorGradoLng,
            orientacionGrados = vista.mapOrientation,
            anchoPx = vista.width,
            altoPx = vista.height
        )
        version++
    }

    /**
     * Entrada táctil del mapa. Solo marca "gesto" cuando de verdad hay movimiento (más de [holgura]
     * px o dos dedos), para que un simple toque en un marcador NO desactive el seguimiento del usuario.
     */
    internal fun alTocar(vista: View, evento: MotionEvent, holgura: Float, bloquearPager: Boolean) {
        when (evento.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = evento.x
                downY = evento.y
                if (bloquearPager) vista.parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_DOWN -> iniciarGesto()
            MotionEvent.ACTION_MOVE ->
                if (!gestoActivo && (evento.pointerCount > 1 ||
                        hypot(evento.x - downX, evento.y - downY) > holgura)
                ) iniciarGesto()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (bloquearPager) vista.parent?.requestDisallowInterceptTouchEvent(false)
                gestoActivo = false
                enMovimiento = false
                inicioPorGesto = false
            }
        }
    }

    private fun iniciarGesto() {
        gestoActivo = true
        enMovimiento = true
        inicioPorGesto = true
    }

    private fun aplicar(vista: MapView, punto: PuntoMapa, zoom: Double, bearing: Float) {
        vista.controller.setZoom(zoom)
        vista.controller.setCenter(GeoPoint(punto.latitud, punto.longitud))
        vista.setMapOrientation(AngulosMapa.orientacionDesdeBearing(bearing))
        sincronizarDesdeMapa()
    }

    /**
     * Mide cuántos píxeles ocupa un grado de longitud con el zoom actual, usando la propia vista
     * (así cuenta el escalado por dpi de las teselas). Solo se mide la DISTANCIA entre dos puntos, que
     * no cambia con la rotación del mapa. Se repite únicamente cuando cambia el zoom.
     */
    private fun calibrar(vista: MapView, lat: Double, lng: Double, z: Double) {
        val proy = vista.projection
        val a = Point()
        val b = Point()
        val lng2 = if (lng + DELTA_CALIBRACION > 180.0) lng - DELTA_CALIBRACION else lng + DELTA_CALIBRACION
        proy.toPixels(GeoPoint(lat, lng), a)
        proy.toPixels(GeoPoint(lat, lng2), b)
        val distancia = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())
        if (distancia > 0.0) {
            pxPorGradoLng = distancia / abs(lng2 - lng)
            zoomCalibrado = z
        }
    }

    /** Aceleración y frenado suaves (smoothstep). */
    private fun suavizar(t: Double): Double = t * t * (3.0 - 2.0 * t)

    companion object {
        const val ZOOM_POR_DEFECTO = 18.5
        const val ZOOM_MINIMO = 3.0
        const val ZOOM_MAXIMO = 20.0
        private const val DELTA_CALIBRACION = 0.01
    }
}
