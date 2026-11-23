package com.renea.arete_social.data.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IdentityManagerTest {

    private lateinit var identityManager: IdentityManager

    // Frase de prueba fija de 12 palabras BIP39
    private val testWords = listOf(
        "abandon", "ability", "able", "about", "above", "absent",
        "absorb", "abstract", "absurd", "abuse", "access", "accident"
    )

    @Before
    fun setUp() {
        identityManager = IdentityManager()
    }

    @Test
    fun testInitialStateNotLoaded() {
        assertFalse(identityManager.isLoaded)
    }

    @Test
    fun testLoadFromPhraseDeterministic() {
        identityManager.loadFromPhrase(testWords)
        assertTrue(identityManager.isLoaded)

        val ikBytes1 = identityManager.ikPublicBytes
        assertEquals(65, ikBytes1.size)
        assertEquals(0x04.toByte(), ikBytes1[0]) // Clave no comprimida P-256

        // Segunda instancia con la misma frase debe derivar exactamente la misma clave
        val secondManager = IdentityManager()
        secondManager.loadFromPhrase(testWords)
        val ikBytes2 = secondManager.ikPublicBytes

        assertTrue(ikBytes1.contentEquals(ikBytes2))
    }

    @Test
    fun testSignAndVerify() {
        identityManager.loadFromPhrase(testWords)
        val message = "arete/test/payload/12345".toByteArray()

        val signature = identityManager.signWithIk(message)
        assertNotNull(signature)
        assertTrue(signature.isNotEmpty())

        val isValid = identityManager.verifyWithPublicKey(
            identityManager.ikPublicBytes,
            message,
            signature
        )
        assertTrue("La firma con IK debe ser válida", isValid)

        val isInvalid = identityManager.verifyWithPublicKey(
            identityManager.ikPublicBytes,
            "otro mensaje".toByteArray(),
            signature
        )
        assertFalse("La firma no debe verificar otro payload", isInvalid)
    }

    @Test
    fun testWipeMemory() {
        identityManager.loadFromPhrase(testWords)
        assertTrue(identityManager.isLoaded)

        identityManager.wipeMemory()
        assertFalse(identityManager.isLoaded)
    }

    @Test
    fun testHashPhraseConsistency() {
        val hash1 = identityManager.hashPhrase(testWords)
        val hash2 = identityManager.hashPhrase(testWords)
        assertEquals(hash1, hash2)
        assertEquals(64, hash1.length) // SHA-256 en hex
    }
}
