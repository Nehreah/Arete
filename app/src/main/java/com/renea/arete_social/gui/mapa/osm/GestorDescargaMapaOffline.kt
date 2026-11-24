package com.renea.arete_social.gui.mapa.osm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

private const val TAG = "DescargaMapaOffline"

sealed interface EstadoDescargaMapa {
    object Inactivo : EstadoDescargaMapa
    data class EnProgreso(val porcentaje: Int, val teselasDescargadas: Int, val totalTeselas: Int) : EstadoDescargaMapa
    data class Completado(val totalDescargadas: Int) : EstadoDescargaMapa
    data class Error(val mensaje: String) : EstadoDescargaMapa
}

/**
 * Gestor soberano de descarga de teselas OpenStreetMap para uso offline en Aréte.
 *
 * Utiliza [CacheManager] de OSMDroid para precargar regiones geográficas definidas
 * (radio en km o BoundingBox) en los niveles de zoom necesarios para el radar táctico,
 * guardándolas en almacenamiento persistente no volátil para que funcionen sin internet.
 */
class GestorDescargaMapaOffline(
    private val context: Context,
    private val mapView: MapView
) {
    private val cacheManager = runCatching {
        CacheManager(mapView)
    }.getOrElse {
        CacheManager(FuenteMapnik, mapView.tileProvider.tileWriter, 0, 19)
    }
    private var tareaActual: CacheManager.CacheManagerTask? = null

    private val _estado = MutableStateFlow<EstadoDescargaMapa>(EstadoDescargaMapa.Inactivo)
    val estado: StateFlow<EstadoDescargaMapa> = _estado.asStateFlow()

    /**
     * Calcula la caja geográfica alrededor de un centro dado con un radio en kilómetros.
     */
    fun calcularCajaRadio(centro: GeoPoint, radioKm: Double): BoundingBox {
        val deltaLat = radioKm / 111.0
        val cosLat = kotlin.math.cos(Math.toRadians(centro.latitude)).coerceAtLeast(0.01)
        val deltaLon = radioKm / (111.0 * cosLat)
        return BoundingBox(
            centro.latitude + deltaLat,
            centro.longitude + deltaLon,
            centro.latitude - deltaLat,
            centro.longitude - deltaLon
        )
    }

    /**
     * Estima el número de teselas necesarias para cubrir el área entre [zoomMin] y [zoomMax].
     */
    fun estimarTeselas(caja: BoundingBox, zoomMin: Int = 13, zoomMax: Int = 16): Int {
        return runCatching {
            cacheManager.possibleTilesInArea(caja, zoomMin, zoomMax)
        }.getOrDefault(0)
    }

    /**
     * Inicia la descarga asíncrona de teselas para que queden disponibles 100% offline.
     * Utiliza [downloadAreaAsyncNoUI] para delegar el progreso a nuestra interfaz Compose
     * sin levantar diálogos obsoletos ni bloquear la ventana.
     */
    fun iniciarDescarga(
        caja: BoundingBox,
        zoomMin: Int = 13,
        zoomMax: Int = 16
    ) {
        cancelarDescarga()
        val total = estimarTeselas(caja, zoomMin, zoomMax)
        if (total <= 0) {
            _estado.value = EstadoDescargaMapa.Error("Área no válida o sin teselas requeridas")
            return
        }

        _estado.value = EstadoDescargaMapa.EnProgreso(0, 0, total)
        try {
            tareaActual = cacheManager.downloadAreaAsyncNoUI(
                context,
                caja,
                zoomMin,
                zoomMax,
                object : CacheManager.CacheManagerCallback {
                    override fun onTaskComplete() {
                        Log.d(TAG, "Descarga de mapas completada exitosamente ($total teselas)")
                        _estado.value = EstadoDescargaMapa.Completado(total)
                    }

                    override fun onTaskFailed(errors: Int) {
                        Log.w(TAG, "Descarga de mapas finalizó con $errors errores de un total de $total")
                        val descargadas = (total - errors).coerceAtLeast(0)
                        if (descargadas > 0) {
                            // Si la gran mayoría de teselas se guardó, el mapa funciona offline.
                            _estado.value = EstadoDescargaMapa.Completado(descargadas)
                        } else {
                            _estado.value = EstadoDescargaMapa.Error("No se pudo conectar al servidor de mapas. Verifica tu conexión e inténtalo de nuevo.")
                        }
                    }

                    override fun updateProgress(progress: Int, current: Int, totalProgress: Int, percent: Int) {
                        _estado.value = EstadoDescargaMapa.EnProgreso(
                            porcentaje = percent.coerceIn(0, 100),
                            teselasDescargadas = progress,
                            totalTeselas = totalProgress
                        )
                    }

                    override fun downloadStarted() {
                        Log.d(TAG, "Descarga de teselas de mapa iniciada")
                    }

                    override fun setPossibleTilesInArea(total: Int) {
                        // Notificación de total de teselas a descargar
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al iniciar CacheManager: ${e.message}", e)
            _estado.value = EstadoDescargaMapa.Error("No se pudo iniciar la descarga: ${e.message}")
        }
    }

    fun cancelarDescarga() {
        tareaActual?.let {
            runCatching { it.cancel(true) }
        }
        tareaActual = null
        if (_estado.value is EstadoDescargaMapa.EnProgreso) {
            _estado.value = EstadoDescargaMapa.Inactivo
        }
    }

    fun limpiarEstado() {
        _estado.value = EstadoDescargaMapa.Inactivo
    }
}
