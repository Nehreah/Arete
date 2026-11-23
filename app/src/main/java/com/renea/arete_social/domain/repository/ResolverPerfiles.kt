package com.renea.arete_social.domain.repository

import com.renea.arete_social.domain.model.SnapshotPerfil
import kotlinx.coroutines.flow.Flow

data class PerfilPar(
    val usuarioId: String,
    val nombre: String,
    val descripcion: String = "",
    val foto: String? = null,
    val portada: String? = null,
    val esAmigo: Boolean = false,
    val ultimaVezVisto: Long = 0L
)

/**
 * Directorio y resolvedor unificado de perfiles de pares (vecinos BLE, chat y perfiles recibidos).
 * Asegura una sola fuente de verdad para nombres y avatares (interfaz pura de dominio).
 */
interface ResolverPerfiles {
    suspend fun resolverNombre(usuarioId: String): String
    suspend fun resolverFoto(usuarioId: String): String?
    suspend fun obtenerPerfil(usuarioId: String): PerfilPar?
    fun observarPerfil(usuarioId: String): Flow<PerfilPar?>
    suspend fun registrarParDescubierto(usuarioId: String, nombreProvisional: String?)
    suspend fun registrarPerfilRecibido(snapshot: SnapshotPerfil)
    suspend fun registrarInteraccionMensaje(remitenteId: String, remitenteNombre: String? = null, remitenteFoto: String? = null)
    suspend fun actualizarRutaMediaPar(usuarioId: String, hashSha256: String, rutaArchivo: String)
    suspend fun podarDesconocidos(limiteTimestamp: Long)
}
