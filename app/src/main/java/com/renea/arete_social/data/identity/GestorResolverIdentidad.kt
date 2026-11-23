package com.renea.arete_social.data.identity

import com.renea.arete_social.domain.repository.ResolverIdentidad
import com.renea.arete_social.domain.util.toHex
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementación predeterminada en memoria con resolución soberana de la identidad local.
 */
class GestorResolverIdentidad(
    private val identityManager: IdentityManager,
    private val miUsuarioIdProveedor: () -> String?
) : ResolverIdentidad {

    private val usuarioAIk = ConcurrentHashMap<String, String>()
    private val ikAUsuario = ConcurrentHashMap<String, String>()

    override fun miUsuarioId(): String? = miUsuarioIdProveedor()

    override fun miIkHex(): String {
        if (!identityManager.isLoaded) {
            identityManager.generateAndLoad()
        }
        return identityManager.ikPublicBytes.toHex()
    }

    override fun esMio(identificador: String?): Boolean {
        if (identificador.isNullOrBlank()) return false
        val miId = miUsuarioId()
        if (miId != null && identificador.equals(miId, ignoreCase = true)) return true
        val miIk = runCatching { miIkHex() }.getOrNull()
        if (miIk != null && identificador.equals(miIk, ignoreCase = true)) return true
        return false
    }

    override fun ikDe(usuarioId: String): String? {
        if (esMio(usuarioId)) return miIkHex()
        return usuarioAIk[usuarioId]
    }

    override fun usuarioDe(ikHex: String): String? {
        if (esMio(ikHex)) return miUsuarioId() ?: ikHex
        return ikAUsuario[ikHex.lowercase()] ?: ikHex
    }

    override fun registrarVinculo(usuarioId: String, ikHex: String) {
        val cleanIk = ikHex.lowercase()
        usuarioAIk[usuarioId] = cleanIk
        ikAUsuario[cleanIk] = usuarioId
    }
}
