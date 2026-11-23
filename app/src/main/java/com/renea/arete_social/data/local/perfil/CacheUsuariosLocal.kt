package com.renea.arete_social.data.local.perfil

import android.content.SharedPreferences
import com.renea.arete_social.data.local.media.AlmacenMediaPerfil
import com.renea.arete_social.domain.model.Usuario
import kotlinx.serialization.json.Json

/**
 * Persistencia y caché local de perfiles de usuario (propio y de amigos/contactos).
 * Guarda perfiles localmente en SharedPreferences sin dependencia de backend externo.
 */
class CacheUsuariosLocal(
    private val prefs: SharedPreferences,
    private val reloj: () -> Long = System::currentTimeMillis
) {
    companion object {
        private const val PREFIJO_USUARIO = "usr_"
        private const val CLAVE_ORDEN = "usuarios_cacheados_orden"
        const val MAX_USUARIOS_CACHE = 150
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Synchronized
    fun guardar(usuario: Usuario) {
        if (usuario.id.isBlank()) return
        val fotoLimpia = AlmacenMediaPerfil.migrarSiEsDataUrl(usuario.foto, esAvatar = true)
        val portadaLimpia = AlmacenMediaPerfil.migrarSiEsDataUrl(usuario.portada, esAvatar = false)
        val usuarioAjustado = usuario.copy(foto = fotoLimpia, portada = portadaLimpia)

        val texto = runCatching { json.encodeToString(Usuario.serializer(), usuarioAjustado) }.getOrNull() ?: return
        val clave = PREFIJO_USUARIO + usuario.id

        val ordenPrevio = prefs.getString(CLAVE_ORDEN, "").orEmpty()
            .split(',')
            .filter { it.isNotBlank() && it != usuario.id }
        val nuevoOrden = (listOf(usuario.id) + ordenPrevio).take(MAX_USUARIOS_CACHE)

        val editor = prefs.edit()
        editor.putString(clave, texto)
        editor.putString(CLAVE_ORDEN, nuevoOrden.joinToString(","))

        val expulsados = ordenPrevio.drop(MAX_USUARIOS_CACHE - 1)
        for (idViejo in expulsados) {
            editor.remove(PREFIJO_USUARIO + idViejo)
        }
        editor.apply()
    }

    fun obtener(usuarioId: String): Usuario? {
        if (usuarioId.isBlank()) return null
        val texto = prefs.getString(PREFIJO_USUARIO + usuarioId, null) ?: return null
        val usuario = runCatching {
            json.decodeFromString(Usuario.serializer(), texto)
        }.getOrNull() ?: return null

        val fotoLimpia = AlmacenMediaPerfil.migrarSiEsDataUrl(usuario.foto, esAvatar = true)
        val portadaLimpia = AlmacenMediaPerfil.migrarSiEsDataUrl(usuario.portada, esAvatar = false)
        return usuario.copy(foto = fotoLimpia, portada = portadaLimpia)
    }

    @Synchronized
    fun guardarTodos(usuarios: List<Usuario>) {
        usuarios.forEach { guardar(it) }
    }

    @Synchronized
    fun eliminar(usuarioId: String) {
        val clave = PREFIJO_USUARIO + usuarioId
        val orden = prefs.getString(CLAVE_ORDEN, "").orEmpty()
            .split(',')
            .filter { it.isNotBlank() && it != usuarioId }
        prefs.edit()
            .remove(clave)
            .putString(CLAVE_ORDEN, orden.joinToString(","))
            .apply()
    }

    @Synchronized
    fun limpiar() {
        prefs.edit().clear().apply()
    }
}
