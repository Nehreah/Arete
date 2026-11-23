package com.renea.arete_social.domain.model

import java.util.Base64

/**
 * Protocolo de transferencia de medios (miniaturas de avatar y portada) sobre la malla BLE / Wi-Fi.
 * Funciona en canal dedicado "perfil-media", de baja prioridad y en trozos de 2 KB.
 */
object ProtocoloPerfilMediaBle {

    const val CHAT_ID = "perfil-media"
    const val CHUNK_SIZE_BYTES = 2048 // 2 KB por trozo
    const val MAX_CHUNKS = 100 // Límite de seguridad (~200 KB máximo)

    private const val PREFIJO_PEDIR = "[MEDIA_REQ:v1:"
    private const val PREFIJO_CHUNK = "[MEDIA_CHUNK:v1:"

    data class TrozoMedia(
        val hashSha256: String,
        val indice: Int,
        val total: Int,
        val bytes: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is TrozoMedia) return false
            return hashSha256 == other.hashSha256 && indice == other.indice && total == other.total && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int {
            var result = hashSha256.hashCode()
            result = 31 * result + indice
            result = 31 * result + total
            result = 31 * result + bytes.contentHashCode()
            return result
        }
    }

    fun pedir(hashSha256: String): String = "$PREFIJO_PEDIR$hashSha256]"

    fun parsearPedir(contenido: String): String? {
        if (!contenido.startsWith(PREFIJO_PEDIR) || !contenido.endsWith("]")) return null
        val hash = contenido.removePrefix(PREFIJO_PEDIR).removeSuffix("]")
        return hash.takeIf { it.isNotBlank() && it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F' } }
    }

    fun codificarTrozo(hashSha256: String, indice: Int, total: Int, bytesTrozo: ByteArray): String {
        val base64Data = Base64.getEncoder().encodeToString(bytesTrozo)
        return "$PREFIJO_CHUNK$hashSha256:$indice:$total:$base64Data]"
    }

    fun parsearTrozo(contenido: String): TrozoMedia? {
        if (!contenido.startsWith(PREFIJO_CHUNK) || !contenido.endsWith("]")) return null
        val cuerpo = contenido.removePrefix(PREFIJO_CHUNK).removeSuffix("]")
        val partes = cuerpo.split(":", limit = 4)
        if (partes.size != 4) return null

        val hash = partes[0]
        if (hash.length != 64) return null
        val indice = partes[1].toIntOrNull() ?: return null
        val total = partes[2].toIntOrNull() ?: return null
        if (indice < 0 || total <= 0 || indice >= total || total > MAX_CHUNKS) return null

        val bytes = runCatching { Base64.getDecoder().decode(partes[3]) }.getOrNull() ?: return null
        return TrozoMedia(
            hashSha256 = hash,
            indice = indice,
            total = total,
            bytes = bytes
        )
    }

    private const val PREFIJO_MISSING = "[MEDIA_MISSING:v1:"

    fun pedirFaltantes(hashSha256: String, indices: List<Int>): String =
        "$PREFIJO_MISSING$hashSha256:${indices.joinToString(",")}]"

    data class PeticionFaltantes(val hashSha256: String, val indices: List<Int>)

    fun parsearFaltantes(contenido: String): PeticionFaltantes? {
        if (!contenido.startsWith(PREFIJO_MISSING) || !contenido.endsWith("]")) return null
        val cuerpo = contenido.removePrefix(PREFIJO_MISSING).removeSuffix("]")
        val partes = cuerpo.split(":", limit = 2)
        if (partes.size != 2) return null
        val hash = partes[0]
        if (hash.length != 64) return null
        val indices = partes[1].split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 0 until MAX_CHUNKS }
        if (indices.isEmpty()) return null
        return PeticionFaltantes(hashSha256 = hash, indices = indices)
    }
}
