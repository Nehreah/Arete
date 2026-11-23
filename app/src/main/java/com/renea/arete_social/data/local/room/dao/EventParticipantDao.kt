package com.renea.arete_social.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.renea.arete_social.data.local.room.entity.EventParticipantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventParticipantDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertParticipant(participant: EventParticipantEntity)

    @Query("SELECT * FROM local_event_participant WHERE evento_id = :eventoId ORDER BY updated_at_ms ASC")
    suspend fun getParticipants(eventoId: String): List<EventParticipantEntity>

    @Query("SELECT * FROM local_event_participant WHERE evento_id = :eventoId ORDER BY updated_at_ms ASC")
    fun observeParticipants(eventoId: String): Flow<List<EventParticipantEntity>>

    @Query("DELETE FROM local_event_participant WHERE evento_id = :eventoId AND usuario_id = :usuarioId")
    suspend fun removeParticipant(eventoId: String, usuarioId: String)

    @Query("DELETE FROM local_event_participant WHERE evento_id = :eventoId")
    suspend fun clearParticipantsForEvent(eventoId: String)
}
