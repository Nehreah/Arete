package com.renea.arete_social.data.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.Signature

class DeviceKeyManagerTest {

    private lateinit var deviceKeyManager: DeviceKeyManager
    private val testDeviceId = "test-device-uuid-9999"

    @Before
    fun setUp() {
        // En entorno JVM puro, pasa un provider no-Android para forzar el fallback de software
        deviceKeyManager = DeviceKeyManager(keyStoreProvider = "CustomNonAndroidProvider")
    }

    @Test
    fun testGetOrCreateDksAndDka() {
        val dks = deviceKeyManager.getOrCreateDksKeyPair(testDeviceId)
        val dka = deviceKeyManager.getOrCreateDkaKeyPair(testDeviceId)

        assertNotNull(dks.public)
        assertNotNull(dks.private)
        assertNotNull(dka.public)
        assertNotNull(dka.private)

        // Idempotencia: segundo llamado debe retornar las mismas claves
        val dksAgain = deviceKeyManager.getOrCreateDksKeyPair(testDeviceId)
        assertEquals(dks.public, dksAgain.public)
    }

    @Test
    fun testSignWithDks() {
        val payload = "prueba_firma_dispositivo_dks".toByteArray()
        val signatureBytes = deviceKeyManager.signWithDks(testDeviceId, payload)

        assertTrue(signatureBytes.isNotEmpty())

        val dks = deviceKeyManager.getOrCreateDksKeyPair(testDeviceId)
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(dks.public)
        verifier.update(payload)
        assertTrue(verifier.verify(signatureBytes))
    }

    @Test
    fun testComputeDkaSharedSecret() {
        val peerDeviceId = "peer-device-uuid-1111"
        val peerDka = deviceKeyManager.getOrCreateDkaKeyPair(peerDeviceId)

        val secretA = deviceKeyManager.computeDkaSharedSecret(testDeviceId, peerDka.public)
        val secretB = deviceKeyManager.computeDkaSharedSecret(peerDeviceId, deviceKeyManager.getOrCreateDkaKeyPair(testDeviceId).public)

        assertTrue(secretA.isNotEmpty())
        assertTrue(secretA.contentEquals(secretB))
    }

    @Test
    fun testDeleteDeviceKeys() {
        val dks1 = deviceKeyManager.getOrCreateDksKeyPair(testDeviceId)
        deviceKeyManager.deleteDeviceKeys(testDeviceId)
        val dks2 = deviceKeyManager.getOrCreateDksKeyPair(testDeviceId)

        // Al haber eliminado, se genera un par nuevo diferente
        assertTrue(dks1.public != dks2.public)
    }
}
