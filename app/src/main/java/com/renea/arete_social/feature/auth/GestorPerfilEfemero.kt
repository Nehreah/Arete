package com.renea.arete_social.feature.auth

import android.content.Context
import android.content.SharedPreferences
import com.renea.arete_social.domain.model.PublicacionPerfil
import com.renea.arete_social.domain.model.TipoPublicacionPerfil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

object GestorPerfilEfemero {

    private const val PREFS_NAME = "antired_perfil_efemero_prefs"
    private const val KEY_PUBLICACIONES = "publicaciones_semanales_json"

    private var prefs: SharedPreferences? = null
    private var usuarioActualId: String? = null
    private val _publicaciones = MutableStateFlow<List<PublicacionPerfil>>(emptyList())
    val publicaciones: StateFlow<List<PublicacionPerfil>> = _publicaciones.asStateFlow()

    fun inicializar(context: Context, usuarioId: String? = null) {
        val targetUid = usuarioId ?: "invitado"
        if (prefs != null && usuarioActualId == targetUid) return
        usuarioActualId = targetUid
        prefs = context.applicationContext.getSharedPreferences("${PREFS_NAME}_$targetUid", Context.MODE_PRIVATE)
        cargarYLimpiarExpiradas()
    }

    fun limpiar() {
        _publicaciones.value = emptyList()
        prefs = null
        usuarioActualId = null
    }

    private fun cargarYLimpiarExpiradas() {
        val sp = prefs ?: return
        val rawJson = sp.getString(KEY_PUBLICACIONES, "[]") ?: "[]"
        val lista = mutableListOf<PublicacionPerfil>()
        val ahora = System.currentTimeMillis()

        runCatching {
            val jsonArray = JSONArray(rawJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id")
                val usuarioId = obj.optString("usuarioId")
                val autorNombre = obj.optString("autorNombre")
                val tipoStr = obj.optString("tipo", TipoPublicacionPerfil.TEXTO_RETRO.name)
                val tipo = runCatching { TipoPublicacionPerfil.valueOf(tipoStr) }.getOrDefault(TipoPublicacionPerfil.TEXTO_RETRO)
                val contenido = obj.optString("contenido")
                val timestamp = obj.optLong("timestamp", ahora)
                val likes = obj.optInt("likes", 0)

                val usuariosLike = mutableSetOf<String>()
                val likesArray = obj.optJSONArray("usuariosLike")
                if (likesArray != null) {
                    for (j in 0 until likesArray.length()) {
                        usuariosLike.add(likesArray.getString(j))
                    }
                }

                val pub = PublicacionPerfil(
                    id = id,
                    usuarioId = usuarioId,
                    autorNombre = autorNombre,
                    tipo = tipo,
                    contenido = contenido,
                    timestamp = timestamp,
                    likes = likes,
                    usuariosLike = usuariosLike
                )

                // Solo conservar publicaciones que tengan menos de 7 días (vigencia semanal)
                if (pub.estaVigenteSemanal(ahora)) {
                    lista.add(pub)
                }
            }
        }

        _publicaciones.value = lista
        guardarEnPrefs(lista)
    }

    fun agregarPublicacionTexto(usuarioId: String, autorNombre: String, texto: String): PublicacionPerfil? {
        if (texto.isBlank()) return null
        val nueva = PublicacionPerfil(
            usuarioId = usuarioId,
            autorNombre = autorNombre,
            tipo = TipoPublicacionPerfil.TEXTO_RETRO,
            contenido = texto.trim(),
            timestamp = System.currentTimeMillis()
        )
        val listaActualizada = listOf(nueva) + _publicaciones.value.filter { it.estaVigenteSemanal() }
        _publicaciones.value = listaActualizada
        guardarEnPrefs(listaActualizada)
        return nueva
    }

