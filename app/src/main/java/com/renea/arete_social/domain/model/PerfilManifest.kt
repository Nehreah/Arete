package com.renea.arete_social.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Secciones modulares del perfil para sincronización P2P incremental (v2).
 */
enum class TipoSeccionPerfil {
    BASE,        // Descripción y metadatos base
    GUSTOS,      // Gustos e intereses
    BITACORA,    // Estados de bitácora
    LIBRERIA,    // Obras literarias (cuentos, poemarios)
    CARTAS,      // Cartas públicas
    COMUNIDADES, // Comunidades locales
    EVENTOS      // Eventos locales
}

@Serializable
data class SeccionHeader(
    val version: Long = 1L,
    val hashSha256: String = "",
    val tamanoBytes: Int = 0
)

/**
 * Manifiesto liviano (<= 2 KB) anunciado por Bluetooth.
 * Permite al receptor comparar hashes y solicitar únicamente las secciones desactualizadas.
 */
@Serializable
data class ManifiestoPerfil(
    val v: Int = 2,
    val usuarioId: String,
    val nombre: String,
    val versionGlobal: Long = 1L,
    val esPrivado: Boolean = false,
    val fotoHash: String? = null,
    val portadaHash: String? = null,
    val secciones: Map<String, SeccionHeader> = emptyMap(),
    val actualizadoEn: Long = 0L
)

@Serializable
data class SeccionBaseDatos(
    val descripcion: String = ""
)

@Serializable
data class SeccionGustosDatos(
    val intereses: List<String> = emptyList(),
    val categorias: List<String> = emptyList(),
    val hobby: String? = null,
    val deporte: String? = null,
    val ritmoMusical: String? = null,
    val serieFavorita: String? = null,
    val libroFavorito: String? = null,
    val signoZodiacal: String? = null
)

@Serializable
data class SeccionBitacoraDatos(
    val estados: List<EstadoPerfilSnapshot> = emptyList()
)

@Serializable
data class SeccionLibreriaDatos(
    val obras: List<ObraPerfilSnapshot> = emptyList()
)

@Serializable
data class SeccionCartasDatos(
    val cartas: List<CartaResumenP2P> = emptyList()
)

@Serializable
data class CartaResumenP2P(
    val id: String,
    val titulo: String,
    val extracto: String,
    val fecha: Long
)

/**
 * Codificación y decodificación de mensajes del protocolo perfil v2 por BLE / Wi-Fi.
 */
object ProtocoloPerfilV2 {
    const val CHAT_ID_MANIFEST = "perfil-sync"
    const val CHAT_ID_SECCIONES = "perfil-sec"

    const val MAX_JSON_CHARS = 14_000
    private const val MAX_ID = 64

    private const val PREFIJO_REQ_MANIF = "[PERFIL_MANIF_REQ:v2:"
    private const val PREFIJO_RESP_MANIF = "[PERFIL_MANIF:v2:"
    private const val PREFIJO_REQ_SEC = "[SEC_REQ:v1:"
    private const val PREFIJO_DATOS_SEC = "[SEC_DATOS:v1:"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // --- MANIFIESTO ---

    fun pedirManifiesto(duenoId: String): String = "$PREFIJO_REQ_MANIF$duenoId]"

    fun parsearPedirManifiesto(contenido: String): String? {
        if (!contenido.startsWith(PREFIJO_REQ_MANIF) || !contenido.endsWith("]")) return null
        val id = contenido.removePrefix(PREFIJO_REQ_MANIF).removeSuffix("]")
        return id.takeIf { it.isNotBlank() && it.length <= MAX_ID && ' ' !in it && ']' !in it }
    }

    fun codificarManifiesto(manifiesto: ManifiestoPerfil): String {
        val payload = json.encodeToString(ManifiestoPerfil.serializer(), manifiesto)
        return "$PREFIJO_RESP_MANIF$payload]"
    }

    fun decodificarManifiesto(contenido: String): ManifiestoPerfil? {
        if (!contenido.startsWith(PREFIJO_RESP_MANIF) || !contenido.endsWith("]")) return null
        val cuerpo = contenido.removePrefix(PREFIJO_RESP_MANIF).removeSuffix("]")
        if (cuerpo.length > MAX_JSON_CHARS) return null
        return runCatching { json.decodeFromString(ManifiestoPerfil.serializer(), cuerpo) }.getOrNull()
    }

    // --- SECCIONES BAJO DEMANDA ---

    data class PeticionSeccion(
        val duenoId: String,
        val tipoSeccion: TipoSeccionPerfil,
        val version: Long
    )

    data class PaqueteSeccion(
        val duenoId: String,
        val tipoSeccion: TipoSeccionPerfil,
        val indice: Int,
        val total: Int,
        val payloadJson: String
    )

