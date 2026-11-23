package com.renea.arete_social.data.protocol

import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Validador acotado para operaciones del protocolo Aréte v1.
 *
 * Implementa las reglas de la sección 9.6 de la auditoría:
 * - Validación acotada y total (sin excepciones no controladas ni DoS de CPU/memoria).
 * - Tamaño máximo de operación ≤ 8 KB.
 * - Descarte previo al almacenamiento si está mal formada.
 */
object OperationValidator {

    const val MAX_OPERATION_BYTES: Int = 8192 // 8 KB
    const val MAX_DELEGATION_DEPTH: Int = 4

    enum class ValidationStatus {
        VALID,
        INVALID_FORMAT,
        INVALID_SIZE,
        INVALID_SIGNATURE,
        UNSUPPORTED_VERSION,
        SUSPICIOUS
    }

    data class ValidationResult(
        val status: ValidationStatus,
        val reason: String? = null
    ) {
        val isValid: Boolean get() = status == ValidationStatus.VALID
    }

    /**
     * Valida la estructura básica y límites de tamaño de un sobre recibido.
     */
    fun validateBasicEnvelope(rawBytes: ByteArray): ValidationResult {
        if (rawBytes.size > MAX_OPERATION_BYTES) {
            return ValidationResult(
                ValidationStatus.INVALID_SIZE,
                "El tamaño total (${rawBytes.size} bytes) excede el máximo permitido ($MAX_OPERATION_BYTES bytes)"
            )
        }

        return try {
            val envelope = OperationEnvelope.decode(rawBytes)
            if (envelope.version != OperationEnvelope.PROTOCOL_VERSION_1) {
                return ValidationResult(
                    ValidationStatus.UNSUPPORTED_VERSION,
                    "Versión de protocolo no soportada: ${envelope.version}"
                )
            }
            if (envelope.bodyBytes.size > OperationEnvelope.MAX_BODY_BYTES) {
                return ValidationResult(
                    ValidationStatus.INVALID_SIZE,
                    "El cuerpo excede el tamaño máximo permitido (${envelope.bodyBytes.size} bytes)"
                )
            }
            ValidationResult(ValidationStatus.VALID)
        } catch (e: Exception) {
            ValidationResult(ValidationStatus.INVALID_FORMAT, e.message)
        }
    }

    /**
     * Verifica criptográficamente la firma digital de la operación contra la clave pública del dispositivo (DK-s).
     * Dominio obligatorio: "arete/op/v1" || envelopeBytes
     */
    fun verifySignature(envelope: OperationEnvelope, devicePublicKey: PublicKey): Boolean {
        if (envelope.signatureBytes.isEmpty()) return false
        return try {
            val bytesToVerify = envelope.bytesToSign()
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(devicePublicKey)
            verifier.update(bytesToVerify)
            verifier.verify(envelope.signatureBytes)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Reconstruye una PublicKey P-256 desde bytes X.509 DER.
     */
    fun decodePublicKeyX509(encodedBytes: ByteArray): PublicKey? {
        return try {
            val keySpec = X509EncodedKeySpec(encodedBytes)
            val keyFactory = KeyFactory.getInstance("EC")
            keyFactory.generatePublic(keySpec)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Reconstruye una PublicKey P-256 desde bytes crudos sin comprimir (65 bytes: 0x04 || X || Y).
     */
    fun reconstructPublicKey(raw: ByteArray): PublicKey? {
        return try {
            if (raw.size != 65 || raw[0] != 0x04.toByte()) return null
            val x = java.math.BigInteger(1, raw.copyOfRange(1, 33))
            val y = java.math.BigInteger(1, raw.copyOfRange(33, 65))
            val ecSpec = java.security.AlgorithmParameters.getInstance("EC").also {
                it.init(java.security.spec.ECGenParameterSpec("secp256r1"))
            }.getParameterSpec(java.security.spec.ECParameterSpec::class.java)
            val kf = KeyFactory.getInstance("EC")
            kf.generatePublic(java.security.spec.ECPublicKeySpec(java.security.spec.ECPoint(x, y), ecSpec))
        } catch (_: Exception) {
            null
        }
    }
}
