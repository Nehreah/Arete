package com.renea.arete_social.data.protocol

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class OwnerSpineTest {

    @Test
    fun testRevocationCutoffAndBackdatingMitigation() {
        val ownerIk = ByteArray(65) { 0x01 }
        val adminIk = ByteArray(65) { 0x02 }
        val communityId = ByteArray(32) { 0x10 }

        val spine = OwnerSpine(communityId, ownerIk)

        val genesisHash = ByteArray(32) { 0x01 }
        spine.appendOwnerBlock(
            ownerSeq = 1,
            opHash = genesisHash,
            operationType = OperationEnvelope.TYPE_GENESIS,
            timestamp = 1000L
        )

        val leaderBlock2Hash = ByteArray(32) { 0x02 }
        spine.appendOwnerBlock(
            ownerSeq = 2,
            opHash = leaderBlock2Hash,
            operationType = OperationEnvelope.TYPE_LEADER_DESIGNATE,
            timestamp = 2000L
        )

        // 1. Admin crea una operación con headSeq = 2 (válida antes de revocación)
        val validOp = OperationEnvelope(
            version = 1,
            operationType = OperationEnvelope.TYPE_JOIN_ACCEPT,
            communityId = communityId,
            authorIk = adminIk,
            deviceId = UUID.randomUUID(),
            deviceEpoch = 1,
            deviceSeq = 1L,
            prevOpHash = ByteArray(32),
            ownerHeadSeq = 2L,
            ownerHeadHash = leaderBlock2Hash,
            epochId = ByteArray(32),
            timestamp = 2500L,
            bodyBytes = ByteArray(10)
        )
        assertEquals(OwnerSpine.EvaluationStatus.CONFIRMED, spine.evaluateDelegatedOperation(validOp))

        // 2. El dueño revoca al admin en la altura 3 con cutoff = 3
        val revokeBlock3Hash = ByteArray(32) { 0x03 }
        spine.appendOwnerBlock(
            ownerSeq = 3,
            opHash = revokeBlock3Hash,
            operationType = OperationEnvelope.TYPE_ROLE_REVOKE,
            cutoffSeq = 3L,
            targetIk = adminIk,
            timestamp = 3000L
        )

        // 3. Operación firmada con headSeq = 3 (igual al corte) -> debe ser INVALID_CUTOFF
        val invalidOp = OperationEnvelope(
            version = 1,
            operationType = OperationEnvelope.TYPE_JOIN_ACCEPT,
            communityId = communityId,
            authorIk = adminIk,
            deviceId = UUID.randomUUID(),
            deviceEpoch = 1,
            deviceSeq = 2L,
            prevOpHash = ByteArray(32),
            ownerHeadSeq = 3L,
            ownerHeadHash = revokeBlock3Hash,
            epochId = ByteArray(32),
            timestamp = 3100L,
            bodyBytes = ByteArray(10)
        )
        assertEquals(OwnerSpine.EvaluationStatus.INVALID_CUTOFF, spine.evaluateDelegatedOperation(invalidOp))

        // 4. Operación fechada hacia atrás: admin finge que su headSeq era 2, pero su timestamp es 4000L
        // (muy posterior a 3000L de la revocación) -> SUSPICIOUS
        val backdatedOp = OperationEnvelope(
            version = 1,
            operationType = OperationEnvelope.TYPE_JOIN_ACCEPT,
            communityId = communityId,
            authorIk = adminIk,
            deviceId = UUID.randomUUID(),
            deviceEpoch = 1,
            deviceSeq = 3L,
            prevOpHash = ByteArray(32),
            ownerHeadSeq = 2L,
            ownerHeadHash = leaderBlock2Hash,
            epochId = ByteArray(32),
            timestamp = 10_000_000L, // Muy posterior al corte
            bodyBytes = ByteArray(10)
        )
        assertEquals(OwnerSpine.EvaluationStatus.SUSPICIOUS, spine.evaluateDelegatedOperation(backdatedOp))

        // 5. Operación que referencia una cabeza no conocida aún -> PROVISIONAL
        val provisionalOp = OperationEnvelope(
            version = 1,
            operationType = OperationEnvelope.TYPE_CONTENT_POST,
            communityId = communityId,
            authorIk = ByteArray(65) { 0x09 },
            deviceId = UUID.randomUUID(),
            deviceEpoch = 1,
            deviceSeq = 1L,
            prevOpHash = ByteArray(32),
            ownerHeadSeq = 99L,
            ownerHeadHash = ByteArray(32) { 0x99.toByte() },
            epochId = ByteArray(32),
            timestamp = 3200L,
            bodyBytes = ByteArray(10)
        )
        assertEquals(OwnerSpine.EvaluationStatus.PROVISIONAL, spine.evaluateDelegatedOperation(provisionalOp))
    }

    @Test
    fun testOwnerHeadSeqZeroIsConfirmedWithoutCutoff() {
        val ownerIk = ByteArray(65) { 0x01 }
        val memberIk = ByteArray(65) { 0x05 }
        val communityId = ByteArray(32) { 0x10 }

        val spine = OwnerSpine(communityId, ownerIk)
        val genesisHash = ByteArray(32) { 0x01 }
        spine.appendOwnerBlock(
            ownerSeq = 1,
            opHash = genesisHash,
            operationType = OperationEnvelope.TYPE_GENESIS,
            timestamp = 1000L
        )

        // Operación inicial (ej: post o join request) con ownerHeadSeq = 0L y hash ceros
        val opHeadCero = OperationEnvelope(
            version = 1,
            operationType = OperationEnvelope.TYPE_CONTENT_POST,
            communityId = communityId,
            authorIk = memberIk,
            deviceId = UUID.randomUUID(),
            deviceEpoch = 1,
            deviceSeq = 1L,
            prevOpHash = ByteArray(32),
            ownerHeadSeq = 0L,
            ownerHeadHash = ByteArray(32),
            epochId = ByteArray(32),
            timestamp = 1200L,
            bodyBytes = ByteArray(10)
        )
        // Debe ser CONFIRMED porque no hay corte de revocación y ownerHeadSeq = 0L es el punto de partida válido
        assertEquals(OwnerSpine.EvaluationStatus.CONFIRMED, spine.evaluateDelegatedOperation(opHeadCero))
    }
}
