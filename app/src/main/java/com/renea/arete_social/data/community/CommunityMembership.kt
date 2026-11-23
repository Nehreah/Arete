package com.renea.arete_social.data.community

import com.renea.arete_social.data.protocol.OperationEnvelope
import java.util.UUID
import com.renea.arete_social.domain.util.toHex

/**
 * Modelos de datos para el estado local de comunidades y membresías
 * en el protocolo descentralizado de Aréte.
 */
data class CommunityState(
    val communityId: ByteArray,
    val name: String,
    val description: String,
    val ownerIk: ByteArray,
    val leaderIk: ByteArray? = null,
    val currentHeadSeq: Long = 0L,
    val currentHeadHash: ByteArray = ByteArray(32),
    val currentEpochId: ByteArray = ByteArray(32),
    val members: Map<String, CommunityMemberState> = emptyMap(), // authorIkHex -> Member
    val rulesVersion: Int = 1,
    val isDissolved: Boolean = false
) {
    fun getMember(authorIk: ByteArray): CommunityMemberState? {
        val hex = authorIk.toHex()
        return members[hex]
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CommunityState

        if (!communityId.contentEquals(other.communityId)) return false
        if (name != other.name) return false
        if (description != other.description) return false
        if (!ownerIk.contentEquals(other.ownerIk)) return false
        if (leaderIk != null) {
            if (other.leaderIk == null) return false
            if (!leaderIk.contentEquals(other.leaderIk)) return false
        } else if (other.leaderIk != null) return false
        if (currentHeadSeq != other.currentHeadSeq) return false
        if (!currentHeadHash.contentEquals(other.currentHeadHash)) return false
        if (!currentEpochId.contentEquals(other.currentEpochId)) return false
        if (members != other.members) return false
        if (rulesVersion != other.rulesVersion) return false
        if (isDissolved != other.isDissolved) return false

        return true
    }

    override fun hashCode(): Int {
        var result = communityId.contentHashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + ownerIk.contentHashCode()
        result = 31 * result + (leaderIk?.contentHashCode() ?: 0)
        result = 31 * result + currentHeadSeq.hashCode()
        result = 31 * result + currentHeadHash.contentHashCode()
        result = 31 * result + currentEpochId.contentHashCode()
        result = 31 * result + members.hashCode()
        result = 31 * result + rulesVersion
        result = 31 * result + isDissolved.hashCode()
        return result
    }
}

data class CommunityMemberState(
    val authorIk: ByteArray,
    val role: CommunityAuthority.Role,
    val joinedAtOwnerSeq: Long,
    val designatedByIk: ByteArray? = null,
    val status: MemberStatus = MemberStatus.ACTIVE
) {
    enum class MemberStatus {
        ACTIVE,
        EXPELLED,
        LEFT
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CommunityMemberState

        if (!authorIk.contentEquals(other.authorIk)) return false
        if (role != other.role) return false
        if (joinedAtOwnerSeq != other.joinedAtOwnerSeq) return false
        if (designatedByIk != null) {
            if (other.designatedByIk == null) return false
            if (!designatedByIk.contentEquals(other.designatedByIk)) return false
        } else if (other.designatedByIk != null) return false
        if (status != other.status) return false

        return true
    }

    override fun hashCode(): Int {
        var result = authorIk.contentHashCode()
        result = 31 * result + role.hashCode()
        result = 31 * result + joinedAtOwnerSeq.hashCode()
        result = 31 * result + (designatedByIk?.contentHashCode() ?: 0)
        result = 31 * result + status.hashCode()
        return result
    }
}

data class JoinRequest(
    val requestId: UUID,
    val communityId: ByteArray,
    val applicantIk: ByteArray,
    val applicantDeviceId: UUID,
    val timestamp: Long,
    val sealedPayload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JoinRequest

        if (requestId != other.requestId) return false
        if (!communityId.contentEquals(other.communityId)) return false
        if (!applicantIk.contentEquals(other.applicantIk)) return false
        if (applicantDeviceId != other.applicantDeviceId) return false
        if (timestamp != other.timestamp) return false
        if (!sealedPayload.contentEquals(other.sealedPayload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = requestId.hashCode()
        result = 31 * result + communityId.contentHashCode()
        result = 31 * result + applicantIk.contentHashCode()
        result = 31 * result + applicantDeviceId.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + sealedPayload.contentHashCode()
        return result
    }
}
