package com.renea.arete_social.domain.model

/**
 * Roles unificados para comunidades en Aréte (AntiRed).
 * Alinea la nomenclatura del Ledger criptográfico (OWNER/LEADER/ADMIN/MODERATOR/MEMBER)
 * con la UI y los ViewModels en español.
 */
enum class RolComunidad(
    val codigo: String,
    val texto: String,
    val emoji: String,
    val rango: Int
) {
    OWNER("OWNER", "Creador", "👑", 0),
    LEADER("LEADER", "Líder", "🛡️", 1),
    ADMIN("ADMIN", "Admin", "⭐", 2),
    MODERATOR("MODERATOR", "Moderador", "🔧", 3),
    MEMBER("MEMBER", "Miembro", "👤", 4);

    /** Determina si el rol tiene privilegios de administración y gestión comunitaria. */
    fun puedeGestionar(): Boolean = this == OWNER || this == LEADER || this == ADMIN

    /** Determina si el rol puede publicar o crear eventos comunitarios. */
    fun puedeCrearEventos(): Boolean = this == OWNER || this == LEADER || this == ADMIN || this == MODERATOR

    /** Determina si el rol es el líder principal o propietario del espacio. */
    fun esLiderODueno(): Boolean = this == OWNER || this == LEADER

    companion object {
        fun desdeCodigo(codigo: String?): RolComunidad {
            if (codigo.isNullOrBlank()) return MEMBER
            return when (codigo.trim().uppercase()) {
                "OWNER", "CREADOR", "PROPIETARIO", "DUENO", "DUEÑO" -> OWNER
                "LEADER", "LIDER", "LÍDER" -> LEADER
                "ADMIN", "ADMINISTRADOR", "ADMIN DELEGADO" -> ADMIN
                "MODERATOR", "MODERADOR" -> MODERATOR
                else -> MEMBER
            }
        }
    }
}
