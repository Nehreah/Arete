package com.renea.arete_social.data.local.perfil

import android.util.Base64
import com.renea.arete_social.domain.model.PublicacionPerfil
import com.renea.arete_social.domain.repository.RepositorioPublicacionesPerfil
import com.renea.arete_social.feature.auth.GestorPerfilEfemero

class RepositorioPublicacionesPerfilLocal : RepositorioPublicacionesPerfil {

    override suspend fun listar(autorId: String): List<PublicacionPerfil> {
        return GestorPerfilEfemero.publicaciones.value.filter { it.usuarioId == autorId }
    }

    override suspend fun publicarTexto(
        autorId: String,
        autorNombre: String,
        texto: String
    ): PublicacionPerfil {
        return GestorPerfilEfemero.agregarPublicacionTexto(autorId, autorNombre, texto)
            ?: PublicacionPerfil(
                usuarioId = autorId,
                autorNombre = autorNombre,
                tipo = com.renea.arete_social.domain.model.TipoPublicacionPerfil.TEXTO_RETRO,
                contenido = texto
            )
    }

    override suspend fun publicarFoto(
        autorId: String,
        autorNombre: String,
        jpegBytes: ByteArray
    ): PublicacionPerfil {
        val b64 = "data:image/jpeg;base64," + Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        return GestorPerfilEfemero.agregarPublicacionFoto(autorId, autorNombre, b64)
            ?: PublicacionPerfil(
                usuarioId = autorId,
                autorNombre = autorNombre,
                tipo = com.renea.arete_social.domain.model.TipoPublicacionPerfil.FOTO_MATRIX,
                contenido = b64
            )
    }

    override suspend fun eliminar(publicacion: PublicacionPerfil) {
        GestorPerfilEfemero.eliminarPublicacion(publicacion.id)
    }

    override suspend fun definirLike(publicacionId: String, miId: String, darLike: Boolean) {
        GestorPerfilEfemero.toggleLike(publicacionId, miId)
    }

    override suspend fun limpiarVencidasPropias(autorId: String) = Unit
}
