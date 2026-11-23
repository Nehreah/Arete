package com.renea.arete_social.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Almacenamiento local Room de las obras literarias de un par cercano (Fase 3 & 4).
 */
@Entity(
    tableName = "peer_libreria",
    indices = [
        Index(value = ["usuario_id"])
    ]
)
data class PeerLibreriaEntity(
    @PrimaryKey
    @ColumnInfo(name = "obra_id") val obraId: String,
    @ColumnInfo(name = "usuario_id") val usuarioId: String,
    @ColumnInfo(name = "titulo") val titulo: String,
    @ColumnInfo(name = "sinopsis") val sinopsis: String = "",
    @ColumnInfo(name = "tipo") val tipo: String = "CUENTO",
    @ColumnInfo(name = "caracteres_total") val caracteresTotal: Int = 0,
    @ColumnInfo(name = "contenido") val contenido: String? = null,
    @ColumnInfo(name = "actualizado_en") val actualizadoEn: Long = System.currentTimeMillis()
)
