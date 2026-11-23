package com.renea.arete_social.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Entidad Room para las claves de época de comunidades.
 * Almacena la clave Ke CIFRADA en reposo (ENC:v1:<iv>:<ct>) mediante EpochKeyStorageProtector
 * respaldado por el Android Keystore (ARETE-SEC-001). Nunca se persiste en texto plano.
 */
@Entity(
    tableName = "epoch_key",
    primaryKeys = ["community_id_hex", "epoch_id_hex"],
    indices = [
        Index(value = ["community_id_hex", "is_canonical"])
    ]
)
data class EpochKeyEntity(
    @ColumnInfo(name = "community_id_hex") val communityIdHex: String,
    @ColumnInfo(name = "epoch_id_hex") val epochIdHex: String,

    // ke_hex: clave de época cifrada en reposo con AES-256-GCM ("ENC:v1:<iv_b64>:<ct_b64>")
    @ColumnInfo(name = "ke_hex") val keHex: String,

    @ColumnInfo(name = "is_canonical") val isCanonical: Boolean = true,
    @ColumnInfo(name = "received_at_ms") val receivedAtMs: Long = System.currentTimeMillis()
)
