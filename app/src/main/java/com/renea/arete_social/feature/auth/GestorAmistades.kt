package com.renea.arete_social.feature.auth

import android.content.Context
import android.content.SharedPreferences
import com.renea.arete_social.domain.model.SolicitudAmistad
import com.renea.arete_social.domain.repository.AmistadesServidor
import com.renea.arete_social.domain.repository.RepositorioAmistades
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Estado en memoria y persistencia de amigos y solicitudes de amistad.
 *
 * La fuente de verdad es el SERVIDOR (tabla "amistades"): [sincronizar] la descarga y
 * reemplaza este estado. SharedPreferences queda solo como copia para abrir la app sin red.
 */
object GestorAmistades {
    private var prefs: SharedPreferences? = null
    private var usuarioActualId: String? = null

    private val _amigosIds = MutableStateFlow<Set<String>>(emptySet())
    val amigosIds: StateFlow<Set<String>> = _amigosIds.asStateFlow()

    private val _solicitudes = MutableStateFlow<List<SolicitudAmistad>>(emptyList())
    val solicitudes: StateFlow<List<SolicitudAmistad>> = _solicitudes.asStateFlow()

    private val _solicitudesEnviadasIds = MutableStateFlow<Set<String>>(emptySet())
    val solicitudesEnviadasIds: StateFlow<Set<String>> = _solicitudesEnviadasIds.asStateFlow()

    // Se incrementa con cada cambio local. Si cambia mientras se descarga del servidor, esa
    // descarga ya está vieja y se descarta (la siguiente sincronización la corrige).
    @Volatile private var version = 0

    // Avisa a quien sincroniza (AreteNavHost) que conviene descargar ya las amistades del servidor.
    private val _pedirSincronizacion = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sincronizacionPedida: SharedFlow<Unit> = _pedirSincronizacion.asSharedFlow()

    /** Sesión con cuenta: las amistades las decide el servidor. Sin cuenta (invitado) solo viven en el teléfono. */
    private val conCuenta: Boolean
        get() = usuarioActualId?.let { it != "invitado" && !it.startsWith("invitado-local-") } == true

    fun pedirSincronizacion() {
        _pedirSincronizacion.tryEmit(Unit)
    }

    /**
     * Llegó (por BLE/WiFi/señal) el aviso de una solicitud nueva. Con cuenta NO se confía en el aviso
     * para cambiar nada: la solicitud ya está en el servidor, así que se descarga de allí.
     */
    fun recibirAvisoSolicitud(solicitud: SolicitudAmistad) {
        agregarSolicitud(solicitud)
        if (conCuenta) pedirSincronizacion()
    }

    /** Llegó el aviso de que una solicitud mía fue respondida (o de una amistad nueva). */
    fun recibirAvisoRespuesta(otroId: String, aceptada: Boolean) {
        val teniaSolicitudEnviada = _solicitudesEnviadasIds.value.contains(otroId)
        quitarSolicitudEnviada(otroId)
        if (aceptada && teniaSolicitudEnviada) {
            agregarAmigo(otroId)
        }
        if (conCuenta) {
            pedirSincronizacion()
        }
    }

    /** Reemplaza el estado local por lo que dice el servidor. */
    fun reemplazarDesdeServidor(datos: AmistadesServidor) {
        if (conCuenta) {
            // H-01: Con cuenta, se reemplaza estrictamente sin unión para no retener amigos locales fraudulentos
            _amigosIds.value = datos.amigosIds
            _solicitudes.value = datos.solicitudesRecibidas
            _solicitudesEnviadasIds.value = datos.solicitudesEnviadasIds - datos.amigosIds
        } else {
            _amigosIds.value = datos.amigosIds + _amigosIds.value
            val conocidas = datos.solicitudesRecibidas.map { it.remitenteId }.toSet()
            val localesExtra = _solicitudes.value.filter { it.remitenteId !in conocidas && it.remitenteId !in _amigosIds.value }
            _solicitudes.value = datos.solicitudesRecibidas + localesExtra
            _solicitudesEnviadasIds.value = (datos.solicitudesEnviadasIds + _solicitudesEnviadasIds.value) - _amigosIds.value
        }
        guardarEnPrefs()
    }

    /**
     * Descarga las amistades de [miId] del servidor. Devuelve true si se aplicaron.
     * Sin red (o con un cambio local en medio) conserva el estado actual.
     */
    suspend fun sincronizar(repositorio: RepositorioAmistades, miId: String): Boolean {
        val versionInicial = version
        val datos = runCatching { repositorio.obtenerTodas(miId) }.getOrNull() ?: return false
        if (version != versionInicial) return false
        reemplazarDesdeServidor(datos)
        return true
    }

    fun inicializar(
        context: Context? = null,
        usuarioId: String? = null,
        prefsOverride: SharedPreferences? = null
    ) {
        val targetUid = usuarioId ?: "invitado"
        if (prefs != null && usuarioActualId == targetUid) return
        usuarioActualId = targetUid
        if (context != null) {
            migrarLegadoSiExiste(context)
            prefs = context.applicationContext.getSharedPreferences("antired_amistades_v2_$targetUid", Context.MODE_PRIVATE)
        } else if (prefsOverride != null) {
            prefs = prefsOverride
        }
        cargarDesdePrefs()
    }

