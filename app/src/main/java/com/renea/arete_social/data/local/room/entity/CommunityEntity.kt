package com.renea.arete_social.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Entidad Room que representa una comunidad conocida por este dispositivo.
 * El communityId es el hash SHA-256 de la operación génesis (clave primaria de hecho).
 */
@Entity(
    tableName = "community",
    primaryKeys = ["community_id_hex"]
)
data class CommunityEntity(
    @ColumnInfo(name = "community_id_hex") val communityIdHex: String, // SHA-256 génesis en hex

    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "description") val description: String,

    @ColumnInfo(name = "owner_ik_hex") val ownerIkHex: String,
    @ColumnInfo(name = "leader_ik_hex") val leaderIkHex: String? = null,

    @ColumnInfo(name = "current_head_seq") val currentHeadSeq: Long = 0L,
    @ColumnInfo(name = "current_head_hash_hex") val currentHeadHashHex: String = "0".repeat(64),
    @ColumnInfo(name = "current_epoch_id_hex") val currentEpochIdHex: String = "0".repeat(64),

    @ColumnInfo(name = "rules_version") val rulesVersion: Int = 1,
    @ColumnInfo(name = "is_dissolved") val isDissolved: Boolean = false,
    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "tipo") val tipo: String = "PRIVADA",
    @ColumnInfo(name = "foto_url") val fotoUrl: String? = null,
    @ColumnInfo(name = "portada_url") val portadaUrl: String? = null,
    @ColumnInfo(name = "foto_hash") val fotoHash: String? = null
)
