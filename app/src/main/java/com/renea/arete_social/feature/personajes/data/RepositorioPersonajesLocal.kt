package com.renea.arete_social.feature.personajes.data

import android.content.Context
import android.content.SharedPreferences
import com.renea.arete_social.feature.personajes.model.Recuerdo
import com.renea.arete_social.feature.personajes.model.Relacion
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Interfaz de repositorio para gestionar el estado local de relaciones y recuerdos de personajes.
 */
interface RepositorioPersonajes {
    fun obtenerRelaciones(): List<Relacion>
    fun obtenerRelacion(otroOwnerId: String): Relacion?
    fun guardarRelacion(relacion: Relacion)
    fun obtenerRecuerdos(): List<Recuerdo>
    fun guardarRecuerdo(recuerdo: Recuerdo)
}

/**
 * Persistencia local offline de relaciones y recuerdos en SharedPreferences (JSON).
 */
class RepositorioPersonajesLocal(
    context: Context? = null,
    prefsOverride: SharedPreferences? = null
) : RepositorioPersonajes {

    private val prefs: SharedPreferences? =
        prefsOverride ?: context?.applicationContext?.getSharedPreferences("antired_personajes_social_db", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private companion object {
        const val KEY_RELACIONES = "relaciones_json"
        const val KEY_RECUERDOS = "recuerdos_json"
        const val MAX_RECUERDOS_GUARDADOS = 50
    }

    @Synchronized
    override fun obtenerRelaciones(): List<Relacion> {
        val p = prefs ?: return emptyList()
        val raw = p.getString(KEY_RELACIONES, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<Relacion>>(raw) }.getOrDefault(emptyList())
    }

    override fun obtenerRelacion(otroOwnerId: String): Relacion? {
        return obtenerRelaciones().find { it.otroOwnerId == otroOwnerId }
    }

    @Synchronized
    override fun guardarRelacion(relacion: Relacion) {
        val p = prefs ?: return
        val listaActual = obtenerRelaciones().filter { it.otroOwnerId != relacion.otroOwnerId }
        val nuevaLista = listaActual + relacion
        p.edit().putString(KEY_RELACIONES, json.encodeToString(nuevaLista)).apply()
    }

    @Synchronized
    override fun obtenerRecuerdos(): List<Recuerdo> {
        val p = prefs ?: return emptyList()
        val raw = p.getString(KEY_RECUERDOS, null) ?: return emptyList()
        val lista = runCatching { json.decodeFromString<List<Recuerdo>>(raw) }.getOrDefault(emptyList())
        return lista.sortedByDescending { it.fechaMillis }
    }

    @Synchronized
    override fun guardarRecuerdo(recuerdo: Recuerdo) {
        val p = prefs ?: return
        val listaActual = obtenerRecuerdos().filter { it.id != recuerdo.id }
        val nuevaLista = (listOf(recuerdo) + listaActual).take(MAX_RECUERDOS_GUARDADOS)
        p.edit().putString(KEY_RECUERDOS, json.encodeToString(nuevaLista)).apply()
    }
}
