package com.renea.arete_social.data.local.moderacion

import android.content.Context
import android.content.SharedPreferences
import com.renea.arete_social.domain.model.MotivoReporte
import com.renea.arete_social.domain.model.TipoReporte
import com.renea.arete_social.domain.repository.RepositorioModeracion

class RepositorioModeracionLocal(
    context: Context? = null
) : RepositorioModeracion {

    private val prefs: SharedPreferences? = context?.getSharedPreferences("antired_moderacion_local_prefs", Context.MODE_PRIVATE)

    
    override suspend fun estaBloqueado(usuarioId: String): Boolean {
        if (usuarioId.isBlank()) return false
        val bloqueados = prefs?.getStringSet("usuarios_bloqueados", emptySet()) ?: emptySet()
        return bloqueados.contains(usuarioId)
    }

    
    override suspend fun bloquearUsuario(usuarioId: String): Result<Unit> {
        if (usuarioId.isBlank()) return Result.success(Unit)
        val actual = prefs?.getStringSet("usuarios_bloqueados", emptySet())?.toMutableSet() ?: mutableSetOf()
        actual.add(usuarioId)
        prefs?.edit()?.putStringSet("usuarios_bloqueados", actual)?.apply()
        return Result.success(Unit)
    }

    
    override suspend fun desbloquearUsuario(usuarioId: String): Result<Unit> {
        if (usuarioId.isBlank()) return Result.success(Unit)
        val actual = prefs?.getStringSet("usuarios_bloqueados", emptySet())?.toMutableSet() ?: mutableSetOf()
        actual.remove(usuarioId)
        prefs?.edit()?.putStringSet("usuarios_bloqueados", actual)?.apply()
        return Result.success(Unit)
    }

    
    override suspend fun obtenerUsuariosBloqueados(): Set<String> {
        return prefs?.getStringSet("usuarios_bloqueados", emptySet()) ?: emptySet()
    }

    override suspend fun enviarReporte(
        tipo: TipoReporte,
        motivo: MotivoReporte,
        reportadoId: String?,
        referenciaId: String?,
        descripcion: String?,
        bloquearTambien: Boolean
    ): Result<Unit> {
        if (bloquearTambien && !reportadoId.isNullOrBlank()) {
            bloquearUsuario(reportadoId)
        }
        return Result.success(Unit)
    }
}
