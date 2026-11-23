package com.renea.arete_social.feature.comunidades.data

import android.content.Context
import android.content.SharedPreferences
import com.renea.arete_social.feature.comunidades.PublicacionMuro
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Gestor singleton para la persistencia local de publicaciones del muro de comunidades.
 * Almacena de forma permanente los mensajes, imágenes y likes de cada comunidad en SharedPreferences.
 */
object GestorMuroComunidades {

    private const val PREFS_NAME = "antired_muro_comunidades_prefs"
    private const val KEY_PUBLICACIONES_PREFIX = "muro_comunidad_"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var prefs: SharedPreferences? = null

    fun inicializar(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun obtenerPublicaciones(comunidadId: String): List<PublicacionMuro> {
        val sp = prefs ?: return emptyList()
        val raw = sp.getString("$KEY_PUBLICACIONES_PREFIX$comunidadId", null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<PublicacionMuro>>(raw)
        }.getOrDefault(emptyList())
    }

    fun guardarPublicacion(comunidadId: String, publicacion: PublicacionMuro) {
        val actuales = obtenerPublicaciones(comunidadId).toMutableList()
        val index = actuales.indexOfFirst { it.id == publicacion.id }
        if (index >= 0) {
            actuales[index] = publicacion
        } else {
            actuales.add(0, publicacion)
        }
        guardarLista(comunidadId, actuales)
    }

    fun guardarLista(comunidadId: String, publicaciones: List<PublicacionMuro>) {
        val sp = prefs ?: return
        runCatching {
            val raw = json.encodeToString(publicaciones)
            sp.edit().putString("$KEY_PUBLICACIONES_PREFIX$comunidadId", raw).apply()
        }
    }

    fun toggleLike(comunidadId: String, publicacionId: String, miUsuarioId: String): PublicacionMuro? {
        val actuales = obtenerPublicaciones(comunidadId).toMutableList()
        val index = actuales.indexOfFirst { it.id == publicacionId }
        if (index < 0) return null

        val actual = actuales[index]
        val yaDioLike = actual.likedPorMi
        val nuevoLikes = if (yaDioLike) (actual.likes - 1).coerceAtLeast(0) else actual.likes + 1
        val actualizada = actual.copy(
            likes = nuevoLikes,
            likedPorMi = !yaDioLike
        )
        actuales[index] = actualizada
        guardarLista(comunidadId, actuales)
        return actualizada
    }

    private const val KEY_ARCHIVOS_PREFIX = "archivos_comunidad_"

    fun obtenerArchivos(comunidadId: String): List<com.renea.arete_social.feature.comunidades.ArchivoComunidad> {
        val sp = prefs ?: return emptyList()
        val raw = sp.getString("$KEY_ARCHIVOS_PREFIX$comunidadId", null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<com.renea.arete_social.feature.comunidades.ArchivoComunidad>>(raw)
        }.getOrDefault(emptyList())
    }

    fun guardarArchivo(comunidadId: String, archivo: com.renea.arete_social.feature.comunidades.ArchivoComunidad) {
        val actuales = obtenerArchivos(comunidadId).toMutableList()
        val index = actuales.indexOfFirst { it.id == archivo.id }
        if (index >= 0) {
            actuales[index] = archivo
        } else {
            actuales.add(0, archivo)
        }
        val sp = prefs ?: return
        runCatching {
            val raw = json.encodeToString(actuales)
            sp.edit().putString("$KEY_ARCHIVOS_PREFIX$comunidadId", raw).apply()
        }
    }
}
