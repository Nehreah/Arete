package com.renea.arete_social.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Perfil en SOLO TEXTO para verlo por Bluetooth, sin internet. Un perfil público se entrega a cualquier
 * dispositivo cercano; uno privado, solo a las amistades del dueño.
 *
 * Qué viaja: estados (bitácora), cuentos y poemarios en texto, nombre y descripción.
 * Qué NO viaja nunca: bytes crudos de fotos ni PDF (las miniaturas viajan vía hash/canal media).
 */
@Serializable
data class EstadoPerfilSnapshot(
    val id: String,
    val texto: String = "",
    val ts: Long,
    val fotoHash: String? = null
)

@Serializable
data class ObraPerfilSnapshot(
    val id: String,
    val tipo: String,
    val titulo: String,
    val contenido: String,
    /** true cuando el texto se cortó para caber en un paquete Bluetooth. */
    val recortada: Boolean = false,
    val ts: Long
)

@Serializable
data class SnapshotPerfil(
    val v: Int = 1,
    val usuarioId: String,
    val nombre: String = "",
    val descripcion: String = "",
    val foto: String? = null,
    val portada: String? = null,
    val fotoHash: String? = null,
    val portadaHash: String? = null,
    val perfilVer: Long = 0L,
    /**
     * true = solo se entrega a amistades; false = perfil público, se entrega a cualquiera cercano.
     * Por defecto false para facilitar el descubrimiento local entre personas cercanas.
     */
    val privado: Boolean = false,
    /** En el dispositivo que lo recibe: cuándo se recibió. En el del dueño: última actualización. */
    val actualizadoEn: Long = 0L,
    val estados: List<EstadoPerfilSnapshot> = emptyList(),
    val obras: List<ObraPerfilSnapshot> = emptyList(),
    /**
     * Gustos e intereses recibidos mediante protocolo v2 (sección GUSTOS).
     * Null cuando no se ha recibido la sección o el par es desconocido.
     */
    val intereses: List<String>? = null,
    val hobby: String? = null,
    val deporte: String? = null,
    val ritmoMusical: String? = null,
    val serieFavorita: String? = null,
    val libroFavorito: String? = null,
    val signoZodiacal: String? = null
)

/**
 * Protocolo de petición/respuesta del perfil sobre la malla BLE.
 *
 * - Petición: `[PERFIL_PEDIR:v1:<idDelDueño>]`, a los vecinos directos (TTL 1, no se retransmite).
 * - Respuesta: `[PERFIL_DATOS:v1:<json>]`, solo la envía el DUEÑO, dirigida a quien preguntó.
 *
 * La seguridad sale de la malla: cada paquete va firmado con la identidad ECDSA del remitente y el
 * router descarta los que no verifican. Aquí además se exige que quien responde sea el dueño del
 * perfil (nadie puede "reenviar" ni inventar el perfil de otra persona).
 */
object ProtocoloPerfilBle {
    const val CHAT_ID = "perfil-sync"

    /** Tope del JSON (el paquete mesh admite 16.000 caracteres en total). */
    const val MAX_JSON_CHARS = 14_000
    const val MAX_ESTADOS = 10
    const val MAX_OBRAS = 20
    const val MAX_TEXTO_ESTADO = 1_000
    const val MAX_CONTENIDO_OBRA = 2_000
    const val MAX_TITULO = 120
    const val MAX_NOMBRE = 80
    const val MAX_DESCRIPCION = 300
    private const val MAX_ID = 64

    private const val PREFIJO_PEDIR = "[PERFIL_PEDIR:v1:"
    private const val PREFIJO_DATOS = "[PERFIL_DATOS:v1:"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun pedir(duenoId: String): String = "$PREFIJO_PEDIR$duenoId]"

    /** Devuelve el id del dueño pedido, o null si [contenido] no es una petición válida. */
    fun parsearPedir(contenido: String): String? {
        if (!contenido.startsWith(PREFIJO_PEDIR) || !contenido.endsWith("]")) return null
        val id = contenido.removePrefix(PREFIJO_PEDIR).removeSuffix("]")
        return id.takeIf { it.isNotBlank() && it.length <= MAX_ID && ' ' !in it && ']' !in it }
    }

