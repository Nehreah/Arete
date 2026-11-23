package com.renea.arete_social.domain.repository

import com.renea.arete_social.domain.model.PublicacionPerfil

/**
 * Estados (texto) y fotos del perfil, con vigencia de 7 días. Viven en el servidor: quien abre
 * un perfil las pide al servidor, que solo las entrega si el perfil es público o si son amistades.
 */
interface RepositorioPublicacionesPerfil {
    /**
     * Publicaciones vigentes de [autorId] visibles para quien pregunta, más recientes primero.
     * En las fotos, [PublicacionPerfil.contenido] es una URL firmada temporal.
     * Si el perfil no es visible para quien pregunta, la lista llega vacía.
     */
    suspend fun listar(autorId: String): List<PublicacionPerfil>

    suspend fun publicarTexto(autorId: String, autorNombre: String, texto: String): PublicacionPerfil

    /** [jpegBytes] ya comprimidos (la pantalla de perfil los reduce a JPEG antes de publicar). */
    suspend fun publicarFoto(autorId: String, autorNombre: String, jpegBytes: ByteArray): PublicacionPerfil

    suspend fun eliminar(publicacion: PublicacionPerfil)

    /** Pone o quita mi like. */
    suspend fun definirLike(publicacionId: String, miId: String, darLike: Boolean)

    /** Borra del servidor las publicaciones propias que ya pasaron de 7 días (y sus fotos). */
    suspend fun limpiarVencidasPropias(autorId: String)
}
