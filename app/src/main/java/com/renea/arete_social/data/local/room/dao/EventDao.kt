package com.renea.arete_social.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.renea.arete_social.data.local.room.entity.EventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvent(event: EventEntity)

    @Query("SELECT * FROM local_event WHERE id = :id")
    suspend fun getEvent(id: String): EventEntity?

    @Query("SELECT * FROM local_event WHERE is_cancelled = 0 AND date_time_ms >= :afterTimeMs ORDER BY date_time_ms ASC")
    fun observeUpcomingEvents(afterTimeMs: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM local_event WHERE community_id_hex = :communityIdHex AND is_cancelled = 0 ORDER BY date_time_ms ASC")
    suspend fun getEventsForCommunity(communityIdHex: String): List<EventEntity>

    @Query("UPDATE local_event SET is_cancelled = 1, updated_at_ms = :updatedAtMs WHERE id = :id")
    suspend fun cancelEvent(id: String, updatedAtMs: Long = System.currentTimeMillis())

    @Query("SELECT * FROM local_event WHERE is_cancelled = 0 ORDER BY date_time_ms ASC")
    suspend fun getAllEvents(): List<EventEntity>

    @Query("SELECT * FROM local_event WHERE is_cancelled = 0 AND organizer_ik_hex = :organizerIkHex ORDER BY date_time_ms ASC")
    suspend fun getEventsByOrganizer(organizerIkHex: String): List<EventEntity>

    @Query("DELETE FROM local_event WHERE id = :id")
    suspend fun deleteEvent(id: String)

    @Query("SELECT * FROM local_event WHERE (privacy = 'PUBLIC' OR privacy = 'PUBLICO') AND is_cancelled = 0 ORDER BY updated_at_ms DESC")
    suspend fun getPublicEvents(): List<EventEntity>

    @Query("SELECT * FROM local_event WHERE updated_at_ms > :sinceMs ORDER BY updated_at_ms ASC")
    suspend fun getEventsModifiedSince(sinceMs: Long): List<EventEntity>

    @Query("SELECT * FROM local_event WHERE id = :id")
    fun observeEvent(id: String): Flow<EventEntity?>

    @Query("SELECT * FROM local_event ORDER BY date_time_ms ASC")
    suspend fun getAllEventsIncludingCancelled(): List<EventEntity>

    @Query("SELECT * FROM local_event WHERE is_cancelled = 0 AND organizer_ik_hex IN (:organizerIds) ORDER BY date_time_ms ASC")
    suspend fun getEventsByOrganizerIds(organizerIds: List<String>): List<EventEntity>
}
