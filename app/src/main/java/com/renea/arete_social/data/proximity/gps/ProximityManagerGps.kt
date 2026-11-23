package com.renea.arete_social.data.proximity.gps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.renea.arete_social.domain.model.Dispositivo
import com.renea.arete_social.domain.model.Ubicacion
import com.renea.arete_social.domain.proximity.DetectorProximidad
import com.renea.arete_social.domain.repository.RepositorioUsuarios
import com.renea.arete_social.domain.util.distanciaMetros
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "ProximityGPS"

/**
 * Implementación de DetectorProximidad por GPS nativo de Android (100% software libre, sin Google Play Services).
 * Si no se han concedido los permisos de ubicación, no lanza excepción, sino que suspende limpiamente.
 */
class ProximityManagerGps(
    private val context: Context,
    private val repositorioUsuarios: RepositorioUsuarios,
    private val radioMetros: Double = 500.0,
    private val usuarioIdProveedor: (() -> String?)? = null
) : DetectorProximidad {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    @Volatile
    var ultimaUbicacionConocida: Ubicacion? = null
        private set

    @SuppressLint("MissingPermission")
    override fun ubicacionPropia(): Flow<Ubicacion> = callbackFlow {
        val tienePermiso = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!tienePermiso || locationManager == null) {
            Log.w(TAG, "Ubicación GPS en espera: permiso no concedido o LocationManager no disponible.")
            awaitClose { }
            return@callbackFlow
        }

        // 1. Obtener última ubicación conocida desde GPS o red
        runCatching {
            val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val mejor = when {
                lastGps != null && lastNetwork != null -> if (lastGps.time >= lastNetwork.time) lastGps else lastNetwork
                lastGps != null -> lastGps
                else -> lastNetwork
            }
            if (mejor != null) {
                val ubi = Ubicacion(
                    mejor.latitude,
                    mejor.longitude,
                    System.currentTimeMillis(),
                    precisionMetros = if (mejor.hasAccuracy()) mejor.accuracy else null
                )
                ultimaUbicacionConocida = ubi
                trySend(ubi)
            }
        }.onFailure {
            Log.w(TAG, "Error obteniendo lastKnownLocation: ${it.message}")
        }

        // 2. Suscripción continua con LocationListener nativo
        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                val ubi = Ubicacion(
                    loc.latitude,
                    loc.longitude,
                    System.currentTimeMillis(),
                    precisionMetros = if (loc.hasAccuracy()) loc.accuracy else null
                )
                ultimaUbicacionConocida = ubi
                trySend(ubi)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }

        val gpsHabilitado = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val redHabilitada = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        val exitoGps = if (gpsHabilitado) {
            runCatching {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    10_000L,
                    3f,
                    listener,
                    Looper.getMainLooper()
                )
            }.isSuccess
        } else false

        val exitoRed = if (redHabilitada) {
            runCatching {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    10_000L,
                    3f,
                    listener,
                    Looper.getMainLooper()
                )
            }.isSuccess
        } else false

        awaitClose {
            if (exitoGps || exitoRed) {
                runCatching { locationManager.removeUpdates(listener) }
            }
        }
    }

    override fun descubrir(): Flow<List<Dispositivo>> = callbackFlow {
        var ultimaUbicacion: Ubicacion? = ultimaUbicacionConocida

        suspend fun consultarYEnviar(origen: Ubicacion) {
            val miUsuarioId = usuarioIdProveedor?.invoke()
            val dispositivos = runCatching {
                repositorioUsuarios.usuariosCercanos(origen, radioMetros)
            }.getOrDefault(emptyList())
                .filter { miUsuarioId == null || it.id != miUsuarioId }
                .map { usuario ->
                    Dispositivo(
                        id = usuario.id,
                        nombre = usuario.nombre,
                        señal = null,
                        ultimaConexion = System.currentTimeMillis(),
                        usuarioId = usuario.id,
                        distanciaMetros = usuario.ubicacion?.let { distanciaMetros(origen, it) },
                        ubicacion = usuario.ubicacion,
                        parcheActivo = usuario.parcheActivo,
                        estadoMichi = usuario.estadoMichi?.takeIf { (usuario.estadoMichiHasta ?: 0L) > System.currentTimeMillis() },
                        estadoMichiHasta = usuario.estadoMichiHasta
                    )
                }
            trySend(dispositivos)
        }

        // Emisión inicial vacía para que combine() arranque de inmediato
        trySend(emptyList())

        // Si ya teníamos una ubicación conocida, consultar de inmediato sin esperar al primer tick
        ultimaUbicacionConocida?.let { loc ->
            launch { consultarYEnviar(loc) }
        }

        // 1. Escuchar actualizaciones continuas de posición propia
        val trabajoUbicacion = launch {
            ubicacionPropia().collect { loc ->
                ultimaUbicacion = loc
                consultarYEnviar(loc)
            }
        }

        // 2. Sondeo periódico cada 15 segundos:
        val trabajoSondeo = launch {
            while (isActive) {
                delay(15_000L)
                (ultimaUbicacion ?: ultimaUbicacionConocida)?.let { loc ->
                    consultarYEnviar(loc)
                }
            }
        }

        awaitClose {
            trabajoUbicacion.cancel()
            trabajoSondeo.cancel()
        }
    }

    override fun detener() = Unit
}
