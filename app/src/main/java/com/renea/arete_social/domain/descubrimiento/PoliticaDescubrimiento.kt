package com.renea.arete_social.domain.descubrimiento

import com.renea.arete_social.domain.model.Descubrimiento
import com.renea.arete_social.domain.model.EntidadSnapshot
import com.renea.arete_social.domain.model.EstadoDescubrimiento
import com.renea.arete_social.domain.model.OrigenVisibilidad
import com.renea.arete_social.domain.model.TipoEntidadMapa
import com.renea.arete_social.domain.util.Reloj

/** Radio de descubrimiento: lo que está a esta distancia o menos (2 km) se ve en el radar y mapa. */
const val RADIO_DESCUBRIMIENTO_M = 2000.0

/** Cuánto sigue visible algo descubierto (24 h). */
const val DURACION_DESCUBRIMIENTO_MS = 24 * 60 * 60 * 1000L

/** Un evento ya terminado deja de recordarse (como reciente o por pase) pasado este margen. */
const val GRACIA_EVENTO_TERMINADO_MS = 24 * 60 * 60 * 1000L

/**
 * Las reglas del descubrimiento de lugares y eventos. Kotlin puro, sin Android, para probarlas
 * con tests. Usa un [Reloj] en vez de leer la hora directamente.
 *
 * Resumen de reglas:
 *  - CERCA: estás a [radioMetros] o menos. Visible y se puede compartir.
 *  - RECIENTE: estuviste cerca hace menos de [duracionMillis] (contado desde la ÚLTIMA vez
 *    que estuviste cerca; no hace falta detectar cuándo te fuiste). Visible y se puede compartir.
 *  - PASE: te lo compartieron hace menos de [duracionMillis]. Visible, NO se puede compartir.
 *  - Prioridad: CERCA > RECIENTE > PASE.
 *  - Un pase no se renueva: solo se acepta cuando para ti no es visible por ningún origen.
 *  - Un evento terminado hace más de [graciaEventoMillis] no se recuerda como reciente ni por
 *    pase. El criterio de «estar cerca» no cambia, para no alterar lo que el mapa ya hacía.
 */
class PoliticaDescubrimiento(
    private val reloj: Reloj = Reloj.Sistema,
    val radioMetros: Double = RADIO_DESCUBRIMIENTO_M,
    val duracionMillis: Long = DURACION_DESCUBRIMIENTO_MS,
    val graciaEventoMillis: Long = GRACIA_EVENTO_TERMINADO_MS
) {

    /**
     * Estado de [descubrimiento] para ti ahora. [distanciaMetros] es la distancia desde tu
     * posición actual (null si todavía no hay ubicación).
     */
    fun estadoDe(descubrimiento: Descubrimiento?, distanciaMetros: Double?): EstadoDescubrimiento {
        if (distanciaMetros != null && distanciaMetros <= radioMetros) return EstadoDescubrimiento.Cerca
        if (descubrimiento == null) return EstadoDescubrimiento.NoDescubierto

        val ahora = reloj.ahora()
        if (eventoTerminado(descubrimiento.snapshot, ahora)) return EstadoDescubrimiento.NoDescubierto

        descubrimiento.ultimoVistoCerca?.let { visto ->
            val vence = visto + duracionMillis
            if (ahora < vence) return EstadoDescubrimiento.Reciente(vence)
        }
        descubrimiento.paseRecibidoEn?.let { recibido ->
            val vence = recibido + duracionMillis
            if (ahora < vence) return EstadoDescubrimiento.PorPase(vence)
        }
        return EstadoDescubrimiento.NoDescubierto
    }

    /** Solo se comparte lo descubierto físicamente (cerca o recientemente), nunca lo que llegó por pase ni por el Súper. */
    fun puedeCompartir(estado: EstadoDescubrimiento): Boolean =
        estado is EstadoDescubrimiento.Cerca || estado is EstadoDescubrimiento.Reciente

    /** Un pase solo se acepta si ahora no ves esa cosa por ningún otro origen (no se renueva). */
    fun puedeAceptarPase(estado: EstadoDescubrimiento): Boolean =
        estado is EstadoDescubrimiento.NoDescubierto

    /** Qué se puede meter en un pase: lugares y eventos públicos. */
    fun esCompartible(snapshot: EntidadSnapshot): Boolean = snapshot.esPublico

    /** Anota que ahora mismo estás cerca. */
    fun marcarVistoCerca(descubrimiento: Descubrimiento, snapshot: EntidadSnapshot = descubrimiento.snapshot): Descubrimiento =
        descubrimiento.copy(snapshot = snapshot, ultimoVistoCerca = reloj.ahora())

    /** Registra un pase recibido (la validación de firma y de «no renovación» la hace quien llama). */
    fun registrarPase(existente: Descubrimiento?, snapshot: EntidadSnapshot, emisorId: String): Descubrimiento =
        (existente ?: Descubrimiento(snapshot)).copy(
            snapshot = snapshot,
            paseRecibidoEn = reloj.ahora(),
            paseEmisorId = emisorId
        )

    /** True si ya no hay nada que recordar de [descubrimiento] (se puede borrar). */
    fun estaVencido(descubrimiento: Descubrimiento): Boolean =
        estadoDe(descubrimiento, distanciaMetros = null) is EstadoDescubrimiento.NoDescubierto

    /** Traduce el estado al origen que se muestra en la interfaz (null si no se ve). */
    fun origenDe(estado: EstadoDescubrimiento): OrigenVisibilidad? = when (estado) {
        EstadoDescubrimiento.NoDescubierto -> null
        EstadoDescubrimiento.Cerca -> OrigenVisibilidad.CERCA
        is EstadoDescubrimiento.Reciente -> OrigenVisibilidad.RECIENTE
        is EstadoDescubrimiento.PorPase -> OrigenVisibilidad.PASE
    }

    /** Momento en que se oculta, o null si está cerca (no tiene vencimiento mientras sigas cerca). */
    fun venceEnDe(estado: EstadoDescubrimiento): Long? = when (estado) {
        is EstadoDescubrimiento.Reciente -> estado.venceEn
        is EstadoDescubrimiento.PorPase -> estado.venceEn
        else -> null
    }

    private fun eventoTerminado(snapshot: EntidadSnapshot, ahora: Long): Boolean =
        snapshot.tipo == TipoEntidadMapa.EVENTO &&
            snapshot.fechaEvento != null &&
            snapshot.fechaEvento + graciaEventoMillis < ahora
}
