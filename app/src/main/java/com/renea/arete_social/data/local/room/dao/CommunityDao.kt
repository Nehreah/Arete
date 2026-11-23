package com.renea.arete_social.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.renea.arete_social.data.local.room.entity.CommunityEntity
import com.renea.arete_social.data.local.room.entity.CommunityMemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommunityDao {

    // ─── Comunidades ───

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCommunity(community: CommunityEntity)

    @Update
    suspend fun updateCommunity(community: CommunityEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCommunity(community: CommunityEntity)

    @Query("SELECT * FROM community WHERE community_id_hex = :communityIdHex")
    suspend fun getCommunity(communityIdHex: String): CommunityEntity?

    @Query("SELECT * FROM community WHERE is_dissolved = 0 ORDER BY updated_at_ms DESC")
    fun observeActiveCommunities(): Flow<List<CommunityEntity>>

    @Query("SELECT * FROM community ORDER BY updated_at_ms DESC")
    suspend fun getAllCommunities(): List<CommunityEntity>

    @Query("UPDATE community SET current_head_seq = :headSeq, current_head_hash_hex = :headHashHex, updated_at_ms = :updatedAt WHERE community_id_hex = :communityIdHex")
    suspend fun updateHead(communityIdHex: String, headSeq: Long, headHashHex: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE community SET current_epoch_id_hex = :epochIdHex, updated_at_ms = :updatedAt WHERE community_id_hex = :communityIdHex")
    suspend fun updateEpoch(communityIdHex: String, epochIdHex: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE community SET leader_ik_hex = :leaderIkHex, updated_at_ms = :updatedAt WHERE community_id_hex = :communityIdHex")
    suspend fun updateLeader(communityIdHex: String, leaderIkHex: String?, updatedAt: Long = System.currentTimeMillis())

    // ─── Miembros ───

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMember(member: CommunityMemberEntity)

    @Query("SELECT * FROM community_member WHERE community_id_hex = :communityIdHex AND status = 'ACTIVE' ORDER BY joined_at_owner_seq ASC")
    suspend fun getActiveMembers(communityIdHex: String): List<CommunityMemberEntity>

    @Query("SELECT * FROM community_member WHERE community_id_hex = :communityIdHex AND author_ik_hex = :authorIkHex")
    suspend fun getMember(communityIdHex: String, authorIkHex: String): CommunityMemberEntity?

    @Query("SELECT * FROM community_member WHERE community_id_hex = :communityIdHex ORDER BY role ASC, joined_at_owner_seq ASC")
    fun observeMembers(communityIdHex: String): Flow<List<CommunityMemberEntity>>

    @Query("UPDATE community_member SET status = :status, updated_at_ms = :updatedAt WHERE community_id_hex = :communityIdHex AND author_ik_hex = :authorIkHex")
    suspend fun updateMemberStatus(communityIdHex: String, authorIkHex: String, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE community_member SET role = :role, updated_at_ms = :updatedAt WHERE community_id_hex = :communityIdHex AND author_ik_hex = :authorIkHex")
    suspend fun updateMemberRole(communityIdHex: String, authorIkHex: String, role: String, updatedAt: Long = System.currentTimeMillis())

    /** Solicitudes de membresía pendientes de aprobación (TYPE_JOIN_REQUEST recibido pero aún no aceptado). */
    @Query("SELECT * FROM community_member WHERE community_id_hex = :communityIdHex AND status = 'REQUESTED' ORDER BY joined_at_owner_seq ASC")
    suspend fun getPendingRequests(communityIdHex: String): List<CommunityMemberEntity>

    /** Observa en tiempo real las solicitudes pendientes (útil para badge en la UI). */
    @Query("SELECT * FROM community_member WHERE community_id_hex = :communityIdHex AND status = 'REQUESTED' ORDER BY joined_at_owner_seq ASC")
    fun observePendingRequests(communityIdHex: String): Flow<List<CommunityMemberEntity>>
}

