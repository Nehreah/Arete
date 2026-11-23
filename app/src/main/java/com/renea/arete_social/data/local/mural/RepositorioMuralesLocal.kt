package com.renea.arete_social.data.local.mural

import android.content.Context
import com.renea.arete_social.data.local.media.AlmacenMediaPerfil
import com.renea.arete_social.domain.model.PublicacionMural
import com.renea.arete_social.feature.comunidades.PublicacionMuro
import com.renea.arete_social.feature.comunidades.data.GestorMuroComunidades
import java.util.UUID

/**
 * Repositorio de murales comunitarios y globales 100% Local-First.
 * Persiste las publicaciones en disco del dispositivo sin depender de servidores centrales.
 */
class RepositorioMuralesLocal(private val context: Context) {

    suspend fun listar(comunidadId: String? = null): List<PublicacionMural> {
        val cid = comunidadId ?: "mural_global"
        GestorMuroComunidades.inicializar(context)
        return GestorMuroComunidades.obtenerPublicaciones(cid).map { pm ->
            PublicacionMural(
                id = pm.id,
                autorId = pm.autorId,
                autorNombre = pm.autor,
                contenido = pm.texto,
                imagenUrl = pm.imagenUri,
                comunidadId = comunidadId,
                creadaEn = System.currentTimeMillis()
            )
        }
    }

    suspend fun publicarTexto(autorId: String, autorNombre: String, texto: String, comunidadId: String?) {
        val limpio = texto.trim().take(2000)
        require(limpio.isNotEmpty()) { "Escribe algo antes de publicar." }
        val cid = comunidadId ?: "mural_global"
        GestorMuroComunidades.inicializar(context)
        val pm = PublicacionMuro(
            id = UUID.randomUUID().toString(),
            autorId = autorId,
            autor = autorNombre,
            tiempo = "Ahora",
            texto = limpio
        )
        GestorMuroComunidades.guardarPublicacion(cid, pm)
    }

    suspend fun publicarFoto(autorId: String, autorNombre: String, jpeg: ByteArray, comunidadId: String?, texto: String = "") {
        require(jpeg.isNotEmpty()) { "La foto está vacía." }
        val cid = comunidadId ?: "mural_global"
        GestorMuroComunidades.inicializar(context)
        val res = AlmacenMediaPerfil.guardarImagen(jpeg, esAvatar = false, extension = "jpg")
        val pm = PublicacionMuro(
            id = UUID.randomUUID().toString(),
            autorId = autorId,
            autor = autorNombre,
            tiempo = "Ahora",
            texto = texto.trim().take(2000),
            imagenUri = res.rutaArchivo
        )
        GestorMuroComunidades.guardarPublicacion(cid, pm)
    }
}
