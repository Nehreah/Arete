package com.renea.arete_social.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Almacenamiento local Room de los gustos e intereses recibidos de un par cercano (Fase 3 & 4).
 */
@Entity(tableName = "peer_gustos")
data class PeerGustosEntity(
    @PrimaryKey
    @ColumnInfo(name = "usuario_id") val usuarioId: String,
    @ColumnInfo(name = "intereses_json") val interesesJson: String,
    @ColumnInfo(name = "categorias_json") val categoriasJson: String = "[]",
    @ColumnInfo(name = "actualizado_en") val actualizadoEn: Long = System.currentTimeMillis()
)
