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

    @Synchronized
    fun inicializar(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    @Synchronized
    fun obtenerPublicaciones(comunidadId: String): List<PublicacionMuro> {
        val sp = prefs ?: return emptyList()
        val raw = sp.getString("$KEY_PUBLICACIONES_PREFIX$comunidadId", null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<PublicacionMuro>>(raw)
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun guardarPublicacion(comunidadId: String, publicacion: PublicacionMuro) {
        val actuales = obtenerPublicaciones(comunidadId).toMutableList()
        val index = actuales.indexOfFirst { it.id == publicacion.id }
        if (index >= 0) {
            // Preservar likedPorMi local si la publicación viene de la red
            val local = actuales[index]
            val fusionada = if (publicacion.autorId != local.autorId || publicacion.likedPorMi != local.likedPorMi) {
                publicacion.copy(likedPorMi = local.likedPorMi)
            } else {
                publicacion
            }
            actuales[index] = fusionada
        } else {
            actuales.add(0, publicacion)
        }
        // Blindaje: Acotar a un máximo de 200 publicaciones para evitar saturación de SharedPreferences
        val limitadas = if (actuales.size > 200) actuales.take(200) else actuales
        guardarLista(comunidadId, limitadas)
    }

    @Synchronized
    fun guardarLista(comunidadId: String, publicaciones: List<PublicacionMuro>) {
        val sp = prefs ?: return
        runCatching {
            val limitadas = if (publicaciones.size > 200) publicaciones.take(200) else publicaciones
            val raw = json.encodeToString(limitadas)
            sp.edit().putString("$KEY_PUBLICACIONES_PREFIX$comunidadId", raw).apply()
        }
    }

    @Synchronized
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

    /**
     * Registra el like emitido por un peer remoto sin alterar el estado `likedPorMi` del usuario local.
     */
    @Synchronized
    fun registrarLikeRemoto(comunidadId: String, publicacionId: String, usuarioRemotoId: String): PublicacionMuro? {
        val actuales = obtenerPublicaciones(comunidadId).toMutableList()
        val index = actuales.indexOfFirst { it.id == publicacionId }
        if (index < 0) return null

        val actual = actuales[index]
        // Se incrementa el contador de likes recibido de la malla; likedPorMi permanece inalterado para el usuario local
        val actualizada = actual.copy(likes = actual.likes + 1)
        actuales[index] = actualizada
        guardarLista(comunidadId, actuales)
        return actualizada
    }

    private const val KEY_ARCHIVOS_PREFIX = "archivos_comunidad_"

    @Synchronized
    fun obtenerArchivos(comunidadId: String): List<com.renea.arete_social.feature.comunidades.ArchivoComunidad> {
        val sp = prefs ?: return emptyList()
        val raw = sp.getString("$KEY_ARCHIVOS_PREFIX$comunidadId", null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<com.renea.arete_social.feature.comunidades.ArchivoComunidad>>(raw)
        }.getOrDefault(emptyList())
    }

    @Synchronized
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
            val limitados = if (actuales.size > 100) actuales.take(100) else actuales
            val raw = json.encodeToString(limitados)
            sp.edit().putString("$KEY_ARCHIVOS_PREFIX$comunidadId", raw).apply()
        }
    }
}