    fun limpiar() {
        _amigosIds.value = emptySet()
        _solicitudes.value = emptyList()
        _solicitudesEnviadasIds.value = emptySet()
        prefs = null
        usuarioActualId = null
    }

    private fun migrarLegadoSiExiste(context: Context) {
        runCatching {
            val legado = context.getSharedPreferences("antired_amistades_v2", Context.MODE_PRIVATE)
            if (legado.contains("amigos_ids") || legado.contains("solicitudes_json")) {
                legado.edit().clear().apply()
            }
        }
    }

    private fun cargarDesdePrefs() {
        val p = prefs ?: return
        val amigosSet = p.getStringSet("amigos_ids", emptySet()) ?: emptySet()
        _amigosIds.value = amigosSet.toSet()

        val enviadasSet = p.getStringSet("solicitudes_enviadas_ids", emptySet()) ?: emptySet()
        _solicitudesEnviadasIds.value = enviadasSet.toSet()

        val jsonStr = p.getString("solicitudes_json", "[]") ?: "[]"
        val lista = mutableListOf<SolicitudAmistad>()
        runCatching {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                lista.add(
                    SolicitudAmistad(
                        id = obj.optString("id"),
                        remitenteId = obj.optString("remitenteId"),
                        remitenteNombre = obj.optString("remitenteNombre"),
                        remitenteFoto = obj.optString("remitenteFoto").takeIf { it.isNotBlank() },
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        }
        _solicitudes.value = lista
    }

    private fun guardarEnPrefs() {
        version++
        val p = prefs ?: return
        p.edit()
            .putStringSet("amigos_ids", _amigosIds.value)
            .putStringSet("solicitudes_enviadas_ids", _solicitudesEnviadasIds.value)
            .apply()

        val arr = JSONArray()
        for (s in _solicitudes.value) {
            val obj = JSONObject().apply {
                put("id", s.id)
                put("remitenteId", s.remitenteId)
                put("remitenteNombre", s.remitenteNombre)
                put("remitenteFoto", s.remitenteFoto ?: "")
                put("timestamp", s.timestamp)
            }
            arr.put(obj)
        }
        p.edit().putString("solicitudes_json", arr.toString()).apply()
    }

    fun esAmigo(usuarioId: String): Boolean {
        return _amigosIds.value.contains(usuarioId)
    }

    fun tieneSolicitud(usuarioId: String): Boolean {
        return _solicitudes.value.any { it.remitenteId == usuarioId }
    }

    fun tieneSolicitudEnviada(usuarioId: String): Boolean {
        return _solicitudesEnviadasIds.value.contains(usuarioId)
    }

    fun marcarSolicitudEnviada(usuarioId: String) {
        val nuevo = _solicitudesEnviadasIds.value.toMutableSet()
        nuevo.add(usuarioId)
        _solicitudesEnviadasIds.value = nuevo
        guardarEnPrefs()
    }

    fun cancelarSolicitudEnviada(usuarioId: String) {
        val nuevo = _solicitudesEnviadasIds.value.toMutableSet()
        nuevo.remove(usuarioId)
        _solicitudesEnviadasIds.value = nuevo
        guardarEnPrefs()
    }

    fun quitarSolicitudEnviada(usuarioId: String) {
        cancelarSolicitudEnviada(usuarioId)
    }

    fun agregarAmigo(usuarioId: String) {
        val nuevo = _amigosIds.value.toMutableSet()
        nuevo.add(usuarioId)
        _amigosIds.value = nuevo

        val enviadas = _solicitudesEnviadasIds.value.toMutableSet()
        enviadas.remove(usuarioId)
        _solicitudesEnviadasIds.value = enviadas

        val nuevasSolicitudes = _solicitudes.value.filter { it.remitenteId != usuarioId }
        _solicitudes.value = nuevasSolicitudes
        guardarEnPrefs()
    }

    fun eliminarAmigo(usuarioId: String) {
        val nuevo = _amigosIds.value.toMutableSet()
        nuevo.remove(usuarioId)
        _amigosIds.value = nuevo

        val enviadas = _solicitudesEnviadasIds.value.toMutableSet()
        enviadas.remove(usuarioId)
        _solicitudesEnviadasIds.value = enviadas

        guardarEnPrefs()
    }

    fun agregarSolicitud(solicitud: SolicitudAmistad) {
        if (esAmigo(solicitud.remitenteId)) return
        val lista = _solicitudes.value.filter { it.remitenteId != solicitud.remitenteId }.toMutableList()
        lista.add(0, solicitud)
        _solicitudes.value = lista
        guardarEnPrefs()
    }

    fun rechazarSolicitud(remitenteId: String) {
        val lista = _solicitudes.value.filter { it.remitenteId != remitenteId }
        _solicitudes.value = lista
        guardarEnPrefs()
    }
}
