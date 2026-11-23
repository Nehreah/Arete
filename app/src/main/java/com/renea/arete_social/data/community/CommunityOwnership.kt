package com.renea.arete_social.data.community

import com.renea.arete_social.data.protocol.OperationEnvelope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.PublicKey

/**
 * Gestión de Propiedad, Sucesión y Herederos según la sección 7.4 de la auditoría v3.
 *
 * Reglas de sucesión:
 * 1. Transferencia Voluntaria (OWNERSHIP_TRANSFER, 0x0006):
 *    El dueño actual transfiere la propiedad a un miembro designado. La operación solo es válida
 *    si está firmada por el dueño actual en la Columna del Dueño.
 * 2. Designación de Heredero (HEIR_DESIGNATE, 0x0007):
 *    El dueño designa una IK sucesora que podrá reclamar la titularidad en caso de pérdida o ausencia prolongada.
 * 3. Anulación de Heredero (HEIR_CANCEL, 0x0008):
 *    El dueño revoca al heredero designado previamente.
 * 4. Reclamo de Propiedad (HEIR_CLAIM, 0x0018):
 *    El heredero designado reclama la titularidad. Para ser válido, requiere:
 *    - Que la IK coincida con el heredero registrado en la cabeza consolidada.
 *    - Que hayan transcurrido al menos [INACTIVITY_THRESHOLD_MS] (90 días) desde la última operación del dueño,
 *      O que cuente con la contrafirma válida del Líder de la comunidad.
 */
class CommunityOwnership(
    private val ledger: CommunityLedger
) {
    companion object {
        const val INACTIVITY_THRESHOLD_MS = 90L * 24 * 60 * 60 * 1000L // 90 días de inactividad
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class OwnershipTransferBody(
        val newOwnerIkHex: String,
        val reason: String = ""
    )

    @Serializable
    data class HeirDesignateBody(
        val heirIkHex: String,
        val conditions: String = ""
    )

    @Serializable
    data class HeirClaimBody(
        val heirIkHex: String,
        val claimTimestampMs: Long,
        val leaderCountersignatureHex: String? = null
    )

    data class SuccessionState(
        val currentOwnerIkHex: String,
        val designatedHeirIkHex: String? = null,
        val lastOwnerActivityTimestampMs: Long = 0L
    )

    /**
     * Evalúa si una operación de reclamo de heredero es legítima según las reglas de corte y causalidad.
     */
    fun evaluateHeirClaim(
        claimEnvelope: OperationEnvelope,
        designatedHeirIk: ByteArray?,
        lastOwnerOpTimestampMs: Long,
        leaderIkPublicKey: PublicKey? = null,
        verifyLeaderSignature: ((payload: ByteArray, sigHex: String, key: PublicKey) -> Boolean)? = null
    ): Boolean {
        if (claimEnvelope.operationType != OperationEnvelope.TYPE_HEIR_CLAIM) return false
        if (designatedHeirIk == null) return false

        // La IK del autor del reclamo debe coincidir con el heredero registrado
        if (!claimEnvelope.authorIk.contentEquals(designatedHeirIk)) {
            return false
        }

        val body = runCatching {
            val jsonString = String(claimEnvelope.bodyBytes, Charsets.UTF_8)
            json.decodeFromString(HeirClaimBody.serializer(), jsonString)
        }.getOrNull() ?: return false

        // Condición A: Período de inactividad del dueño superado
        val elapsedSinceOwnerActivity = claimEnvelope.timestamp - lastOwnerOpTimestampMs
        if (elapsedSinceOwnerActivity >= INACTIVITY_THRESHOLD_MS) {
            return true
        }

        // Condición B: Contrafirma del Líder presente y válida
        val countersigHex = body.leaderCountersignatureHex
        if (!countersigHex.isNullOrBlank() && leaderIkPublicKey != null && verifyLeaderSignature != null) {
            val bodyWithoutCountersig = serializeHeirClaimBody(body.heirIkHex, body.claimTimestampMs, null)
            val envelopeToVerify = claimEnvelope.copy(bodyBytes = bodyWithoutCountersig)
            val payloadToVerify = envelopeToVerify.bytesToSign()
            return verifyLeaderSignature(payloadToVerify, countersigHex, leaderIkPublicKey)
        }

        return false
    }

    /**
     * Serializa el cuerpo para una transferencia voluntaria de propiedad.
     */
    fun serializeTransferBody(newOwnerIkHex: String, reason: String = ""): ByteArray {
        val body = OwnershipTransferBody(newOwnerIkHex, reason)
        return json.encodeToString(OwnershipTransferBody.serializer(), body).toByteArray(Charsets.UTF_8)
    }

    /**
     * Serializa el cuerpo para la designación de un heredero.
     */
    fun serializeHeirDesignateBody(heirIkHex: String, conditions: String = ""): ByteArray {
        val body = HeirDesignateBody(heirIkHex, conditions)
        return json.encodeToString(HeirDesignateBody.serializer(), body).toByteArray(Charsets.UTF_8)
    }

    /**
     * Serializa el cuerpo para el reclamo de propiedad por heredero.
     */
    fun serializeHeirClaimBody(
        heirIkHex: String,
        claimTimestampMs: Long,
        leaderCountersignatureHex: String? = null
    ): ByteArray {
        val body = HeirClaimBody(heirIkHex, claimTimestampMs, leaderCountersignatureHex)
        return json.encodeToString(HeirClaimBody.serializer(), body).toByteArray(Charsets.UTF_8)
    }
}
