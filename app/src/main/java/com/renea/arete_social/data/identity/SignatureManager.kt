package com.renea.arete_social.data.identity

import com.renea.arete_social.data.protocol.OperationEnvelope
import com.renea.arete_social.data.protocol.OperationValidator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.util.concurrent.ConcurrentHashMap

/**
 * Gestor de verificación y emisión de firmas según la sección 8 de la auditoría v3.
 *
 * Valida la cadena de confianza:
 * Operación firmada por DK-s -> Certificado de Dispositivo firmado por IK -> Clave de Identidad (IK).
 */
class SignatureManager {

    // Caché de certificados de dispositivos verificados: deviceId -> DeviceCertificate
    private val certificateCache = ConcurrentHashMap<String, DeviceCertificate>()

    fun registerCertificate(certificate: DeviceCertificate, ikPublicKey: PublicKey): Boolean {
        if (!certificate.verifyCertificate(ikPublicKey)) {
            return false
        }
        val key = "${certificate.deviceId}_${certificate.deviceEpoch}"
        certificateCache[key] = certificate
        return true
    }

    fun getCertificate(deviceId: String, deviceEpoch: Int): DeviceCertificate? {
        return certificateCache["${deviceId}_${deviceEpoch}"]
    }

    /**
     * Valida la firma de una operación contra el certificado del dispositivo emisor.
     */
    fun verifyOperation(envelope: OperationEnvelope): Boolean {
        val cert = getCertificate(envelope.deviceId.toString(), envelope.deviceEpoch) ?: return false

        // La IK del autor en el sobre debe coincidir con la IK del certificado
        if (!envelope.authorIk.contentEquals(cert.authorIk)) {
            return false
        }

        val dksPublic = cert.getDksPublicKeyObject() ?: return false
        return OperationValidator.verifySignature(envelope, dksPublic)
    }

    /**
     * Firma un sobre de operación usando la clave privada DK-s del dispositivo.
     */
    fun signOperation(envelopeWithoutSignature: OperationEnvelope, dksPrivateKey: PrivateKey): OperationEnvelope {
        val bytesToSign = envelopeWithoutSignature.bytesToSign()
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(dksPrivateKey)
        signer.update(bytesToSign)
        val signature = signer.sign()

        return envelopeWithoutSignature.copy(signatureBytes = signature)
    }

    /**
     * Emite un nuevo certificado de dispositivo firmado por la Clave de Identidad (IK).
     */
    fun issueDeviceCertificate(
        authorIk: ByteArray,
        deviceId: java.util.UUID,
        deviceEpoch: Int,
        dksPublicKey: ByteArray,
        dkaPublicKey: ByteArray,
        validFromMillis: Long,
        validUntilMillis: Long,
        ikPrivateKey: PrivateKey
    ): DeviceCertificate {
        val unsignedCert = DeviceCertificate(
            version = 1,
            authorIk = authorIk,
            deviceId = deviceId,
            deviceEpoch = deviceEpoch,
            dksPublicKey = dksPublicKey,
            dkaPublicKey = dkaPublicKey,
            validFromMillis = validFromMillis,
            validUntilMillis = validUntilMillis,
            ikSignature = ByteArray(0)
        )

        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(ikPrivateKey)
        signer.update(unsignedCert.bytesToSign())
        val signature = signer.sign()

        return unsignedCert.copy(ikSignature = signature)
    }
}
