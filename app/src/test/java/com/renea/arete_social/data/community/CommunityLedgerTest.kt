package com.renea.arete_social.data.community

import com.renea.arete_social.data.protocol.OperationEnvelope
import com.renea.arete_social.data.protocol.OwnerSpine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class CommunityLedgerTest {

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    @Test
    fun testCommunityLedgerLifecycle() {
        val ownerIk = ByteArray(65) { 0x01 }
        val leaderIk = ByteArray(65) { 0x02 }
        val memberIk = ByteArray(65) { 0x03 }
        val communityId = ByteArray(32) { 0xAA.toByte() }

        val ledger = CommunityLedger(
            communityId = communityId,
            ownerIk = ownerIk,
            initialName = "Comunidad Aréte",
            initialDescription = "Prueba local-first"
        )

        // 1. Génesis por el dueño
        val genesisBody = "{\"name\":\"Comunidad Aréte\",\"description\":\"Local-first\",\"initialEpochIdHex\":\"${ByteArray(32) { 0x11 }.toHex()}\"}".toByteArray(Charsets.UTF_8)
        val genesisOp = OperationEnvelope(
            version = 1,
            operationType = OperationEnvelope.TYPE_GENESIS,
            communityId = communityId,
            authorIk = ownerIk,
            deviceId = UUID.randomUUID(),
            deviceEpoch = 1,
            deviceSeq = 1L,
            prevOpHash = ByteArray(32),
            ownerHeadSeq = 0L,
            ownerHeadHash = ByteArray(32),
            epochId = ByteArray(32),
            timestamp = 1000L,
            bodyBytes = genesisBody
        )
        val statusGen = ledger.applyOperation(genesisOp)
        assertEquals(OwnerSpine.EvaluationStatus.CONFIRMED, statusGen)
        assertEquals(1L, ledger.getState().currentHeadSeq)

        // 2. Dueño designa al Líder
        val leaderBody = "{\"targetIkHex\":\"${leaderIk.toHex()}\",\"role\":\"LEADER\"}".toByteArray(Charsets.UTF_8)
        val leaderOp = OperationEnvelope(
            version = 1,
            operationType = OperationEnvelope.TYPE_LEADER_DESIGNATE,
            communityId = communityId,
            authorIk = ownerIk,
            deviceId = UUID.randomUUID(),
            deviceEpoch = 1,
            deviceSeq = 2L,
            prevOpHash = genesisOp.calculateOpId(),
            ownerHeadSeq = 1L,
            ownerHeadHash = genesisOp.calculateOpId(),
            epochId = ByteArray(32),
            timestamp = 2000L,
            bodyBytes = leaderBody
        )
        val statusLeader = ledger.applyOperation(leaderOp)
        assertEquals(OwnerSpine.EvaluationStatus.CONFIRMED, statusLeader)
        assertEquals(CommunityAuthority.Role.LEADER, ledger.getState().getMember(leaderIk)?.role)

        // 3. Líder admite a un miembro (JOIN_ACCEPT)
        val joinBody = "{\"targetIkHex\":\"${memberIk.toHex()}\",\"role\":\"MEMBER\"}".toByteArray(Charsets.UTF_8)
        val joinOp = OperationEnvelope(
            version = 1,
            operationType = OperationEnvelope.TYPE_JOIN_ACCEPT,
            communityId = communityId,
            authorIk = leaderIk,
            deviceId = UUID.randomUUID(),
            deviceEpoch = 1,
            deviceSeq = 1L,
            prevOpHash = ByteArray(32),
            ownerHeadSeq = 2L,
            ownerHeadHash = leaderOp.calculateOpId(),
            epochId = ByteArray(32),
            timestamp = 2500L,
            bodyBytes = joinBody
        )
        val statusJoin = ledger.applyOperation(joinOp)
        assertEquals(OwnerSpine.EvaluationStatus.CONFIRMED, statusJoin)
        assertEquals(CommunityAuthority.Role.MEMBER, ledger.getState().getMember(memberIk)?.role)

        // 4. Líder expulsa al miembro
        val expelBody = "{\"targetIkHex\":\"${memberIk.toHex()}\",\"reason\":\"Incumplimiento de normas\"}".toByteArray(Charsets.UTF_8)
        val expelOp = OperationEnvelope(
            version = 1,
            operationType = OperationEnvelope.TYPE_EXPEL,
            communityId = communityId,
            authorIk = leaderIk,
            deviceId = UUID.randomUUID(),
            deviceEpoch = 1,
            deviceSeq = 2L,
            prevOpHash = joinOp.calculateOpId(),
            ownerHeadSeq = 2L,
            ownerHeadHash = leaderOp.calculateOpId(),
            epochId = ByteArray(32),
            timestamp = 3000L,
            bodyBytes = expelBody
        )
        val statusExpel = ledger.applyOperation(expelOp)
        assertEquals(OwnerSpine.EvaluationStatus.CONFIRMED, statusExpel)
        assertEquals(CommunityMemberState.MemberStatus.EXPELLED, ledger.getState().getMember(memberIk)?.status)

        // 5. Rotación de época post-expulsión
        val newEpochId = ByteArray(32) { 0x99.toByte() }
        val rotateOp = OperationEnvelope(
            version = 1,
            operationType = OperationEnvelope.TYPE_EPOCH_ROTATE,
            communityId = communityId,
            authorIk = leaderIk,
            deviceId = UUID.randomUUID(),
            deviceEpoch = 1,
            deviceSeq = 3L,
            prevOpHash = expelOp.calculateOpId(),
            ownerHeadSeq = 2L,
            ownerHeadHash = leaderOp.calculateOpId(),
            epochId = newEpochId,
            timestamp = 3100L,
            bodyBytes = ByteArray(0)
        )
        val statusRotate = ledger.applyOperation(rotateOp)
        assertEquals(OwnerSpine.EvaluationStatus.CONFIRMED, statusRotate)
        assertTrue(ledger.getState().currentEpochId.contentEquals(newEpochId))
    }
}