    /**
     * Arma la respuesta ajustándola al tamaño máximo: primero los estados más nuevos y luego las
     * obras (con el texto recortado). Lo que no cabe se omite; con internet se ve completo.
     */
    fun codificarDatos(snapshot: SnapshotPerfil): String {
        // En BLE enviamos únicamente URLs remotas o hashes de medios locales; los bytes de imagen no se embeben
        val fotoRemota = snapshot.foto?.takeIf { it.startsWith("http://") || it.startsWith("https://") }?.take(500)
        val portadaRemota = snapshot.portada?.takeIf { it.startsWith("http://") || it.startsWith("https://") }?.take(500)

        var actual = snapshot.copy(
            nombre = snapshot.nombre.take(MAX_NOMBRE),
            descripcion = snapshot.descripcion.take(MAX_DESCRIPCION),
            foto = fotoRemota,
            portada = portadaRemota,
            fotoHash = snapshot.fotoHash?.take(64),
            portadaHash = snapshot.portadaHash?.take(64),
            perfilVer = snapshot.perfilVer,
            estados = emptyList(),
            obras = emptyList()
        )
        fun cabe(s: SnapshotPerfil) = json.encodeToString(SnapshotPerfil.serializer(), s).length <= MAX_JSON_CHARS

        if (!cabe(actual)) {
            actual = actual.copy(portada = null)
        }
        if (!cabe(actual)) {
            actual = actual.copy(foto = null)
        }

        for (e in snapshot.estados.sortedByDescending { it.ts }.take(MAX_ESTADOS)) {
            val candidato = actual.copy(
                estados = actual.estados + e.copy(id = e.id.take(MAX_ID), texto = e.texto.take(MAX_TEXTO_ESTADO))
            )
            if (cabe(candidato)) actual = candidato else break
        }
        for (o in snapshot.obras.sortedByDescending { it.ts }.take(MAX_OBRAS)) {
            val candidato = actual.copy(
                obras = actual.obras + o.copy(
                    id = o.id.take(MAX_ID),
                    titulo = o.titulo.take(MAX_TITULO),
                    contenido = o.contenido.take(MAX_CONTENIDO_OBRA),
                    recortada = o.recortada || o.contenido.length > MAX_CONTENIDO_OBRA
                )
            )
            if (cabe(candidato)) actual = candidato else break
        }
        return PREFIJO_DATOS + json.encodeToString(SnapshotPerfil.serializer(), actual) + "]"
    }

    /**
     * Valida y limpia una respuesta recibida de un dispositivo ajeno. Devuelve null si está mal
     * formada o es demasiado grande.
     */
    fun decodificarDatos(contenido: String): SnapshotPerfil? {
        if (!contenido.startsWith(PREFIJO_DATOS) || !contenido.endsWith("]")) return null
        val crudo = contenido.removePrefix(PREFIJO_DATOS).removeSuffix("]")
        if (crudo.length > MAX_JSON_CHARS) return null
        val s = runCatching { json.decodeFromString(SnapshotPerfil.serializer(), crudo) }.getOrNull() ?: return null
        if (s.v != 1 || s.usuarioId.isBlank() || s.usuarioId.length > MAX_ID) return null
        return s.copy(
            nombre = s.nombre.take(MAX_NOMBRE),
            descripcion = s.descripcion.take(MAX_DESCRIPCION),
            foto = s.foto?.takeIf { it.startsWith("http://") || it.startsWith("https://") }?.take(500),
            portada = s.portada?.takeIf { it.startsWith("http://") || it.startsWith("https://") }?.take(500),
            fotoHash = s.fotoHash?.take(64),
            portadaHash = s.portadaHash?.take(64),
            perfilVer = s.perfilVer,
            estados = s.estados.take(MAX_ESTADOS).map {
                it.copy(id = it.id.take(MAX_ID), texto = it.texto.take(MAX_TEXTO_ESTADO))
            },
            obras = s.obras.take(MAX_OBRAS).map {
                it.copy(
                    id = it.id.take(MAX_ID),
                    tipo = it.tipo.take(20),
                    titulo = it.titulo.take(MAX_TITULO),
                    contenido = it.contenido.take(MAX_CONTENIDO_OBRA)
                )
            }
        )
    }
}

// ── Conversiones ─────────────────────────────────────────────────────────────

/** Solo obras con texto: las que son únicamente un PDF no se pueden mostrar por Bluetooth. */
fun List<ObraLibreria>.aObrasSnapshot(): List<ObraPerfilSnapshot> =
    filter { it.contenido.isNotBlank() }
        .map { ObraPerfilSnapshot(id = it.id, tipo = it.tipo.name, titulo = it.titulo, contenido = it.contenido, ts = it.creadaEn) }

fun SnapshotPerfil.aObras(): List<ObraLibreria> =
    obras.map {
        ObraLibreria(
            id = it.id,
            autorId = usuarioId,
            autorNombre = nombre,
            tipo = runCatching { TipoObra.valueOf(it.tipo) }.getOrDefault(TipoObra.CUENTO),
            titulo = it.titulo,
            contenido = if (it.recortada) it.contenido + "\n\n… (texto recortado por Bluetooth; completo con internet)" else it.contenido,
            enlace = null,
            pdfRuta = null,
            pdfNombre = null,
            creadaEn = it.ts,
            compartidaMapaEn = if (actualizadoEn > 0L) actualizadoEn else it.ts
        )
    }.sortedByDescending { it.creadaEn }
