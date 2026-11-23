package com.renea.arete_social.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Identidad local del usuario: contiene la clave pública de identidad (IK) y los metadatos
 * de la frase de recuperación. La clave privada IK no se guarda aquí; vive derivada
 * en memoria a partir de la frase o en el Keystore para los dispositivos.
 */
@Entity(tableName = "local_identity")
data class LocalIdentityEntity(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "id") val id: Int = 1, // Solo una identidad activa por instancia

    @ColumnInfo(name = "ik_public_hex") val ikPublicHex: String, // 65 bytes P-256 sin comprimir como hex
    @ColumnInfo(name = "device_id") val deviceId: String,         // UUID del dispositivo
    @ColumnInfo(name = "device_epoch") val deviceEpoch: Int,

    // Verificador de que el usuario anotó la frase (SHA-256 de la frase, no la frase misma)
    @ColumnInfo(name = "phrase_hash_hex") val phraseHashHex: String,

    // Respaldo cifrado opcional (Argon2id)
    @ColumnInfo(name = "backup_hint") val backupHint: String? = null,

    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long = System.currentTimeMillis()
)
