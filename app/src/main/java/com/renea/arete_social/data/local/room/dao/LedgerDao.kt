package com.renea.arete_social.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.renea.arete_social.data.local.room.entity.EpochKeyEntity
import com.renea.arete_social.data.local.room.entity.LedgerOperationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {

    // ─── Registro de Operaciones (append-only) ───

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOperation(operation: LedgerOperationEntity): Long

    @Query("SELECT * FROM ledger_operation WHERE community_id_hex = :communityIdHex ORDER BY owner_head_seq ASC, device_seq ASC")
    suspend fun getOperationsForCommunity(communityIdHex: String): List<LedgerOperationEntity>

    @Query("SELECT * FROM ledger_operation WHERE community_id_hex = :communityIdHex AND owner_head_seq > :afterOwnerSeq ORDER BY owner_head_seq ASC LIMIT :limit")
    suspend fun getOperationsSince(communityIdHex: String, afterOwnerSeq: Long, limit: Int = 200): List<LedgerOperationEntity>

    @Query("SELECT * FROM ledger_operation WHERE op_id_hex = :opIdHex")
    suspend fun getOperation(opIdHex: String): LedgerOperationEntity?

    @Query("SELECT COUNT(*) FROM ledger_operation WHERE community_id_hex = :communityIdHex")
    suspend fun countOperations(communityIdHex: String): Long

    @Query("SELECT * FROM ledger_operation WHERE community_id_hex = :communityIdHex AND operation_type = :operationType ORDER BY stored_at_ms DESC")
    suspend fun getOperationsByType(communityIdHex: String, operationType: Int): List<LedgerOperationEntity>

    @Query("UPDATE ledger_operation SET validation_status = :status WHERE op_id_hex = :opIdHex")
    suspend fun updateOperationStatus(opIdHex: String, status: String)

    @Query("SELECT * FROM ledger_operation WHERE community_id_hex = :communityIdHex AND author_ik_hex = :authorIkHex ORDER BY device_seq DESC LIMIT 1")
    suspend fun getLastOperationByAuthor(communityIdHex: String, authorIkHex: String): LedgerOperationEntity?

    @Query("SELECT * FROM ledger_operation WHERE community_id_hex = :communityIdHex ORDER BY owner_head_seq DESC LIMIT 1")
    suspend fun getOwnerHead(communityIdHex: String): LedgerOperationEntity?

    @Query("SELECT * FROM ledger_operation WHERE community_id_hex = :communityIdHex AND owner_head_seq BETWEEN :fromSeq AND :toSeq ORDER BY owner_head_seq ASC")
    suspend fun getOperationsInRange(communityIdHex: String, fromSeq: Long, toSeq: Long): List<LedgerOperationEntity>

    // ─── Claves de Época ───

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEpochKey(epochKey: EpochKeyEntity)

    @Query("SELECT * FROM epoch_key WHERE community_id_hex = :communityIdHex AND epoch_id_hex = :epochIdHex")
    suspend fun getEpochKey(communityIdHex: String, epochIdHex: String): EpochKeyEntity?

    @Query("SELECT * FROM epoch_key WHERE community_id_hex = :communityIdHex AND is_canonical = 1 ORDER BY received_at_ms DESC LIMIT 1")
    suspend fun getCurrentEpochKey(communityIdHex: String): EpochKeyEntity?

    @Query("SELECT * FROM epoch_key WHERE community_id_hex = :communityIdHex ORDER BY received_at_ms DESC")
    suspend fun getAllEpochKeys(communityIdHex: String): List<EpochKeyEntity>

    @Query("UPDATE epoch_key SET is_canonical = 0 WHERE community_id_hex = :communityIdHex AND epoch_id_hex != :canonicalEpochIdHex")
    suspend fun demoteNonCanonicalEpochs(communityIdHex: String, canonicalEpochIdHex: String)
}
