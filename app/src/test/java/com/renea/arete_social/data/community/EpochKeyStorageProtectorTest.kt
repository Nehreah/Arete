package com.renea.arete_social.data.community

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.SecureRandom

class EpochKeyStorageProtectorTest {

    private lateinit var protector: EpochKeyStorageProtector
    private val secureRandom = SecureRandom()

    @Before
    fun setUp() {
        // En entorno JVM puro, pasa un provider no-Android para usar la clave simétrica de prueba
        protector = EpochKeyStorageProtector(keyStoreProvider = "CustomNonAndroidProvider")
    }

    @Test
    fun testEncryptAndDecryptEpochKey() {
        val originalKe = ByteArray(32).also { secureRandom.nextBytes(it) }

        val encrypted = protector.encryptKe(originalKe)

        assertTrue("El valor cifrado debe iniciar con ENC:v1:", protector.isEncrypted(encrypted))
        assertTrue(encrypted.startsWith("ENC:v1:"))

        val decrypted = protector.decryptKe(encrypted)

        assertArrayEquals("La clave descifrada debe ser idéntica a la original", originalKe, decrypted)
    }

    @Test
    fun testRandomIvProducesDifferentCiphertext() {
        val originalKe = ByteArray(32).also { secureRandom.nextBytes(it) }

        val enc1 = protector.encryptKe(originalKe)
        val enc2 = protector.encryptKe(originalKe)

        assertNotEquals("Dos cifrados sucesivos deben generar IVs y ciphertexts distintos", enc1, enc2)
        assertArrayEquals(protector.decryptKe(enc1), protector.decryptKe(enc2))
    }

    @Test
    fun testLegacyHexCompatibility() {
        val rawBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0xAA.toByte(), 0xFF.toByte())
        val legacyHex = "01020304aaff"

        assertFalse(protector.isEncrypted(legacyHex))

        val decrypted = protector.decryptKe(legacyHex)

        assertArrayEquals(rawBytes, decrypted)
    }
}
