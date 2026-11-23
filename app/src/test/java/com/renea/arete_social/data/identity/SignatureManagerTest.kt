package com.renea.arete_social.data.identity

import com.renea.arete_social.data.protocol.OperationEnvelope
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.UUID

class SignatureManagerTest {

    @Test
    fun testIssueCertificateAndSignOperation() {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))

        // Clave de Identidad (IK)
        val ikKeyPair = kpg.generateKeyPair()

        // Claves de Dispositivo (DK-s y DK-a)
        val dksKeyPair = kpg.generateKeyPair()
        val dkaKeyPair = kpg.generateKeyPair()

        val authorIkBytes = ByteArray(65) { if (it == 0) 0x04.toByte() else 0x11.toByte() }
        val deviceId = UUID.randomUUID()
        val signatureManager = SignatureManager()

        // 1. Emitir certificado de dispositivo
        val cert = signatureManager.issueDeviceCertificate(
            authorIk = authorIkBytes,
            deviceId = deviceId,
            deviceEpoch = 1,
            dksPublicKey = dksKeyPair.public.encoded,
            dkaPublicKey = dkaKeyPair.public.encoded,
            validFromMillis = System.currentTimeMillis() - 1000,
            validUntilMillis = System.currentTimeMillis() + 100000,
            ikPrivateKey = ikKeyPair.private
        )

        // 2. Registrar certificado en caché
        val registered = signatureManager.registerCertificate(cert, ikKeyPair.public)
        assertTrue(registered)

        // 3. Crear y firmar operación con DK-s
        val unsignedOp = OperationEnvelope(
            version = 1,
            operationType = OperationEnvelope.TYPE_JOIN_ACCEPT,
            communityId = ByteArray(32) { 0xAA.toByte() },
            authorIk = authorIkBytes,
            deviceId = deviceId,
            deviceEpoch = 1,
            deviceSeq = 1L,
            prevOpHash = ByteArray(32),
            ownerHeadSeq = 0L,
            ownerHeadHash = ByteArray(32),
            epochId = ByteArray(32),
            timestamp = System.currentTimeMillis(),
            bodyBytes = "{\"test\":true}".toByteArray(Charsets.UTF_8)
        )

        val signedOp = signatureManager.signOperation(unsignedOp, dksKeyPair.private)
        assertTrue(signedOp.signatureBytes.isNotEmpty())

        // 4. Verificar operación contra la cadena de confianza
        val isValid = signatureManager.verifyOperation(signedOp)
        assertTrue(isValid)

        // 5. Verificar que si se altera el contenido, la firma falla
        val tamperedOp = signedOp.copy(bodyBytes = "{\"test\":false}".toByteArray(Charsets.UTF_8))
        val isTamperedValid = signatureManager.verifyOperation(tamperedOp)
        assertFalse(isTamperedValid)
    }
}
