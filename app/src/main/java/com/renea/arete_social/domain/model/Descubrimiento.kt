package com.renea.arete_social.domain.model

import kotlinx.serialization.Serializable

/** Qué clase de cosa del mapa se descubre. */
enum class TipoEntidadMapa { LUGAR, EVENTO }

/**
 * De dónde viene el hecho de que veas un lugar o evento.
 *  - [CERCA]: estás a 500 m o menos.
 *  - [RECIENTE]: estuviste cerca hace menos de 24 h.
 *  - [PASE]: alguien cercano te lo compartió por Bluetooth (24 h desde que lo recibes).
 *  - [SUPER]: tu Michi está en Modo Súper y está en tu zona regional. No se comparte.
 */
enum class OrigenVisibilidad { CERCA, RECIENTE, PASE, SUPER }

/**
 * Copia de lo esencial de un lugar o evento. Se guarda en el teléfono para poder seguir
 * dibujándolo aunque estés lejos o sin internet, y es lo que viaja dentro de un pase.
 */
@Serializable
data class EntidadSnapshot(
    val tipo: TipoEntidadMapa,
    val id: String,
    val nombre: String,
    val descripcion: String = "",
    val latitud: Double,
    val longitud: Double,
    val chatId: String = "",
    /** Solo eventos: fecha de inicio (milisegundos UTC). */
    val fechaEvento: Long? = null,
    /** Solo eventos. Un lugar es siempre público. */
    val visibilidad: VisibilidadEvento = VisibilidadEvento.PUBLICO
) {
    /** Clave única entre lugares y eventos (un lugar y un evento podrían compartir id). */
    val clave: String get() = "${tipo.name}:$id"

    val ubicacion: Ubicacion get() = Ubicacion(latitud, longitud, 0L)

    /** Solo lo público puede viajar en un pase. */
    val esPublico: Boolean get() = tipo == TipoEntidadMapa.LUGAR || visibilidad == VisibilidadEvento.PUBLICO
}

/** Lo que el teléfono recuerda de un lugar o evento que descubriste. */
data class Descubrimiento(
    val snapshot: EntidadSnapshot,
    /** Última vez que estuviste a 500 m o menos (null si nunca, p. ej. si solo llegó por pase). */
    val ultimoVistoCerca: Long? = null,
    /** Cuándo recibiste un pase que lo incluía (null si nunca). */
    val paseRecibidoEn: Long? = null,
    val paseEmisorId: String? = null
)

/** Estado de un lugar o evento para ti en este instante. */
sealed interface EstadoDescubrimiento {
    /** Para ti no existe: no se muestra. */
    data object NoDescubierto : EstadoDescubrimiento

    /** Estás a 500 m o menos. */
    data object Cerca : EstadoDescubrimiento

    /** Estuviste cerca; sigue visible hasta [venceEn]. */
    data class Reciente(val venceEn: Long) : EstadoDescubrimiento

    /** Te lo compartieron; sigue visible hasta [venceEn]. */
    data class PorPase(val venceEn: Long) : EstadoDescubrimiento
}

val EstadoDescubrimiento.esVisible: Boolean get() = this !is EstadoDescubrimiento.NoDescubierto

/** Un lugar o evento que ves ahora, con su origen y (si aplica) cuándo se oculta. */
data class ElementoVisible(
    val snapshot: EntidadSnapshot,
    val origen: OrigenVisibilidad,
    val venceEn: Long? = null
)

/** Cómo y hasta cuándo se ve algo en el mapa. Se usa para etiquetas y estilos. */
data class VisibilidadMapa(val origen: OrigenVisibilidad, val venceEn: Long? = null)

// ── Conversiones ─────────────────────────────────────────────────────────────

fun Lugar.aSnapshot(): EntidadSnapshot = EntidadSnapshot(
    tipo = TipoEntidadMapa.LUGAR,
    id = id,
    nombre = nombre,
    descripcion = descripcion,
    latitud = ubicacion.latitud,
    longitud = ubicacion.longitud,
    chatId = chatId
)

fun Evento.aSnapshot(): EntidadSnapshot = EntidadSnapshot(
    tipo = TipoEntidadMapa.EVENTO,
    id = id,
    nombre = nombre,
    descripcion = descripcion,
    latitud = ubicacion.latitud,
    longitud = ubicacion.longitud,
    chatId = chatId,
    fechaEvento = fecha,
    visibilidad = visibilidad
)

fun EntidadSnapshot.aLugar(): Lugar = Lugar(
    id = id,
    nombre = nombre,
    descripcion = descripcion,
    ubicacion = Ubicacion(latitud, longitud, 0L),
    chatId = chatId
)

/** Un evento reconstruido desde su copia. Los campos que la copia no guarda quedan vacíos. */
fun EntidadSnapshot.aEvento(): Evento = Evento(
    id = id,
    nombre = nombre,
    descripcion = descripcion,
    fecha = fechaEvento ?: 0L,
    ubicacion = Ubicacion(latitud, longitud, 0L),
    lugarId = null,
    creadorId = "",
    comunidadId = null,
    participantesIds = emptyList(),
    chatId = chatId,
    visibilidad = visibilidad
)

/** Clave usada en los mapas de visibilidad de la interfaz: «LUGAR:id» o «EVENTO:id». */
fun claveEntidad(tipo: TipoEntidadMapa, id: String): String = "${tipo.name}:$id"
