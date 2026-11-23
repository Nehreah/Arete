package com.renea.arete_social.data.community

import com.renea.arete_social.data.protocol.OperationEnvelope
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.UUID

class CommunityOwnershipTest {

    private lateinit var ledger: CommunityLedger
    private lateinit var ownership: CommunityOwnership

    private val heirIk = ByteArray(65).apply {
        this[0] = 0x04
        for (i in 1 until 65) this[i] = (i and 0xFF).toByte()
    }

    @Before
    fun setUp() {
        ledger = mockk(relaxed = true)
        ownership = CommunityOwnership(ledger)
    }

    @Test
    fun testHeirClaimValidAfter90DaysInactivity() {
        val now = 1_750_000_000_000L
        val lastOwnerOp = now - (91L * 24 * 60 * 60 * 1000L) // 91 días atrás

        val body = ownership.serializeHeirClaimBody(
            heirIkHex = heirIk.joinToString("") { "%02x".format(it) },
            claimTimestampMs = now
        )

        val claimEnvelope = OperationEnvelope(
            version = 1,
            operationType = OperationEnvelope.TYPE_HEIR_CLAIM,
            communityId = ByteArray(32),
            authorIk = heirIk,
            deviceId = UUID.randomUUID(),
            deviceEpoch = 1,
            deviceSeq = 1L,
            prevOpHash = ByteArray(32),
            ownerHeadSeq = 10L,
            ownerHeadHash = ByteArray(32),
            epochId = ByteArray(32),
            timestamp = now,
            bodyBytes = body,
            signatureBytes = ByteArray(0)
        )

        val isValid = ownership.evaluateHeirClaim(
            claimEnvelope = claimEnvelope,
            designatedHeirIk = heirIk,
            lastOwnerOpTimestampMs = lastOwnerOp
        )

        assertTrue("El reclamo debe ser válido tras 90 días de inactividad", isValid)
    }

    @Test
    fun testHeirClaimRejectedBefore90DaysWithoutLeaderCountersignature() {
        val now = 1_750_000_000_000L
        val lastOwnerOp = now - (30L * 24 * 60 * 60 * 1000L) // Solo 30 días atrás

        val body = ownership.serializeHeirClaimBody(
            heirIkHex = heirIk.joinToString("") { "%02x".format(it) },
            claimTimestampMs = now,
            leaderCountersignatureHex = null
        )

        val claimEnvelope = OperationEnvelope(
            version = 1,
            operationType = OperationEnvelope.TYPE_HEIR_CLAIM,
            communityId = ByteArray(32),
            authorIk = heirIk,
            deviceId = UUID.randomUUID(),
            deviceEpoch = 1,
            deviceSeq = 1L,
            prevOpHash = ByteArray(32),
            ownerHeadSeq = 10L,
            ownerHeadHash = ByteArray(32),
            epochId = ByteArray(32),
            timestamp = now,
            bodyBytes = body,
            signatureBytes = ByteArray(0)
        )

        val isValid = ownership.evaluateHeirClaim(
            claimEnvelope = claimEnvelope,
            designatedHeirIk = heirIk,
            lastOwnerOpTimestampMs = lastOwnerOp
        )

        assertFalse("El reclamo debe rechazarse antes de 90 días sin contrafirma", isValid)
    }

    @Test
    fun testHeirClaimValidWithLeaderCountersignatureEvenIfRecent() {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val leaderPair = kpg.generateKeyPair()

        val now = 1_750_000_000_000L
        val lastOwnerOp = now - (5L * 24 * 60 * 60 * 1000L) // Solo 5 días de inactividad

        val baseBody = ownership.serializeHeirClaimBody(
            heirIkHex = heirIk.joinToString("") { "%02x".format(it) },
            claimTimestampMs = now,
            leaderCountersignatureHex = null
        )

        val tempEnvelope = OperationEnvelope(
            version = 1,
            operationType = OperationEnvelope.TYPE_HEIR_CLAIM,
            communityId = ByteArray(32),
            authorIk = heirIk,
            deviceId = UUID.randomUUID(),
            deviceEpoch = 1,
            deviceSeq = 1L,
            prevOpHash = ByteArray(32),
            ownerHeadSeq = 10L,
            ownerHeadHash = ByteArray(32),
            epochId = ByteArray(32),
            timestamp = now,
            bodyBytes = baseBody,
            signatureBytes = ByteArray(0)
        )

        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(leaderPair.private)
        signer.update(tempEnvelope.bytesToSign())
        val leaderSig = signer.sign()
        val leaderSigHex = leaderSig.joinToString("") { "%02x".format(it) }

        val bodyWithCountersig = ownership.serializeHeirClaimBody(
            heirIkHex = heirIk.joinToString("") { "%02x".format(it) },
            claimTimestampMs = now,
            leaderCountersignatureHex = leaderSigHex
        )

        val claimEnvelope = tempEnvelope.copy(bodyBytes = bodyWithCountersig)

        val isValid = ownership.evaluateHeirClaim(
            claimEnvelope = claimEnvelope,
            designatedHeirIk = heirIk,
            lastOwnerOpTimestampMs = lastOwnerOp,
            leaderIkPublicKey = leaderPair.public,
            verifyLeaderSignature = { payload, sigHex, key ->
                val v = Signature.getInstance("SHA256withECDSA")
                v.initVerify(key)
                v.update(payload)
                val sigBytes = sigHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                v.verify(sigBytes)
            }
        )

        assertTrue("El reclamo con contrafirma válida del líder debe ser aceptado", isValid)
    }
}
