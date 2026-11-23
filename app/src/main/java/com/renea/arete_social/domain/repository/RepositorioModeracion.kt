package com.renea.arete_social.domain.repository

import com.renea.arete_social.domain.model.MotivoReporte
import com.renea.arete_social.domain.model.TipoReporte

/**
 * Contrato para la moderación comunitaria y protección entre usuarios (UGC).
 */
interface RepositorioModeracion {
    /** Devuelve true si [usuarioId] está actualmente bloqueado por el usuario de la sesión. */
    suspend fun estaBloqueado(usuarioId: String): Boolean

    /** Bloquea a [usuarioId], impidiendo mensajes, encuentros y visibilidad mutua. */
    suspend fun bloquearUsuario(usuarioId: String): Result<Unit>

    /** Desbloquea a [usuarioId]. */
    suspend fun desbloquearUsuario(usuarioId: String): Result<Unit>

    /** Obtiene el conjunto de IDs de usuarios bloqueados por la sesión actual. */
    suspend fun obtenerUsuariosBloqueados(): Set<String>

    /**
     * Envía un reporte formal de moderación a los servidores de Aréte.
     * Si [bloquearTambien] es true, bloquea de forma inmediata al usuario reportado.
     */
    suspend fun enviarReporte(
        tipo: TipoReporte,
        motivo: MotivoReporte,
        reportadoId: String?,
        referenciaId: String?,
        descripcion: String?,
        bloquearTambien: Boolean = false
    ): Result<Unit>
}
