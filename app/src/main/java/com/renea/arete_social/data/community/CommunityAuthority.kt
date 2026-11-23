package com.renea.arete_social.data.community

/**
 * Modelo de autoridad jerárquica y delegación verificable para comunidades en Aréte,
 * según la sección 7.2 y 9.2 de la auditoría arquitectónica v3.
 *
 * El dueño es la máxima autoridad y designa al líder. No hay votaciones:
 * la autoridad se ejerce por delegación firmada y verificable.
 */
object CommunityAuthority {

    enum class Role(val rank: Int) {
        OWNER(0),
        LEADER(1),
        ADMIN(2),
        MODERATOR(3),
        MEMBER(4);

        /**
         * Regla 7.2: Un rol no puede modificar a otro de rango igual o superior.
         * Para que el actor pueda modificar al sujeto, su rango numérico debe ser menor (mayor jerarquía).
         */
        fun canModify(subjectRole: Role): Boolean {
            return this.rank < subjectRole.rank
        }
    }

    /**
     * Permisos específicos delegables
     */
    enum class Permission {
        APPROVE_JOIN,
        REJECT_JOIN,
        EXPEL_MEMBER,
        ROTATE_EPOCH,
        MODERATE_CONTENT,
        DESIGNATE_ADMIN,
        DESIGNATE_MODERATOR,
        TRANSFER_OWNERSHIP,
        DESIGNATE_HEIR
    }

    /**
     * Evalúa si un rol posee por defecto la facultad de ejecutar una acción.
     */
    fun hasPermission(role: Role, permission: Permission): Boolean {
        return when (permission) {
            Permission.TRANSFER_OWNERSHIP,
            Permission.DESIGNATE_HEIR -> role == Role.OWNER

            Permission.DESIGNATE_ADMIN -> role == Role.OWNER || role == Role.LEADER

            Permission.DESIGNATE_MODERATOR -> role == Role.OWNER || role == Role.LEADER || role == Role.ADMIN

            Permission.APPROVE_JOIN,
            Permission.REJECT_JOIN,
            Permission.EXPEL_MEMBER,
            Permission.ROTATE_EPOCH -> role in listOf(Role.OWNER, Role.LEADER, Role.ADMIN)

            Permission.MODERATE_CONTENT -> role in listOf(Role.OWNER, Role.LEADER, Role.ADMIN, Role.MODERATOR)
        }
    }

    /**
     * Valida si un cambio de rol es legítimo según la jerarquía de quien lo ejecuta.
     */
    fun validateRoleChange(
        actorRole: Role,
        currentSubjectRole: Role,
        targetSubjectRole: Role
    ): Boolean {
        // El actor debe ser de mayor rango que el rol actual del sujeto
        if (!actorRole.canModify(currentSubjectRole)) return false

        // El actor no puede promover al sujeto a un rango igual o superior al suyo
        if (targetSubjectRole.rank <= actorRole.rank) return false

        return true
    }
}