    fun agregarPublicacionFoto(usuarioId: String, autorNombre: String, fotoBase64OUrl: String): PublicacionPerfil? {
        if (fotoBase64OUrl.isBlank()) return null
        val nueva = PublicacionPerfil(
            usuarioId = usuarioId,
            autorNombre = autorNombre,
            tipo = TipoPublicacionPerfil.FOTO_MATRIX,
            contenido = fotoBase64OUrl,
            timestamp = System.currentTimeMillis()
        )
        val listaActualizada = listOf(nueva) + _publicaciones.value.filter { it.estaVigenteSemanal() }
        _publicaciones.value = listaActualizada
        guardarEnPrefs(listaActualizada)
        return nueva
    }

    fun toggleLike(publicacionId: String, miUsuarioId: String) {
        val listaActual = _publicaciones.value
        val actualizada = listaActual.map { pub ->
            if (pub.id == publicacionId) {
                val yaDioLike = pub.usuariosLike.contains(miUsuarioId)
                val nuevosLikesSet = if (yaDioLike) {
                    pub.usuariosLike - miUsuarioId
                } else {
                    pub.usuariosLike + miUsuarioId
                }
                pub.copy(
                    likes = nuevosLikesSet.size,
                    usuariosLike = nuevosLikesSet
                )
            } else {
                pub
            }
        }
        _publicaciones.value = actualizada
        guardarEnPrefs(actualizada)
    }

    fun recibirPublicacionRemota(pub: PublicacionPerfil) {
        if (!pub.estaVigenteSemanal()) return
        val lista = _publicaciones.value.toMutableList()
        val index = lista.indexOfFirst { it.id == pub.id }
        if (index >= 0) {
            val existente = lista[index]
            val likesUnidos = (existente.usuariosLike + pub.usuariosLike).toSet()
            lista[index] = pub.copy(
                likes = likesUnidos.size.coerceAtLeast(pub.likes),
                usuariosLike = likesUnidos
            )
        } else {
            lista.add(0, pub)
        }
        val limpia = lista.filter { it.estaVigenteSemanal() }
        _publicaciones.value = limpia
        guardarEnPrefs(limpia)
    }

    fun registrarLikeRemoto(publicacionId: String, usuarioId: String) {
        val listaActual = _publicaciones.value
        val actualizada = listaActual.map { pub ->
            if (pub.id == publicacionId) {
                val nuevoSet = pub.usuariosLike + usuarioId
                pub.copy(
                    likes = nuevoSet.size,
                    usuariosLike = nuevoSet
                )
            } else {
                pub
            }
        }
        _publicaciones.value = actualizada
        guardarEnPrefs(actualizada)
    }

    fun eliminarPublicacion(publicacionId: String) {
        val listaActualizada = _publicaciones.value.filter { it.id != publicacionId }
        _publicaciones.value = listaActualizada
        guardarEnPrefs(listaActualizada)
    }

    fun obtenerPublicacionesUsuario(usuarioId: String) = _publicaciones.map { lista ->
        lista.filter { it.usuarioId == usuarioId && it.estaVigenteSemanal() }
    }

    fun obtenerFotosUsuario(usuarioId: String) = _publicaciones.map { lista ->
        lista.filter { it.usuarioId == usuarioId && it.tipo == TipoPublicacionPerfil.FOTO_MATRIX && it.estaVigenteSemanal() }
    }

    fun obtenerTextosUsuario(usuarioId: String) = _publicaciones.map { lista ->
        lista.filter { it.usuarioId == usuarioId && it.tipo == TipoPublicacionPerfil.TEXTO_RETRO && it.estaVigenteSemanal() }
    }

    private fun guardarEnPrefs(lista: List<PublicacionPerfil>) {
        val sp = prefs ?: return
        runCatching {
            val jsonArray = JSONArray()
            for (pub in lista) {
                val obj = JSONObject().apply {
                    put("id", pub.id)
                    put("usuarioId", pub.usuarioId)
                    put("autorNombre", pub.autorNombre)
                    put("tipo", pub.tipo.name)
                    put("contenido", pub.contenido)
                    put("timestamp", pub.timestamp)
                    put("likes", pub.likes)
                    val likesArr = JSONArray()
                    pub.usuariosLike.forEach { likesArr.put(it) }
                    put("usuariosLike", likesArr)
                }
                jsonArray.put(obj)
            }
            sp.edit().putString(KEY_PUBLICACIONES, jsonArray.toString()).apply()
        }
    }
}
