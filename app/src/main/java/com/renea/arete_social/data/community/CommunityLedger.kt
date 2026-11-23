package com.renea.arete_social.data.community

import com.renea.arete_social.data.protocol.OperationEnvelope
import com.renea.arete_social.data.protocol.OwnerSpine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import com.renea.arete_social.domain.util.toHex
import com.renea.arete_social.domain.util.hexToByteArray

/**
 * Gestiona el registro de operaciones firmadas (ledger) para una comunidad local en Aréte,
 * aplicando las reglas de autoridad, causales y de corte según las secciones 7, 8 y 9 de la auditoría v3.
 */
class CommunityLedger(
    val communityId: ByteArray,
    val ownerIk: ByteArray,
    val initialName: String = "",
    val initialDescription: String = ""
) {
    private val spine = OwnerSpine(communityId, ownerIk)
    private val operations = ConcurrentHashMap<String, OperationEnvelope>() // opIdHex -> Envelope
    private val operationStatuses = ConcurrentHashMap<String, OwnerSpine.EvaluationStatus>()

    // Estado derivado consolidado
    @Volatile
    private var state = CommunityState(
        communityId = communityId,
        name = initialName,
        description = initialDescription,
        ownerIk = ownerIk,
        members = mapOf(
            ownerIk.toHex() to CommunityMemberState(
                authorIk = ownerIk,
                role = CommunityAuthority.Role.OWNER,
                joinedAtOwnerSeq = 0L,
                status = CommunityMemberState.MemberStatus.ACTIVE
            )
        )
    )

    @Serializable
    data class GenesisBody(
        val name: String,
        val description: String,
        val initialEpochIdHex: String,
        val tipo: String = "PRIVADA",
        val fotoUrl: String? = null
    )

    @Serializable
    data class RoleDesignateBody(
        val targetIkHex: String,
        val role: String // "LEADER", "ADMIN", "MODERATOR"
    )

    @Serializable
    data class ExpelBody(
        val targetIkHex: String,
        val reason: String = ""
    )

    fun getState(): CommunityState = state

    fun getOperation(opIdHex: String): OperationEnvelope? = operations[opIdHex]

    fun getOperationStatus(opIdHex: String): OwnerSpine.EvaluationStatus? = operationStatuses[opIdHex]

    /**
     * Aplica y almacena una operación en el registro local tras evaluar su autoridad y corte.
     */
    @Synchronized
    fun applyOperation(envelope: OperationEnvelope): OwnerSpine.EvaluationStatus {
        val opIdHex = envelope.calculateOpIdHex()
        if (operations.containsKey(opIdHex)) {
            return operationStatuses[opIdHex] ?: OwnerSpine.EvaluationStatus.CONFIRMED
        }

        // Si es una operación de la columna del dueño
        if (isOwnerOperation(envelope.operationType)) {
            if (!envelope.authorIk.contentEquals(ownerIk)) {
                return OwnerSpine.EvaluationStatus.INVALID_AUTHORITY
            }

            val expectedSeq = spine.getHeadSeq() + 1
            if (envelope.deviceSeq != expectedSeq) {
                // Desincronizado o hueco en la secuencia del dueño
                return OwnerSpine.EvaluationStatus.PROVISIONAL
            }

            val opHash = envelope.calculateOpId()
            spine.appendOwnerBlock(
                ownerSeq = envelope.deviceSeq,
                opHash = opHash,
                operationType = envelope.operationType,
                cutoffSeq = if (envelope.operationType == OperationEnvelope.TYPE_LEADER_REVOKE) envelope.deviceSeq else null,
                targetIk = extractTargetIk(envelope),
                timestamp = envelope.timestamp
            )

            updateStateWithOperation(envelope)
            operations[opIdHex] = envelope
            operationStatuses[opIdHex] = OwnerSpine.EvaluationStatus.CONFIRMED
            return OwnerSpine.EvaluationStatus.CONFIRMED
        }

        // Operación delegada o de miembro: evaluada contra la columna del dueño
        val status = spine.evaluateDelegatedOperation(envelope)
        if (status == OwnerSpine.EvaluationStatus.INVALID_CUTOFF || status == OwnerSpine.EvaluationStatus.INVALID_AUTHORITY) {
            return status
        }

        // Verificar autoridad según el estado actual
        val authorMember = state.getMember(envelope.authorIk)
        if (authorMember == null || authorMember.status != CommunityMemberState.MemberStatus.ACTIVE) {
            if (envelope.operationType != OperationEnvelope.TYPE_JOIN_REQUEST) {
                return OwnerSpine.EvaluationStatus.INVALID_AUTHORITY
            }
        } else {
            // Validar permisos según el rol jerárquico del autor
            val targetIk = extractTargetIk(envelope)
            val targetMember = targetIk?.let { state.getMember(it) }

            when (envelope.operationType) {
                OperationEnvelope.TYPE_ADMIN_DESIGNATE -> {
                    if (!CommunityAuthority.hasPermission(authorMember.role, CommunityAuthority.Permission.DESIGNATE_ADMIN)) {
                        return OwnerSpine.EvaluationStatus.INVALID_AUTHORITY
                    }
                    val currentRole = targetMember?.role ?: CommunityAuthority.Role.MEMBER
                    if (!CommunityAuthority.validateRoleChange(authorMember.role, currentRole, CommunityAuthority.Role.ADMIN)) {
                        return OwnerSpine.EvaluationStatus.INVALID_AUTHORITY
                    }
                }
                OperationEnvelope.TYPE_MODERATOR_DESIGNATE -> {
                    if (!CommunityAuthority.hasPermission(authorMember.role, CommunityAuthority.Permission.DESIGNATE_MODERATOR)) {
                        return OwnerSpine.EvaluationStatus.INVALID_AUTHORITY
                    }
                    val currentRole = targetMember?.role ?: CommunityAuthority.Role.MEMBER
                    if (!CommunityAuthority.validateRoleChange(authorMember.role, currentRole, CommunityAuthority.Role.MODERATOR)) {
                        return OwnerSpine.EvaluationStatus.INVALID_AUTHORITY
                    }
                }
                OperationEnvelope.TYPE_ROLE_REVOKE -> {
                    val currentRole = targetMember?.role ?: CommunityAuthority.Role.MEMBER
                    if (!authorMember.role.canModify(currentRole)) {
                        return OwnerSpine.EvaluationStatus.INVALID_AUTHORITY
                    }
                }
                OperationEnvelope.TYPE_JOIN_ACCEPT -> {
                    if (!CommunityAuthority.hasPermission(authorMember.role, CommunityAuthority.Permission.APPROVE_JOIN)) {
                        return OwnerSpine.EvaluationStatus.INVALID_AUTHORITY
                    }
                }
                OperationEnvelope.TYPE_EXPEL -> {
                    if (!CommunityAuthority.hasPermission(authorMember.role, CommunityAuthority.Permission.EXPEL_MEMBER)) {
                        return OwnerSpine.EvaluationStatus.INVALID_AUTHORITY
                    }
                    val currentRole = targetMember?.role ?: CommunityAuthority.Role.MEMBER
                    if (!authorMember.role.canModify(currentRole)) {
                        return OwnerSpine.EvaluationStatus.INVALID_AUTHORITY
                    }
                }
                OperationEnvelope.TYPE_EPOCH_ROTATE -> {
                    if (!CommunityAuthority.hasPermission(authorMember.role, CommunityAuthority.Permission.ROTATE_EPOCH)) {
                        return OwnerSpine.EvaluationStatus.INVALID_AUTHORITY
                    }
                }
            }
        }

        operations[opIdHex] = envelope
        operationStatuses[opIdHex] = status

        if (status == OwnerSpine.EvaluationStatus.CONFIRMED || status == OwnerSpine.EvaluationStatus.PROVISIONAL) {
            updateStateWithOperation(envelope)
        }

        return status
    }

    private fun isOwnerOperation(type: Short): Boolean {
        return type in listOf(
            OperationEnvelope.TYPE_GENESIS,
            OperationEnvelope.TYPE_RULE_CHANGE,
            OperationEnvelope.TYPE_LEADER_DESIGNATE,
            OperationEnvelope.TYPE_LEADER_REVOKE,
            OperationEnvelope.TYPE_REVOKE_SUBTREE,
            OperationEnvelope.TYPE_OWNERSHIP_TRANSFER,
            OperationEnvelope.TYPE_HEIR_DESIGNATE,
            OperationEnvelope.TYPE_HEIR_CANCEL,
            OperationEnvelope.TYPE_CHECKPOINT,
            OperationEnvelope.TYPE_DISSOLVE
        )
    }

    private fun extractTargetIk(envelope: OperationEnvelope): ByteArray? {
        return try {
            val jsonString = String(envelope.bodyBytes, Charsets.UTF_8)
            val json = Json { ignoreUnknownKeys = true }
            val body = json.decodeFromString<RoleDesignateBody>(jsonString)
            body.targetIkHex.hexToByteArray()
        } catch (_: Exception) {
            null
        }
    }

    private fun updateStateWithOperation(envelope: OperationEnvelope) {
        val json = Json { ignoreUnknownKeys = true }
        val bodyString = String(envelope.bodyBytes, Charsets.UTF_8)

        when (envelope.operationType) {
            OperationEnvelope.TYPE_GENESIS -> {
                runCatching {
                    val gen = json.decodeFromString<GenesisBody>(bodyString)
                    state = state.copy(
                        name = gen.name,
                        description = gen.description,
                        currentHeadSeq = envelope.deviceSeq,
                        currentHeadHash = envelope.calculateOpId(),
                        currentEpochId = gen.initialEpochIdHex.hexToByteArray()
                    )
                }
            }
            OperationEnvelope.TYPE_LEADER_DESIGNATE -> {
                runCatching {
                    val body = json.decodeFromString<RoleDesignateBody>(bodyString)
                    val targetIk = body.targetIkHex.hexToByteArray()
                    val targetKey = body.targetIkHex.lowercase()
                    val updatedMembers = state.members.toMutableMap()

                    // Degradar líder previo a ADMIN si existe y no es el nuevo líder ni el dueño
                    state.leaderIk?.let { prevLeaderBytes ->
                        val prevKey = prevLeaderBytes.toHex().lowercase()
                        if (prevKey != targetKey && !prevLeaderBytes.contentEquals(state.ownerIk)) {
                            val prevMember = updatedMembers[prevKey]
                            if (prevMember != null && prevMember.role == CommunityAuthority.Role.LEADER) {
                                updatedMembers[prevKey] = prevMember.copy(role = CommunityAuthority.Role.ADMIN)
                            }
                        }
                    }

                    updatedMembers[targetKey] = CommunityMemberState(
                        authorIk = targetIk,
                        role = CommunityAuthority.Role.LEADER,
                        joinedAtOwnerSeq = envelope.deviceSeq,
                        designatedByIk = envelope.authorIk,
                        status = CommunityMemberState.MemberStatus.ACTIVE
                    )
                    state = state.copy(
                        leaderIk = targetIk,
                        currentHeadSeq = envelope.deviceSeq,
                        currentHeadHash = envelope.calculateOpId(),
                        members = updatedMembers
                    )
                }
            }
            OperationEnvelope.TYPE_ADMIN_DESIGNATE -> {
                runCatching {
                    val body = json.decodeFromString<RoleDesignateBody>(bodyString)
                    val targetIk = body.targetIkHex.hexToByteArray()
                    val targetKey = body.targetIkHex.lowercase()
                    val updatedMembers = state.members.toMutableMap()
                    val currentMember = updatedMembers[targetKey]
                    updatedMembers[targetKey] = (currentMember ?: CommunityMemberState(
                        authorIk = targetIk,
                        role = CommunityAuthority.Role.ADMIN,
                        joinedAtOwnerSeq = envelope.ownerHeadSeq,
                        designatedByIk = envelope.authorIk
                    )).copy(
                        role = CommunityAuthority.Role.ADMIN,
                        status = CommunityMemberState.MemberStatus.ACTIVE
                    )
                    state = state.copy(members = updatedMembers)
                }
            }
            OperationEnvelope.TYPE_MODERATOR_DESIGNATE -> {
                runCatching {
                    val body = json.decodeFromString<RoleDesignateBody>(bodyString)
                    val targetIk = body.targetIkHex.hexToByteArray()
                    val targetKey = body.targetIkHex.lowercase()
                    val updatedMembers = state.members.toMutableMap()
                    val currentMember = updatedMembers[targetKey]
                    updatedMembers[targetKey] = (currentMember ?: CommunityMemberState(
                        authorIk = targetIk,
                        role = CommunityAuthority.Role.MODERATOR,
                        joinedAtOwnerSeq = envelope.ownerHeadSeq,
                        designatedByIk = envelope.authorIk
                    )).copy(
                        role = CommunityAuthority.Role.MODERATOR,
                        status = CommunityMemberState.MemberStatus.ACTIVE
                    )
                    state = state.copy(members = updatedMembers)
                }
            }
            OperationEnvelope.TYPE_ROLE_REVOKE -> {
                runCatching {
                    val body = json.decodeFromString<RoleDesignateBody>(bodyString)
                    val targetKey = body.targetIkHex.lowercase()
                    val updatedMembers = state.members.toMutableMap()
                    val currentMember = updatedMembers[targetKey]
                    if (currentMember != null) {
                        updatedMembers[targetKey] = currentMember.copy(
                            role = CommunityAuthority.Role.MEMBER
                        )
                        state = state.copy(members = updatedMembers)
                    }
                }
            }
            OperationEnvelope.TYPE_JOIN_ACCEPT -> {
                runCatching {
                    val body = json.decodeFromString<RoleDesignateBody>(bodyString)
                    val targetIk = body.targetIkHex.hexToByteArray()
                    val targetKey = body.targetIkHex.lowercase()
                    val updatedMembers = state.members.toMutableMap()
                    val existing = updatedMembers[targetKey]
                    val assignedRole = if (existing != null && existing.role != CommunityAuthority.Role.MEMBER) {
                        existing.role
                    } else {
                        CommunityAuthority.Role.MEMBER
                    }
                    updatedMembers[targetKey] = (existing ?: CommunityMemberState(
                        authorIk = targetIk,
                        role = assignedRole,
                        joinedAtOwnerSeq = envelope.ownerHeadSeq,
                        designatedByIk = envelope.authorIk
                    )).copy(
                        role = assignedRole,
                        status = CommunityMemberState.MemberStatus.ACTIVE
                    )
                    state = state.copy(members = updatedMembers)
                }
            }
            OperationEnvelope.TYPE_LEAVE -> {
                runCatching {
                    val targetKey = envelope.authorIk.toHex().lowercase()
                    val currentMember = state.members[targetKey]
                    // El creador/dueño no puede abandonar la comunidad directamente
                    if (currentMember != null && currentMember.role != CommunityAuthority.Role.OWNER) {
                        val updatedMembers = state.members.toMutableMap()
                        updatedMembers[targetKey] = currentMember.copy(
                            status = CommunityMemberState.MemberStatus.LEFT
                        )
                        state = state.copy(members = updatedMembers)
                    }
                }
            }
            OperationEnvelope.TYPE_EXPEL -> {
                runCatching {
                    val body = json.decodeFromString<ExpelBody>(bodyString)
                    val targetKey = body.targetIkHex.lowercase()
                    val currentMember = state.members[targetKey]
                    if (currentMember != null && currentMember.role != CommunityAuthority.Role.OWNER) {
                        val updatedMembers = state.members.toMutableMap()
                        updatedMembers[targetKey] = currentMember.copy(
                            status = CommunityMemberState.MemberStatus.EXPELLED
                        )
                        state = state.copy(members = updatedMembers)
                    }
                }
            }
            OperationEnvelope.TYPE_EPOCH_ROTATE -> {
                if (envelope.epochId.any { it != 0.toByte() }) {
                    state = state.copy(currentEpochId = envelope.epochId)
                }
            }
        }
    }
}
