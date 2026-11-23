package com.renea.arete_social.data.local.lugares

import android.content.Context
import android.content.SharedPreferences
import com.renea.arete_social.domain.model.Lugar
import com.renea.arete_social.domain.model.Ubicacion
import com.renea.arete_social.domain.model.ZonaRegional
import com.renea.arete_social.domain.repository.RepositorioLugares
import com.renea.arete_social.domain.util.distanciaMetros
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class RepositorioLugaresLocal(
    context: Context? = null
) : RepositorioLugares {

    private val prefs: SharedPreferences? = context?.getSharedPreferences("antired_lugares_local_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lugaresEnMemoria = ConcurrentHashMap<String, Lugar>()
    private val _lugaresActualizados = MutableStateFlow(System.currentTimeMillis())

    init {
        cargarDesdePrefs()
    }

    private fun cargarDesdePrefs() {
        prefs?.all?.forEach { (clave, valor) ->
            if (clave.startsWith("lugar_") && valor is String) {
                runCatching {
                    val lugar = json.decodeFromString(Lugar.serializer(), valor)
                    lugaresEnMemoria[lugar.id] = lugar
                }
            }
        }
    }

    private fun guardarEnPrefs(lugar: Lugar) {
        prefs?.let {
            val serializado = runCatching { json.encodeToString(Lugar.serializer(), lugar) }.getOrNull() ?: return
            it.edit().putString("lugar_${lugar.id}", serializado).apply()
        }
    }

    override suspend fun crear(nombre: String, descripcion: String, ubicacion: Ubicacion): Lugar {
        val lugar = Lugar(
            id = UUID.randomUUID().toString(),
            nombre = nombre,
            descripcion = descripcion,
            ubicacion = ubicacion,
            chatId = UUID.randomUUID().toString()
        )
        lugaresEnMemoria[lugar.id] = lugar
        guardarEnPrefs(lugar)
        _lugaresActualizados.value = System.currentTimeMillis()
        return lugar
    }

    override suspend fun obtenerPorId(id: String): Lugar? = lugaresEnMemoria[id]

    override fun observarLugar(id: String): Flow<Lugar?> {
        return _lugaresActualizados.map { lugaresEnMemoria[id] }
    }

    override suspend fun lugaresCercanos(origen: Ubicacion, radioMetros: Double): List<Lugar> {
        return lugaresEnMemoria.values.filter {
            distanciaMetros(origen, it.ubicacion) <= radioMetros
        }
    }

    override suspend fun lugaresEnZona(zona: ZonaRegional, limite: Int): List<Lugar> {
        return lugaresEnMemoria.values.filter {
            distanciaMetros(it.ubicacion, zona.region.ubicacion) <= zona.radioMaxMetros
        }.take(limite)
    }
}
