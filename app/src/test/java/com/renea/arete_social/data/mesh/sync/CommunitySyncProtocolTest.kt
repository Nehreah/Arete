package com.renea.arete_social.data.mesh.sync

import com.renea.arete_social.data.protocol.OperationEnvelope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class CommunitySyncProtocolTest {

    @Test
    fun testSerializeAndDeserializeSummary() {
        val summary = CommunitySyncProtocol.InventorySummary(
            communityIdHex = "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899",
            ownerHeadSeq = 42L,
            ownerHeadHashHex = "112233445566778899aabbccddeeff00112233445566778899aabbccddeeff00",
            knownDeviceSeqs = mapOf("dev-1" to 10L, "dev-2" to 5L)
        )

        val bytes = CommunitySyncProtocol.serializeSummary(summary)
        assertNotNull(bytes)
        assertTrue(bytes.isNotEmpty())

        val deserialized = CommunitySyncProtocol.deserializeSummary(bytes)
        assertEquals(summary.communityIdHex, deserialized.communityIdHex)
        assertEquals(summary.ownerHeadSeq, deserialized.ownerHeadSeq)
        assertEquals(summary.knownDeviceSeqs["dev-1"], 10L)
    }

    @Test
    fun testComputeMissingOps() {
        val deviceId1 = UUID.randomUUID()
        val authorIk = ByteArray(65).also { it[0] = 0x04 }

        val op1 = OperationEnvelope(
            version = 1,
            operationType = OperationEnvelope.TYPE_GENESIS,
            communityId = ByteArray(32),
            authorIk = authorIk,
            deviceId = deviceId1,
            deviceEpoch = 1,
            deviceSeq = 1L,
            prevOpHash = ByteArray(32),
            ownerHeadSeq = 1L,
            ownerHeadHash = ByteArray(32),
            epochId = ByteArray(32),
            timestamp = 1000L,
            bodyBytes = ByteArray(0),
            signatureBytes = ByteArray(0)
        )

        val op2 = OperationEnvelope(
            version = 1,
            operationType = OperationEnvelope.TYPE_ADMIN_DESIGNATE,
            communityId = ByteArray(32),
            authorIk = authorIk,
            deviceId = deviceId1,
            deviceEpoch = 1,
            deviceSeq = 2L,
            prevOpHash = ByteArray(32),
            ownerHeadSeq = 2L,
            ownerHeadHash = ByteArray(32),
            epochId = ByteArray(32),
            timestamp = 2000L,
            bodyBytes = ByteArray(0),
            signatureBytes = ByteArray(0)
        )

        // El par remoto solo conoce hasta ownerHeadSeq = 1L
        val remoteSummary = CommunitySyncProtocol.InventorySummary(
            communityIdHex = "dummy",
            ownerHeadSeq = 1L,
            ownerHeadHashHex = "dummy",
            knownDeviceSeqs = mapOf(deviceId1.toString() to 1L)
        )

        val missing = CommunitySyncProtocol.computeMissingOps(listOf(op1, op2), remoteSummary)
        assertEquals(1, missing.size)
        assertEquals(op2, missing[0])
    }
}
