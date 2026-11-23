package com.renea.arete_social.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Entidad Room para el estado de membresía de un miembro en una comunidad local.
 * Se actualiza cuando se recibe y valida una operación JOIN_ACCEPT, EXPEL, etc.
 */
@Entity(
    tableName = "community_member",
    primaryKeys = ["community_id_hex", "author_ik_hex"],
    indices = [
        Index(value = ["community_id_hex", "role"]),
        Index(value = ["community_id_hex", "status"])
    ]
)
data class CommunityMemberEntity(
    @ColumnInfo(name = "community_id_hex") val communityIdHex: String,
    @ColumnInfo(name = "author_ik_hex") val authorIkHex: String,

    @ColumnInfo(name = "role") val role: String, // "OWNER", "LEADER", "ADMIN", "MODERATOR", "MEMBER"
    /**
     * Estado de membresía:
     * - "REQUESTED" → solicitud enviada, pendiente de aprobación por el líder/admin.
     * - "ACTIVE"    → miembro activo confirmado por operación JOIN_ACCEPT.
     * - "EXPELLED"  → expulsado por el líder.
     * - "LEFT"      → salió voluntariamente.
     */
    @ColumnInfo(name = "status") val status: String = "ACTIVE",

    @ColumnInfo(name = "joined_at_owner_seq") val joinedAtOwnerSeq: Long,
    @ColumnInfo(name = "designated_by_ik_hex") val designatedByIkHex: String? = null,

    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long = System.currentTimeMillis()
)
