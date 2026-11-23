package com.renea.arete_social.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Entidad Room para las claves de época de comunidades.
 * Almacena la clave Ke cifrada (nunca en texto claro) o ya descifrada en memoria.
 * La clave en este registro es el ke_hex solo para dispositivos autorizados;
 * en un futuro se puede cifrar con la clave de Keystore del dispositivo.
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

    // ke_hex: clave de 32 bytes en hex. Almacenada SOLO cuando el dispositivo ha recibido y descifrado su EpochKeyShare.
    // En producción debería protegerse con el Android Keystore del dispositivo.
    @ColumnInfo(name = "ke_hex") val keHex: String,

    @ColumnInfo(name = "is_canonical") val isCanonical: Boolean = true,
    @ColumnInfo(name = "received_at_ms") val receivedAtMs: Long = System.currentTimeMillis()
)