    fun pedirSeccion(duenoId: String, seccion: TipoSeccionPerfil, version: Long): String =
        "$PREFIJO_REQ_SEC$duenoId:${seccion.name}:$version]"

    fun parsearPedirSeccion(contenido: String): PeticionSeccion? {
        if (!contenido.startsWith(PREFIJO_REQ_SEC) || !contenido.endsWith("]")) return null
        val cuerpo = contenido.removePrefix(PREFIJO_REQ_SEC).removeSuffix("]")
        val partes = cuerpo.split(":")
        if (partes.size != 3) return null
        val duenoId = partes[0].takeIf { it.isNotBlank() && it.length <= MAX_ID } ?: return null
        val seccion = runCatching { TipoSeccionPerfil.valueOf(partes[1]) }.getOrNull() ?: return null
        val version = partes[2].toLongOrNull() ?: return null
        return PeticionSeccion(duenoId, seccion, version)
    }

    fun codificarSeccion(
        duenoId: String,
        seccion: TipoSeccionPerfil,
        indice: Int,
        total: Int,
        payloadJson: String
    ): String = "$PREFIJO_DATOS_SEC$duenoId:${seccion.name}:$indice:$total:$payloadJson]"

    fun parsearSeccion(contenido: String): PaqueteSeccion? {
        if (!contenido.startsWith(PREFIJO_DATOS_SEC) || !contenido.endsWith("]")) return null
        val cuerpo = contenido.removePrefix(PREFIJO_DATOS_SEC).removeSuffix("]")
        val partes = cuerpo.split(":", limit = 5)
        if (partes.size != 5) return null
        val duenoId = partes[0].takeIf { it.isNotBlank() && it.length <= MAX_ID } ?: return null
        val seccion = runCatching { TipoSeccionPerfil.valueOf(partes[1]) }.getOrNull() ?: return null
        val indice = partes[2].toIntOrNull() ?: return null
        val total = partes[3].toIntOrNull() ?: return null
        val payload = partes[4]
        if (indice < 0 || total <= 0 || indice >= total) return null
        return PaqueteSeccion(duenoId, seccion, indice, total, payload)
    }

    fun jsonHelper(): Json = json

    // --- OBRAS LARGAS BAJO DEMANDA (REQ_OBRA) ---

    data class PeticionObra(
        val duenoId: String,
        val obraId: String
    )

    data class PaqueteObra(
        val duenoId: String,
        val obraId: String,
        val indice: Int,
        val total: Int,
        val chunkTexto: String
    )

    private const val PREFIJO_REQ_OBRA = "[OBRA_REQ:v1:"
    private const val PREFIJO_DATOS_OBRA = "[OBRA_DATOS:v1:"

    fun pedirObra(duenoId: String, obraId: String): String =
        "$PREFIJO_REQ_OBRA$duenoId:$obraId]"

    fun parsearPedirObra(contenido: String): PeticionObra? {
        if (!contenido.startsWith(PREFIJO_REQ_OBRA) || !contenido.endsWith("]")) return null
        val cuerpo = contenido.removePrefix(PREFIJO_REQ_OBRA).removeSuffix("]")
        val partes = cuerpo.split(":")
        if (partes.size != 2) return null
        val duenoId = partes[0].takeIf { it.isNotBlank() && it.length <= MAX_ID } ?: return null
        val obraId = partes[1].takeIf { it.isNotBlank() } ?: return null
        return PeticionObra(duenoId, obraId)
    }

    fun codificarObraChunk(
        duenoId: String,
        obraId: String,
        indice: Int,
        total: Int,
        chunkTexto: String
    ): String = "$PREFIJO_DATOS_OBRA$duenoId:$obraId:$indice:$total:$chunkTexto]"

    fun parsearObraChunk(contenido: String): PaqueteObra? {
        if (!contenido.startsWith(PREFIJO_DATOS_OBRA) || !contenido.endsWith("]")) return null
        val cuerpo = contenido.removePrefix(PREFIJO_DATOS_OBRA).removeSuffix("]")
        val partes = cuerpo.split(":", limit = 5)
        if (partes.size != 5) return null
        val duenoId = partes[0].takeIf { it.isNotBlank() && it.length <= MAX_ID } ?: return null
        val obraId = partes[1].takeIf { it.isNotBlank() } ?: return null
        val indice = partes[2].toIntOrNull() ?: return null
        val total = partes[3].toIntOrNull() ?: return null
        val chunkTexto = partes[4]
        if (indice < 0 || total <= 0 || indice >= total) return null
        return PaqueteObra(duenoId, obraId, indice, total, chunkTexto)
    }
}
