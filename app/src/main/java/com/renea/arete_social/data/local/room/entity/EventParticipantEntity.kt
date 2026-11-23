package com.renea.arete_social.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Entidad Room para almacenar los participantes y estado de RSVP de eventos P2P.
 */
@Entity(
    tableName = "local_event_participant",
    primaryKeys = ["evento_id", "usuario_id"],
    indices = [
        Index(value = ["evento_id"]),
        Index(value = ["usuario_id"])
    ]
)
data class EventParticipantEntity(
    @ColumnInfo(name = "evento_id") val eventoId: String,
    @ColumnInfo(name = "usuario_id") val usuarioId: String,
    @ColumnInfo(name = "rol") val rol: String = "ASISTENTE",
    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long = System.currentTimeMillis()
)
