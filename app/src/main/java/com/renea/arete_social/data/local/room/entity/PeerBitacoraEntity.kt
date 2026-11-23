package com.renea.arete_social.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Almacenamiento local Room de los estados de bitácora recibidos de un par cercano (Fase 3 & 4).
 */
@Entity(
    tableName = "peer_bitacora",
    indices = [
        Index(value = ["usuario_id"]),
        Index(value = ["fecha"])
    ]
)
data class PeerBitacoraEntity(
    @PrimaryKey
    @ColumnInfo(name = "estado_id") val estadoId: String,
    @ColumnInfo(name = "usuario_id") val usuarioId: String,
    @ColumnInfo(name = "texto") val texto: String,
    @ColumnInfo(name = "foto_url") val fotoUrl: String? = null,
    @ColumnInfo(name = "foto_hash") val fotoHash: String? = null,
    @ColumnInfo(name = "fecha") val fecha: Long = System.currentTimeMillis()
)
