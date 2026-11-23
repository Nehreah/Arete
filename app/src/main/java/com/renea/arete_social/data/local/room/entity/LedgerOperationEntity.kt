package com.renea.arete_social.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Entidad Room para el registro de operaciones firmadas (ledger) de una comunidad.
 * Solo se añaden registros; nunca se modifica ni se elimina una operación existente.
 *
 * El op_id_hex es el SHA-256(sobre || firma) según la especificación del protocolo v1.
 */
@Entity(
    tableName = "ledger_operation",
    primaryKeys = ["op_id_hex"],
    indices = [
        Index(value = ["community_id_hex", "device_seq"]),
        Index(value = ["community_id_hex", "owner_head_seq"]),
        Index(value = ["operation_type"])
    ]
)
data class LedgerOperationEntity(
    @ColumnInfo(name = "op_id_hex") val opIdHex: String,
    @ColumnInfo(name = "community_id_hex") val communityIdHex: String,

    @ColumnInfo(name = "version") val version: Int = 1,
    @ColumnInfo(name = "operation_type") val operationType: Int,
    @ColumnInfo(name = "author_ik_hex") val authorIkHex: String,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "device_epoch") val deviceEpoch: Int,
    @ColumnInfo(name = "device_seq") val deviceSeq: Long,
    @ColumnInfo(name = "prev_op_hash_hex") val prevOpHashHex: String,
    @ColumnInfo(name = "owner_head_seq") val ownerHeadSeq: Long,
    @ColumnInfo(name = "owner_head_hash_hex") val ownerHeadHashHex: String,
    @ColumnInfo(name = "epoch_id_hex") val epochIdHex: String,
    @ColumnInfo(name = "timestamp_ms") val timestampMs: Long,
    @ColumnInfo(name = "body_hex") val bodyHex: String,
    @ColumnInfo(name = "signature_hex") val signatureHex: String,

    // Estado derivado
    @ColumnInfo(name = "validation_status") val validationStatus: String = "PROVISIONAL", // CONFIRMED, PROVISIONAL, SUSPICIOUS, INVALID_CUTOFF
    @ColumnInfo(name = "stored_at_ms") val storedAtMs: Long = System.currentTimeMillis()
)
