package com.renea.arete_social.domain.repository

import com.renea.arete_social.domain.model.Comunidad
import com.renea.arete_social.feature.comunidades.ArchivoComunidad
import com.renea.arete_social.feature.comunidades.PublicacionMuro
import kotlinx.coroutines.flow.Flow

interface RepositorioComunidades {
    suspend fun crearComunidad(
        nombre: String,
        descripcion: String,
        tipo: String,
        fotoBytes: ByteArray? = null,
        extension: String = "jpg"
    ): Comunidad
    suspend fun listarComunidades(): List<Comunidad>
    suspend fun obtenerComunidad(id: String): Comunidad?
    fun observarComunidad(id: String): Flow<Comunidad?>
    suspend fun unirseAComunidad(comunidadId: String, usuarioId: String)
    suspend fun salirDeComunidad(comunidadId: String, usuarioId: String)
    suspend fun transferirLiderazgo(comunidadId: String, nuevoLiderId: String)
    suspend fun asignarRol(comunidadId: String, usuarioId: String, rol: String)
    suspend fun obtenerRolMiembro(comunidadId: String, usuarioId: String): String?
    suspend fun listarMiembrosConRoles(comunidadId: String): Map<String, String>
    suspend fun actualizarComunidad(id: String, nombre: String, descripcion: String, tipo: String, fotoUrl: String?)
    suspend fun eliminarComunidad(comunidadId: String)
    suspend fun subirAvatarComunidad(comunidadId: String, bytes: ByteArray, extension: String = "jpg"): String
    suspend fun esMiembro(comunidadId: String, usuarioId: String): Boolean
    suspend fun listarMiembrosIds(comunidadId: String): List<String>

    suspend fun crearPublicacionMuro(
        comunidadId: String,
        autorId: String,
        autorNombre: String,
        contenido: String,
        imagenBytes: ByteArray? = null,
        extension: String = "jpg",
        categoria: String = "Todos"
    ): PublicacionMuro

    suspend fun listarPublicacionesMuro(comunidadId: String): List<PublicacionMuro>
    suspend fun toggleLikePublicacion(comunidadId: String, publicacionId: String, usuarioId: String): PublicacionMuro?

    suspend fun subirArchivoComunidad(
        comunidadId: String,
        autorId: String,
        autorNombre: String,
        nombreArchivo: String,
        bytes: ByteArray,
        tipoMime: String
    ): ArchivoComunidad

    suspend fun listarArchivosComunidad(comunidadId: String): List<ArchivoComunidad>

    fun solicitarArchivosLan(comunidadId: String, destinoPeerId: String) {}
    fun descargarArchivoLan(comunidadId: String, archivoId: String, destinoPeerId: String) {}

    // ─── Bandeja de aprobación (comunidades privadas) ───

    /**
     * Devuelve los IDs de los solicitantes pendientes de aprobación en una comunidad privada.
     * Solo tiene sentido para el líder/admin de la comunidad.
     */
    suspend fun listarSolicitudesPendientes(comunidadId: String): List<String>

    /**
     * Aprueba la solicitud de [solicitanteId], emitiendo una operación TYPE_JOIN_ACCEPT
     * firmada en el ledger de la comunidad.
     */
    suspend fun aprobarMiembro(comunidadId: String, solicitanteId: String)
}