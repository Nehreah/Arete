package com.renea.arete_social.data.proximity.hibrido

import android.util.Log
import com.renea.arete_social.domain.model.Dispositivo
import com.renea.arete_social.domain.model.Ubicacion
import com.renea.arete_social.domain.proximity.DetectorProximidad
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn

/**
 * Detector de proximidad híbrido que unifica las fuentes de GPS y Bluetooth Low Energy (BLE).
 *
 * - Si un usuario es detectado por GPS y BLE a la vez:
 *   Mantiene sus coordenadas de mapa pero enriquece el nodo con la señal de radio real (RSSI)
 *   y la distancia calculada por radiofrecuencia de baja latencia.
 * - Si un usuario es detectado únicamente por BLE (ej. sin Internet en sótanos/campus):
 *   Aparece en la lista táctica del radar con su intensidad de señal y distancia estimada.
 * - Si un usuario es detectado únicamente por GPS:
 *   Se muestra normalmente con su distancia calculada por coordenadas geográficas.
 */
class ProximityManagerHibrido(
    private val detectorGps: DetectorProximidad,
    private val detectorBle: DetectorProximidad,
    private val flujoWifi: Flow<List<Dispositivo>>? = null
) : DetectorProximidad {

    override fun ubicacionPropia(): Flow<Ubicacion> =
        detectorGps.ubicacionPropia()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * UNA sola cadena GPS+BLE compartida por todos los suscriptores.
     *
     * `descubrir()` de BLE es un callbackFlow FRÍO: cada `collect` arrancaba su propio escaneo y su
     * propia publicidad (MapaViewModel, GattMensajeriaManager, PetViewModel… ≥3 a la vez, más las
     * llamadas repetidas a `iniciar()`). Efectos: se agotaban los anunciantes BLE del chip
     * (ADVERTISE_FAILED_TOO_MANY_ADVERTISERS), Android estrangulaba los reinicios de escaneo
     * (máx. 5 cada 30 s) y, como el callback de publicidad es un único campo compartido, cerrar UNA
     * pantalla apagaba la publicidad de todas → el resto de teléfonos dejaba de ver este nodo.
     * Con `WhileSubscribed` el escaneo vive mientras haya al menos un suscriptor.
     */
    private val compartido: SharedFlow<List<Dispositivo>> by lazy {
        construirFlujo().shareIn(scope, SharingStarted.Eagerly, replay = 1)
    }

    override fun descubrir(): Flow<List<Dispositivo>> = compartido

    private fun construirFlujo(): Flow<List<Dispositivo>> =
        combine(
            detectorGps.descubrir()
                .onStart { emit(emptyList()) }
                .catch { e ->
                    Log.w("ProximityHibrido", "GPS falló, BLE sigue solo: ${e.message}")
                    emit(emptyList())
                },
            detectorBle.descubrir()
                .catch { e ->
                    Log.w("ProximityHibrido", "BLE falló, GPS sigue solo: ${e.message}")
                    emit(emptyList())
                },
            flujoWifi?.catch { emit(emptyList()) } ?: kotlinx.coroutines.flow.flowOf(emptyList())
        ) { personasGps, dispositivosBle, dispositivosWifi ->
            fun normalizar(id: String?): String {
                if (id.isNullOrBlank()) return ""
                val trimmed = id.trim().lowercase()
                return if (trimmed.startsWith("invitado-local-")) {
                    "invitado-local-" + trimmed.removePrefix("invitado-local-").replace("-", "")
                } else {
                    trimmed.replace("-", "")
                }
            }

            fun truncarSeguro(clave: String): String {
                if (clave.isEmpty()) return ""
                return if (clave.startsWith("invitado-local-")) {
                    "invitado-local-" + clave.removePrefix("invitado-local-").take(16)
                } else {
                    clave.take(16)
                }
            }

            val blePorClave = mutableMapOf<String, Dispositivo>()
            for (ble in dispositivosBle) {
                val claveUsuario = normalizar(ble.usuarioId)
                val claveId = normalizar(ble.id)
                val claveMac = normalizar(ble.macAddress)
                if (claveUsuario.isNotEmpty()) {
                    blePorClave[claveUsuario] = ble
                    blePorClave[truncarSeguro(claveUsuario)] = ble
                }
                if (claveId.isNotEmpty()) {
                    blePorClave[claveId] = ble
                    blePorClave[truncarSeguro(claveId)] = ble
                }
                if (claveMac.isNotEmpty()) {
                    blePorClave[claveMac] = ble
                }
            }

            val resultado = mutableListOf<Dispositivo>()
            val procesados = mutableSetOf<String>()

            // 1. Procesar dispositivos GPS (que tienen nombre real de usuario) y fusionar con datos de radio BLE
            for (persona in personasGps) {
                val claveUsuario = normalizar(persona.usuarioId)
                val claveId = normalizar(persona.id)
                val claveMac = normalizar(persona.macAddress)
                val matchBle = blePorClave[claveUsuario]
                    ?: blePorClave[truncarSeguro(claveUsuario)]
                    ?: blePorClave[claveId]
                    ?: blePorClave[truncarSeguro(claveId)]
                    ?: (if (claveMac.isNotEmpty()) blePorClave[claveMac] else null)

                if (matchBle != null) {
                    // Preferir el estadoMichi recibido por BLE si aún no ha expirado.
                    // Sin esto, el estado recibido vía ESTADO_MICHI se sobreescribía con el
                    // valor previo de GPS al combinar los dos flujos, y el cambio
                    // de estado del peer nunca se veía en el mapa del receptor.
                    val ahora = System.currentTimeMillis()
                    val bleEstadoVigente = matchBle.estadoMichi != null &&
                        (matchBle.estadoMichiHasta == null || matchBle.estadoMichiHasta > ahora)
                    val estadoMichiFinal   = if (bleEstadoVigente) matchBle.estadoMichi   else persona.estadoMichi
                    val estadoHastaFinal   = if (bleEstadoVigente) matchBle.estadoMichiHasta else persona.estadoMichiHasta

                    resultado += persona.copy(
                        // La MAC solo la conoce el escaneo BLE. Sin copiarla, todo usuario visto por
                        // GPS y BLE a la vez quedaba SIN macAddress y GattMensajeriaManager lo saltaba
                        // (`macAddress ?: return`): justo los peers cercanos con internet nunca
                        // recibían conexión Mesh y el enlace solo existía si perdían la red.
                        macAddress = matchBle.macAddress ?: persona.macAddress,
                        señal = matchBle.señal,
                        distanciaMetros = matchBle.distanciaMetros ?: persona.distanciaMetros,
                        ultimaConexion = maxOf(persona.ultimaConexion, matchBle.ultimaConexion),
                        estadoMichi = estadoMichiFinal,
                        estadoMichiHasta = estadoHastaFinal
                    )
                } else {
                    resultado += persona
                }

                if (claveUsuario.isNotEmpty()) {
                    procesados.add(claveUsuario)
                    procesados.add(truncarSeguro(claveUsuario))
                }
                if (claveId.isNotEmpty()) {
                    procesados.add(claveId)
                    procesados.add(truncarSeguro(claveId))
                }
            }

            val ahora = System.currentTimeMillis()

            // 2. Agregar nodos detectados exclusivamente por BLE que no hayan sido agregados por GPS
            val yaAgregadosBle = mutableSetOf<String>()
            for (ble in dispositivosBle) {
                if (ahora - ble.ultimaConexion > 45_000L) continue
                val claveUsuario = normalizar(ble.usuarioId)
                val claveId = normalizar(ble.id)
                val clavePrincipal = if (claveUsuario.isNotEmpty()) claveUsuario else claveId
                val claveTruncada = truncarSeguro(clavePrincipal)

                if (clavePrincipal.isNotEmpty() && clavePrincipal !in procesados && clavePrincipal !in yaAgregadosBle && claveTruncada !in procesados && claveTruncada !in yaAgregadosBle) {
                    resultado += ble
                    yaAgregadosBle.add(clavePrincipal)
                    yaAgregadosBle.add(claveTruncada)
                }
            }

            // 3. Agregar nodos detectados por Wi-Fi LAN / Mesh que no hayan sido agregados por GPS ni BLE
            for (wifi in dispositivosWifi) {
                if (ahora - wifi.ultimaConexion > 45_000L) continue
                val claveUsuario = normalizar(wifi.usuarioId)
                val claveId = normalizar(wifi.id)
                val clavePrincipal = if (claveUsuario.isNotEmpty()) claveUsuario else claveId
                val claveTruncada = truncarSeguro(clavePrincipal)

                if (clavePrincipal.isNotEmpty() && clavePrincipal !in procesados && clavePrincipal !in yaAgregadosBle && claveTruncada !in procesados && claveTruncada !in yaAgregadosBle) {
                    resultado += wifi
                    yaAgregadosBle.add(clavePrincipal)
                    yaAgregadosBle.add(claveTruncada)
                }
            }

            resultado.distinctBy { truncarSeguro(normalizar(it.usuarioId ?: it.id)) }
                .sortedBy { it.distanciaMetros ?: Double.MAX_VALUE }
        }

    override fun detener() {
        detectorGps.detener()
        // El BLE se apaga solo cuando se va el último suscriptor (WhileSubscribed).
    }
}
