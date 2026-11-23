package com.renea.arete_social.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidad Room para Eventos Locales P2P según la sección 11 de la auditoría v3.
 *
 * Cada evento vive localmente y se comparte por P2P (o por el relé):
 * Posee organizador (IK), coordenadas geográficas para el radar/mapa,
 * asociación comunitaria opcional y control causal de cancelación.
 */
@Entity(
    tableName = "local_event",
    indices = [
        Index(value = ["community_id_hex"]),
        Index(value = ["date_time_ms"]),
        Index(value = ["organizer_ik_hex"])
    ]
)
data class EventEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,

    @ColumnInfo(name = "organizer_ik_hex") val organizerIkHex: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "date_time_ms") val dateTimeMs: Long,

    @ColumnInfo(name = "latitude") val latitude: Double? = null,
    @ColumnInfo(name = "longitude") val longitude: Double? = null,
    @ColumnInfo(name = "location_name") val locationName: String = "",

    @ColumnInfo(name = "privacy") val privacy: String = "COMMUNITY", // "PUBLIC", "COMMUNITY", "PRIVATE"
    @ColumnInfo(name = "community_id_hex") val communityIdHex: String? = null,

    @ColumnInfo(name = "is_cancelled") val isCancelled: Boolean = false,
    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "firma_organizador") val firmaOrganizador: String? = null,
    @ColumnInfo(name = "version") val version: Long = 1L,
    @ColumnInfo(name = "foto_url") val fotoUrl: String? = null,
    @ColumnInfo(name = "foto_hash") val fotoHash: String? = null
)
