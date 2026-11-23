package com.renea.arete_social.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Directorio unificado de perfiles de pares (vecinos descubiertos por BLE, contactos de chat y perfiles recibidos).
 * Sirve como fuente de verdad única para resolver nombres y avatares en Chat, Mapa y Perfil Público sin internet.
 */
@Entity(tableName = "peer_profile")
data class PeerProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "usuario_id") val usuarioId: String,
    @ColumnInfo(name = "nombre") val nombre: String,
    @ColumnInfo(name = "descripcion") val descripcion: String = "",
    @ColumnInfo(name = "foto_path") val fotoPath: String? = null,
    @ColumnInfo(name = "foto_hash") val fotoHash: String? = null,
    @ColumnInfo(name = "portada_path") val portadaPath: String? = null,
    @ColumnInfo(name = "portada_hash") val portadaHash: String? = null,
    @ColumnInfo(name = "perfil_ver") val perfilVer: Long = 0L,
    @ColumnInfo(name = "es_amigo") val esAmigo: Boolean = false,
    @ColumnInfo(name = "es_privado") val esPrivado: Boolean = false,
    @ColumnInfo(name = "ultima_vez_visto") val ultimaVezVisto: Long = System.currentTimeMillis()
)
