package com.renea.arete_social.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.renea.arete_social.data.local.room.entity.PeerProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PeerProfileDao {

    @Query("SELECT * FROM peer_profile WHERE usuario_id = :usuarioId LIMIT 1")
    suspend fun getPeer(usuarioId: String): PeerProfileEntity?

    @Query("SELECT * FROM peer_profile WHERE usuario_id = :usuarioId LIMIT 1")
    fun observePeer(usuarioId: String): Flow<PeerProfileEntity?>

    @Query("SELECT * FROM peer_profile ORDER BY ultima_vez_visto DESC")
    fun observeAllPeers(): Flow<List<PeerProfileEntity>>

    @Query("SELECT * FROM peer_profile ORDER BY ultima_vez_visto DESC")
    suspend fun getAllPeers(): List<PeerProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(peer: PeerProfileEntity)

    /** Devuelve solo los peers vistos después de [limiteTimestamp] (epoch ms). */
    @Query("SELECT * FROM peer_profile WHERE ultima_vez_visto >= :limiteTimestamp ORDER BY ultima_vez_visto DESC")
    suspend fun getRecentPeers(limiteTimestamp: Long): List<PeerProfileEntity>

    @Query("UPDATE peer_profile SET ultima_vez_visto = :timestamp WHERE usuario_id = :usuarioId")
    suspend fun updateLastSeen(usuarioId: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM peer_profile WHERE usuario_id = :usuarioId")
    suspend fun deletePeer(usuarioId: String)

    @Query("DELETE FROM peer_profile WHERE es_amigo = 0 AND ultima_vez_visto < :limiteTimestamp")
    suspend fun podarDesconocidosAntiguos(limiteTimestamp: Long)
}
