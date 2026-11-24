package com.renea.arete_social.gui.mapa.osm

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.ViewConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay

/**
 * MapView que avisa tras cada dibujo. Cualquier cambio de cámara (arrastre, inercia, zoom, giro o
 * animación) provoca un redibujo, así la cámara de Compose siempre queda al día, incluida la rotación,
 * que `MapListener` no notifica.
 */
private class MapaVista(contexto: Context) : MapView(contexto) {
    var alDibujar: (() -> Unit)? = null

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        alDibujar?.invoke()
    }
}

/**
 * Mapa OSMDroid para Compose (sustituye a `GoogleMap`). Es la ÚNICA puerta de entrada al motor:
 * recibe tipos propios ([PuntoMapa], [MarcadorMapa], [LineaMapa], [TemaMapa]) y devuelve eventos
 * simples. Los Mimis NO son overlays: la capa Compose va por encima, en un `Box` hermano.
 *
 * @param estadoCamara cámara compartida con el resto de la pantalla (y con la capa de Mimis).
 * @param ubicacionPropia punto azul del usuario; null = no se dibuja.
 * @param onClickMapa toque en una zona sin marcador.
 * @param onClickMarcador toque en un marcador con `abreInfo = false`; recibe su `id`.
 * @param bloquearPager impide que el `HorizontalPager` de pestañas robe los arrastres del mapa.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun OsmMapa(
    estadoCamara: EstadoCamaraOsm,
    tema: TemaMapa,
    modifier: Modifier = Modifier,
    marcadores: List<MarcadorMapa> = emptyList(),
    lineas: List<LineaMapa> = emptyList(),
    ubicacionPropia: PuntoMapa? = null,
    onClickMapa: (PuntoMapa) -> Unit = {},
    onClickMarcador: (String) -> Unit = {},
    proveedor: ProveedorTeselas = ProveedorTeselasMapnik,
    bloquearPager: Boolean = true,
    mostrarDialogoDescarga: Boolean = false,
    onCerrarDialogoDescarga: () -> Unit = {},
    onMapViewReady: ((MapView) -> Unit)? = null
) {
    val contexto = LocalContext.current
    val densidad = contexto.resources.displayMetrics.density
    val alClickMapa by rememberUpdatedState(onClickMapa)
    val iconos = remember(contexto) { IconosMapa(contexto) }
    val holgura = remember(contexto) { ViewConfiguration.get(contexto).scaledTouchSlop.toFloat() }
    val temaAplicado = remember { arrayOfNulls<TemaMapa>(1) }

    val mapView = remember(contexto) {
        MapaVista(contexto).apply {
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = ZOOM_MINIMO
            // Orden fijo: toques del mapa abajo (los marcadores, encima, los reciben primero), luego rotación.
            overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                    alClickMapa(PuntoMapa(p.latitude, p.longitude))
                    return true
                }

                override fun longPressHelper(p: GeoPoint): Boolean = false
            }))
            overlays.add(RotationGestureOverlay(this).apply { isEnabled = true })
        }
    }
    val conciliador = remember(mapView) {
        ConciliadorOverlays(mapView, iconos, densidad).also { it.fijarOverlaysBase() }
    }

    androidx.compose.runtime.LaunchedEffect(mapView) {
        onMapViewReady?.invoke(mapView)
    }

    // Cámara: se enlaza a la vista y se mantiene sincronizada tras cada dibujo.
    DisposableEffect(mapView, estadoCamara) {
        mapView.alDibujar = { estadoCamara.sincronizarDesdeMapa() }
        mapView.setOnTouchListener { v, evento ->
            estadoCamara.alTocar(v, evento, holgura, bloquearPager)
            false // no consume: el MapView sigue manejando el gesto
        }
        estadoCamara.adjuntar(mapView)
        onDispose {
            mapView.alDibujar = null
            mapView.setOnTouchListener(null)
            estadoCamara.desadjuntar(mapView)
        }
    }

    // Ciclo de vida: onResume/onPause guardan caché y detienen trabajo.
    // Invocamos onDetach() explícitamente al salir de composición para liberar hilos y bitmaps.
    val vista = LocalView.current
    DisposableEffect(vista, mapView) {
        val propietario = vista.findViewTreeLifecycleOwner()
        val observador = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDetach()
                else -> Unit
            }
        }
        propietario?.lifecycle?.addObserver(observador)
        onDispose {
            propietario?.lifecycle?.removeObserver(observador)
            conciliador.limpiar()
            mapView.onDetach()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        onRelease = { mv -> mv.onDetach() },
        update = { mv ->
            if (temaAplicado[0] != tema) {
                temaAplicado[0] = tema
                aplicarTema(mv, proveedor, tema)
            }
            conciliador.alClickMarcador = onClickMarcador
            conciliador.conciliar(lineas, marcadores, ubicacionPropia)
        }
    )

    if (mostrarDialogoDescarga) {
        val gestor = remember(mapView) { GestorDescargaMapaOffline(contexto, mapView) }
        DialogoDescargaMapaOffline(
            gestor = gestor,
            centroActual = GeoPoint(estadoCamara.centro.latitud, estadoCamara.centro.longitud),
            onDismiss = onCerrarDialogoDescarga
        )
    }
}

/** Fuente de teselas + filtro de color + colores de carga del tema. No recrea el mapa ni mueve la cámara. */
private fun aplicarTema(mv: MapView, proveedor: ProveedorTeselas, tema: TemaMapa) {
    val fuente = proveedor.fuente(tema)
    if (mv.tileProvider.tileSource != fuente) mv.setTileSource(fuente)
    // estiloParaCapa() sabe usar el filtro especial de ProveedorTeselasSaturado.
    val estilo = estiloParaCapa(proveedor, tema)
    mv.overlayManager.tilesOverlay.apply {
        setColorFilter(estilo.filtro?.aColorFilter())
        loadingBackgroundColor = estilo.fondoCarga
        loadingLineColor = estilo.lineaCarga
    }
    mv.invalidate()
}

private const val ZOOM_MINIMO = 3.0
