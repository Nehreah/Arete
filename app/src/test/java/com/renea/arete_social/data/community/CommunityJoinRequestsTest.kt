package com.renea.arete_social.data.community

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.SecureRandom

class CommunityJoinRequestsTest {

    private lateinit var epochKeyManager: EpochKeyManager
    private lateinit var joinRequests: CommunityJoinRequests

    private val communityId = "comm-uuid-1234567890123456789012".toByteArray(Charsets.UTF_8).copyOf(32)
    private val epochId = "epoch-id-1234567890123456789012".toByteArray(Charsets.UTF_8).copyOf(32)
    private val rawKe = ByteArray(32).also { SecureRandom().nextBytes(it) }

    @Before
    fun setUp() {
        epochKeyManager = EpochKeyManager()
        epochKeyManager.storeEpochKey(communityId, epochId, rawKe)
        joinRequests = CommunityJoinRequests(epochKeyManager)
    }

    @Test
    fun testSealAndUnsealJoinRequest() {
        val payload = CommunityJoinRequests.JoinRequestPayload(
            applicantIkHex = "04aabbccddeeff00112233445566778899",
            applicantDkaPublicKeyDerHex = "3059301306072a8648ce3d020106082a8648ce3d030107",
            displayName = "Camilo Torres",
            introMessage = "Hola, me gustaría ingresar al nodo barrial",
            requestedAtMs = 1_750_000_000_000L
        )

        val sealed = joinRequests.sealRequest(communityId, epochId, payload)
        assertNotNull(sealed)
        assertTrue(sealed.encryptedPayload.isNotEmpty())

        val unsealed = joinRequests.unsealRequest(sealed)
        assertEquals(payload.applicantIkHex, unsealed.applicantIkHex)
        assertEquals(payload.displayName, unsealed.displayName)
        assertEquals(payload.introMessage, unsealed.introMessage)
    }

    @Test(expected = Exception::class)
    fun testUnsealWithWrongEpochFails() {
        val payload = CommunityJoinRequests.JoinRequestPayload(
            applicantIkHex = "04aabbcc",
            applicantDkaPublicKeyDerHex = "3059",
            displayName = "Ana",
            introMessage = "Acceso",
            requestedAtMs = 1_000L
        )

        val sealed = joinRequests.sealRequest(communityId, epochId, payload)

        val wrongEpochId = "wrong-epoch-12345678901234567890".toByteArray(Charsets.UTF_8).copyOf(32)
        epochKeyManager.storeEpochKey(communityId, wrongEpochId, ByteArray(32))

        val wrongSealed = sealed.copy(epochId = wrongEpochId)
        joinRequests.unsealRequest(wrongSealed)
    }
}
