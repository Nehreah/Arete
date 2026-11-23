package com.renea.arete_social.data.community

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException

class CommunityFileManagerTest {

    private lateinit var epochKeyManager: EpochKeyManager
    private lateinit var fileManager: CommunityFileManager

    private val communityId = "comm-uuid-1234567890123456789012".toByteArray(Charsets.UTF_8).copyOf(32)
    private val epochId = "epoch-id-1234567890123456789012".toByteArray(Charsets.UTF_8).copyOf(32)
    private val rawKey = ByteArray(32).also { SecureRandom().nextBytes(it) }

    @Before
    fun setUp() {
        epochKeyManager = EpochKeyManager()
        epochKeyManager.storeEpochKey(communityId, epochId, rawKey)
        fileManager = CommunityFileManager(epochKeyManager)
    }

    @Test
    fun testEncryptAndDecryptBytes() {
        val originalText = "Este es un archivo secreto de la comunidad de Aréte."
        val originalBytes = originalText.toByteArray(Charsets.UTF_8)
        val originalSha256 = MessageDigest.getInstance("SHA-256").digest(originalBytes)

        val encrypted = fileManager.encryptFileBytes(communityId, epochId, originalBytes)
        assertNotNull(encrypted)
        assertTrue(encrypted.size > originalBytes.size)

        val decrypted = fileManager.decryptFileBytes(communityId, epochId, encrypted, originalSha256)
        assertEquals(originalText, String(decrypted, Charsets.UTF_8))
    }

    @Test(expected = Exception::class)
    fun testDecryptWithWrongEpochFails() {
        val originalBytes = "Archivo confidencial".toByteArray(Charsets.UTF_8)
        val originalSha256 = MessageDigest.getInstance("SHA-256").digest(originalBytes)

        val encrypted = fileManager.encryptFileBytes(communityId, epochId, originalBytes)

        val wrongEpochId = "wrong-epoch-12345678901234567890".toByteArray(Charsets.UTF_8).copyOf(32)
        epochKeyManager.storeEpochKey(communityId, wrongEpochId, ByteArray(32))

        // Debe fallar por autenticación GCM (clave o AAD diferente)
        fileManager.decryptFileBytes(communityId, wrongEpochId, encrypted, originalSha256)
    }

    @Test
    fun testEncryptStream() {
        val originalText = "Contenido de flujo largo transmitido por Wi-Fi LAN / Direct"
        val originalBytes = originalText.toByteArray(Charsets.UTF_8)
        val originalSha256 = MessageDigest.getInstance("SHA-256").digest(originalBytes)

        val input = ByteArrayInputStream(originalBytes)
        val output = ByteArrayOutputStream()

        fileManager.encryptStream(communityId, epochId, originalSha256, input, output)
        val encryptedStreamBytes = output.toByteArray()

        assertTrue(encryptedStreamBytes.size > originalBytes.size)

        // Verificamos que se puede descifrar con decryptFileBytes
        val decrypted = fileManager.decryptFileBytes(communityId, epochId, encryptedStreamBytes, originalSha256)
        assertEquals(originalText, String(decrypted, Charsets.UTF_8))
    }
}
