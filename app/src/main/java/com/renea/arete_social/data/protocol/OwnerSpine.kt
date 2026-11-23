package com.renea.arete_social.data.protocol

import java.util.concurrent.ConcurrentHashMap
import com.renea.arete_social.domain.util.toHex

/**
 * Representa y valida la Columna del Dueño (Owner Spine) y los cortes de revocación
 * según las secciones 9.1 y 9.2 de la auditoría arquitectónica v3.
 *
 * La columna del dueño es una cadena lineal de autor único (el dueño de la comunidad).
 * Cada operación delegada referencia una cabeza conocida (ownerHeadSeq, ownerHeadHash).
 */
class OwnerSpine(
    val communityId: ByteArray,
    val ownerIk: ByteArray
) {
    enum class EvaluationStatus {
        CONFIRMED,
        PROVISIONAL,
        SUSPICIOUS,
        INVALID_CUTOFF,
        INVALID_AUTHORITY
    }

    data class OwnerBlock(
        val ownerSeq: Long,
        val opHash: ByteArray,
        val operationType: Short,
        val cutoffSeq: Long? = null,
        val targetIk: ByteArray? = null,
        val timestamp: Long
    )

    // Mapa de altura a bloque del dueño
    private val spineBlocks = ConcurrentHashMap<Long, OwnerBlock>()
    private var currentHeadSeq: Long = 0L
    private var currentHeadHash: ByteArray = ByteArray(32)

    // Cortes de revocación activos: Clave de identidad afectada -> cutoffOwnerSeq
    private val activeRevocationCutoffs = ConcurrentHashMap<String, Long>()

    @Synchronized
    fun appendOwnerBlock(
        ownerSeq: Long,
        opHash: ByteArray,
        operationType: Short,
        cutoffSeq: Long? = null,
        targetIk: ByteArray? = null,
        timestamp: Long
    ) {
        require(ownerSeq == currentHeadSeq + 1 || (currentHeadSeq == 0L && ownerSeq == 1L)) {
            "Secuencia de dueño no contigua: esperada ${currentHeadSeq + 1}, recibida $ownerSeq"
        }

        val block = OwnerBlock(
            ownerSeq = ownerSeq,
            opHash = opHash,
            operationType = operationType,
            cutoffSeq = cutoffSeq,
            targetIk = targetIk,
            timestamp = timestamp
        )

        spineBlocks[ownerSeq] = block
        currentHeadSeq = ownerSeq
        currentHeadHash = opHash

        // Si es revocación con corte explícito, registrar el corte para la identidad afectada
        if (targetIk != null && cutoffSeq != null) {
            val targetKey = targetIk.toHex()
            val existingCutoff = activeRevocationCutoffs[targetKey]
            if (existingCutoff == null || cutoffSeq < existingCutoff) {
                // Prevalece el corte más estricto/más abajo
                activeRevocationCutoffs[targetKey] = cutoffSeq
            }
        }
    }

    /**
     * Evalúa una operación delegada según el corte de revocación y la cabeza conocida.
     *
     * Regla 9.2:
     * - Si authorIk tiene revocación con corte:
     *     - ownerHeadSeq >= cutoffOwnerSeq -> INVÁLIDA
     *     - ownerHeadSeq < cutoffOwnerSeq -> VÁLIDA para su momento
     * - Si timestamp > timestampRevocacion + tolerancia pero ownerHeadSeq < cutoffOwnerSeq -> SOSPECHOSA (fechada hacia atrás)
     */
    fun evaluateDelegatedOperation(
        envelope: OperationEnvelope,
        clockSkewToleranceMs: Long = 5 * 60 * 1000L // 5 minutos de tolerancia
    ): EvaluationStatus {
        val authorKey = envelope.authorIk.toHex()
        val cutoff = activeRevocationCutoffs[authorKey]

        if (cutoff != null) {
            if (envelope.ownerHeadSeq >= cutoff) {
                return EvaluationStatus.INVALID_CUTOFF
            }

            // Mitigación contra operaciones fechadas hacia atrás (Sección 9.2 de auditoría):
            // Si la marca de tiempo de la operación es posterior a la revocación más la tolerancia
            val revocationBlock = spineBlocks.values.firstOrNull {
                it.targetIk != null && it.targetIk.contentEquals(envelope.authorIk)
            }
            if (revocationBlock != null && envelope.timestamp > revocationBlock.timestamp + clockSkewToleranceMs) {
                return EvaluationStatus.SUSPICIOUS
            }
        }

        // Si la cabeza del dueño que el autor conocía está dentro de nuestra cadena confirmada
        if (envelope.ownerHeadSeq == 0L) {
            // Caso base: génesis o secuencia inicial sin corte activo
            if (envelope.ownerHeadHash.all { it == 0.toByte() } || spineBlocks[1L]?.opHash?.contentEquals(envelope.ownerHeadHash) == true) {
                return EvaluationStatus.CONFIRMED
            }
        }

        val knownBlock = spineBlocks[envelope.ownerHeadSeq]
        if (knownBlock != null && knownBlock.opHash.contentEquals(envelope.ownerHeadHash)) {
            return EvaluationStatus.CONFIRMED
        }

        // Si no hay corte de revocación activo para este autor y su referencia no excede nuestra cabeza
        if (cutoff == null && envelope.ownerHeadSeq <= currentHeadSeq) {
            return EvaluationStatus.CONFIRMED
        }

        // Si la cabeza referenciada es superior a lo que conocemos o difiere en hash, es provisional
        return EvaluationStatus.PROVISIONAL
    }

    fun getHeadSeq(): Long = currentHeadSeq
    fun getHeadHash(): ByteArray = currentHeadHash.clone()
}
