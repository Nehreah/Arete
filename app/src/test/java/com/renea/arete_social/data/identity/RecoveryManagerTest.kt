package com.renea.arete_social.data.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecoveryManagerTest {

    private lateinit var identityManager: IdentityManager
    private lateinit var recoveryManager: RecoveryManager

    private val testWords = listOf(
        "abandon", "ability", "able", "about", "above", "absent",
        "absorb", "abstract", "absurd", "abuse", "access", "accident"
    )
    private val password = "SuperSecretPassword123!".toCharArray()

    @Before
    fun setUp() {
        identityManager = IdentityManager()
        identityManager.loadFromPhrase(testWords)
        recoveryManager = RecoveryManager(identityManager)
    }

    @Test
    fun testExportAndImportEncryptedBackup() {
        val encryptedBackup = recoveryManager.exportEncryptedBackup(testWords, password)
        assertNotNull(encryptedBackup)
        assertTrue(encryptedBackup.isNotEmpty())

        val recoveredWords = recoveryManager.importEncryptedBackup(encryptedBackup, password)
        assertEquals(testWords, recoveredWords)
    }

    @Test(expected = Exception::class)
    fun testImportWithWrongPasswordFails() {
        val encryptedBackup = recoveryManager.exportEncryptedBackup(testWords, password)
        val wrongPassword = "WrongPassword999!".toCharArray()

        recoveryManager.importEncryptedBackup(encryptedBackup, wrongPassword)
    }

    @Test
    fun testVerifyPhraseMatchesHash() {
        val hashHex = identityManager.hashPhrase(testWords)
        assertTrue(recoveryManager.verifyPhraseMatchesHash(testWords, hashHex))

        val alteredWords = testWords.toMutableList().apply { set(0, "zoo") }
        assertFalse(recoveryManager.verifyPhraseMatchesHash(alteredWords, hashHex))
    }

    @Test
    fun testImportLegacy100kBackup() {
        val mnemonic = testWords.joinToString(" ")
        val mnemonicBytes = mnemonic.toByteArray(java.nio.charset.StandardCharsets.UTF_8)
        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = javax.crypto.spec.PBEKeySpec(password, salt, 100_000, 256)
        val secretKey = javax.crypto.spec.SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey, javax.crypto.spec.GCMParameterSpec(128, iv))
        cipher.updateAAD("arete/backup/v1".toByteArray(java.nio.charset.StandardCharsets.UTF_8))
        val ciphertext = cipher.doFinal(mnemonicBytes)
        val buffer = java.nio.ByteBuffer.allocate(16 + 12 + ciphertext.size).order(java.nio.ByteOrder.BIG_ENDIAN)
        buffer.put(salt)
        buffer.put(iv)
        buffer.put(ciphertext)

        val recoveredWords = recoveryManager.importEncryptedBackup(buffer.array(), password)
        assertEquals(testWords, recoveredWords)
    }
}
