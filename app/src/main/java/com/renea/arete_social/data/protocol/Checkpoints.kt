package com.renea.arete_social.data.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Representa un punto de control (Checkpoint) firmado para compactación y arranque rápido,
 * según la sección 9.5 de la auditoría arquitectónica v3.
 *
 * El checkpoint consolida el estado derivado en una altura específica de la columna del dueño (ownerSeq).
 */
@Serializable
data class CommunityCheckpointState(
    val communityIdHex: String,
    val ownerSeq: Long,
    val ownerHeadHashHex: String,
    val epochIdHex: String,
    val members: List<CheckpointMember>,
    val rulesVersion: Int,
    val timestamp: Long
) {
    @Serializable
    data class CheckpointMember(
        val authorIkHex: String,
        val role: String, // "OWNER", "LEADER", "ADMIN", "MODERATOR", "MEMBER"
        val joinedAtOwnerSeq: Long
    )

    fun toBodyBytes(): ByteArray {
        val jsonString = Json.encodeToString(this)
        return jsonString.toByteArray(Charsets.UTF_8)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromBodyBytes(bytes: ByteArray): CommunityCheckpointState {
            val jsonString = String(bytes, Charsets.UTF_8)
            return json.decodeFromString(jsonString)
        }
    }
}
