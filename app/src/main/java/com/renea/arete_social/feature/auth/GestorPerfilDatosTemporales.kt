package com.renea.arete_social.feature.auth

import android.content.Context
import android.content.SharedPreferences
import com.renea.arete_social.data.local.media.AlmacenMediaPerfil
import com.renea.arete_social.domain.model.Usuario
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Datos de perfil persistentes para personas en modo invitado o local.
 * Asegura que gustos, hobbies, avatar y portada sobrevivan al cierre y reinicio de la app,
 * guardándolos en preferencias locales y gestionando los medios mediante archivos en disco.
 */
object GestorPerfilDatosTemporales {

    private const val PREFS_NAME = "antired_perfil_invitados_prefs"
    private const val KEY_PREFIX_DATOS = "invitado_datos_"
    private const val KEY_ENCUESTA_OMITIDA = "encuesta_omitida_set"

    private var prefs: SharedPreferences? = null
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val perfiles = mutableMapOf<String, Datos>()
    private val encuestaOmitida = mutableSetOf<String>()

    fun inicializar(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        p.all.forEach { (key, value) ->
            if (key.startsWith(KEY_PREFIX_DATOS) && value is String) {
                val id = key.removePrefix(KEY_PREFIX_DATOS)
                runCatching {
                    val datos = json.decodeFromString<Datos>(value)
                    val fotoMigrada = AlmacenMediaPerfil.migrarSiEsDataUrl(datos.foto, esAvatar = true) ?: datos.foto
                    val portadaMigrada = AlmacenMediaPerfil.migrarSiEsDataUrl(datos.portada, esAvatar = false) ?: datos.portada
                    val datosFinales = datos.copy(foto = fotoMigrada, portada = portadaMigrada)
                    perfiles[id] = datosFinales
                }
            }
        }
        p.getStringSet(KEY_ENCUESTA_OMITIDA, null)?.let {
            encuestaOmitida.addAll(it)
        }
    }

    internal fun inicializarConPrefs(prefsPrueba: SharedPreferences) {
        prefs = prefsPrueba
        perfiles.clear()
        encuestaOmitida.clear()
    }

    fun fueEncuestaOmitida(id: String): Boolean = id in encuestaOmitida

    fun marcarEncuestaOmitida(id: String) {
        encuestaOmitida.add(id)
        prefs?.edit()?.putStringSet(KEY_ENCUESTA_OMITIDA, encuestaOmitida)?.apply()
    }

    @Serializable
    data class Datos(
        val nombre: String = "",
        val hobby: String = "",
        val deporte: String = "",
        val ritmoMusical: String = "",
        val serieFavorita: String = "",
        val libroFavorito: String = "",
        val signoZodiacal: String = "",
        val foto: String = "",
        val portada: String = ""
    )

    fun obtener(id: String): Datos = perfiles[id] ?: Datos()

    fun guardar(id: String, datos: Datos) {
        val fotoMigrada = AlmacenMediaPerfil.migrarSiEsDataUrl(datos.foto, esAvatar = true) ?: datos.foto
        val portadaMigrada = AlmacenMediaPerfil.migrarSiEsDataUrl(datos.portada, esAvatar = false) ?: datos.portada
        val datosAjustados = datos.copy(foto = fotoMigrada, portada = portadaMigrada)

        perfiles[id] = datosAjustados
        prefs?.let { p ->
            runCatching {
                val str = json.encodeToString(datosAjustados)
                p.edit().putString("$KEY_PREFIX_DATOS$id", str).apply()
            }
        }
    }

    fun aplicar(usuario: Usuario): Usuario {
        val d = obtener(usuario.id)
        val fotoResuelta = AlmacenMediaPerfil.migrarSiEsDataUrl(d.foto.ifBlank { null } ?: usuario.foto, esAvatar = true)
        val portadaResuelta = AlmacenMediaPerfil.migrarSiEsDataUrl(d.portada.ifBlank { null } ?: usuario.portada, esAvatar = false)

        return usuario.copy(
            nombre = d.nombre.ifBlank { usuario.nombre },
            hobby = d.hobby.ifBlank { usuario.hobby },
            deporte = d.deporte.ifBlank { usuario.deporte },
            ritmoMusical = d.ritmoMusical.ifBlank { usuario.ritmoMusical },
            serieFavorita = d.serieFavorita.ifBlank { usuario.serieFavorita },
            libroFavorito = d.libroFavorito.ifBlank { usuario.libroFavorito },
            signoZodiacal = d.signoZodiacal.ifBlank { usuario.signoZodiacal },
            foto = fotoResuelta,
            portada = portadaResuelta
        )
    }

    fun limpiar() {
        perfiles.clear()
        encuestaOmitida.clear()
        prefs?.edit()?.clear()?.apply()
    }
}
