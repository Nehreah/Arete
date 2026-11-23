package com.renea.arete_social.data.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class OperationEnvelopeTest {

    @Test
    fun testEncodeAndDecodeRoundtrip() {
        val dummyCommunityId = ByteArray(32) { it.toByte() }
        val dummyAuthorIk = ByteArray(65) { if (it == 0) 0x04.toByte() else it.toByte() }
        val dummyPrevOp = ByteArray(32) { 0 }
        val dummyOwnerHeadHash = ByteArray(32) { 0xAA.toByte() }
        val dummyEpochId = ByteArray(32) { 0xBB.toByte() }
        val dummyBody = "{\"name\":\"Comunidad Aréte Test\"}".toByteArray(Charsets.UTF_8)
        val dummySignature = ByteArray(64) { 0x55.toByte() }
        val deviceId = UUID.randomUUID()

        val envelope = OperationEnvelope(
            version = 1,
            operationType = OperationEnvelope.TYPE_GENESIS,
            communityId = dummyCommunityId,
            authorIk = dummyAuthorIk,
            deviceId = deviceId,
            deviceEpoch = 1,
            deviceSeq = 42L,
            prevOpHash = dummyPrevOp,
            ownerHeadSeq = 10L,
            ownerHeadHash = dummyOwnerHeadHash,
            epochId = dummyEpochId,
            timestamp = 1794769200000L,
            bodyBytes = dummyBody,
            signatureBytes = dummySignature
        )

        val fullBytes = envelope.encodeFullOperation()
        val decoded = OperationEnvelope.decode(fullBytes)

        assertEquals(envelope.version, decoded.version)
        assertEquals(envelope.operationType, decoded.operationType)
        assertArrayEquals(envelope.communityId, decoded.communityId)
        assertArrayEquals(envelope.authorIk, decoded.authorIk)
        assertEquals(envelope.deviceId, decoded.deviceId)
        assertEquals(envelope.deviceEpoch, decoded.deviceEpoch)
        assertEquals(envelope.deviceSeq, decoded.deviceSeq)
        assertArrayEquals(envelope.prevOpHash, decoded.prevOpHash)
        assertEquals(envelope.ownerHeadSeq, decoded.ownerHeadSeq)
        assertArrayEquals(envelope.ownerHeadHash, decoded.ownerHeadHash)
        assertArrayEquals(envelope.epochId, decoded.epochId)
        assertEquals(envelope.timestamp, decoded.timestamp)
        assertArrayEquals(envelope.bodyBytes, decoded.bodyBytes)
        assertArrayEquals(envelope.signatureBytes, decoded.signatureBytes)
        assertEquals(envelope, decoded)

        // Comprobar cálculo de OpId
        val opId = envelope.calculateOpId()
        assertEquals(32, opId.size)
        assertTrue(envelope.calculateOpIdHex().length == 64)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testExceedMaxBodyThrows() {
        val oversizedBody = ByteArray(OperationEnvelope.MAX_BODY_BYTES + 1)
        OperationEnvelope(
            version = 1,
            operationType = OperationEnvelope.TYPE_GENESIS,
            communityId = ByteArray(32),
            authorIk = ByteArray(65) { 0x04 },
            deviceId = UUID.randomUUID(),
            deviceEpoch = 1,
            deviceSeq = 1L,
            prevOpHash = ByteArray(32),
            ownerHeadSeq = 0L,
            ownerHeadHash = ByteArray(32),
            epochId = ByteArray(32),
            timestamp = 1000L,
            bodyBytes = oversizedBody
        )
    }
}
