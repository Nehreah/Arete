package com.renea.arete_social.domain.util

import com.renea.arete_social.domain.model.OrigenVisibilidad
import com.renea.arete_social.domain.model.VisibilidadMapa
import kotlin.math.ceil
import kotlin.math.max

/** «2 h 10 min», «35 min», «menos de 1 min». Siempre redondea hacia arriba (nunca promete menos de lo que queda). */
fun duracionLegible(millis: Long): String {
    val minutosTotales = ceil(max(0L, millis) / 60_000.0).toLong()
    if (minutosTotales < 1L) return "menos de 1 min"
    val horas = minutosTotales / 60
    val minutos = minutosTotales % 60
    return when {
        horas == 0L -> "$minutos min"
        minutos == 0L -> "$horas h"
        else -> "$horas h $minutos min"
    }
}

/**
 * Texto corto para la lista de chats y el detalle de un lugar o evento: de dónde viene que lo veas
 * y, si aplica, cuánto falta para que se oculte.
 */
fun etiquetaVisibilidad(visibilidad: VisibilidadMapa, ahora: Long): String {
    val restante = visibilidad.venceEn?.let { duracionLegible(it - ahora) }
    return when (visibilidad.origen) {
        OrigenVisibilidad.CERCA -> "Estás cerca"
        OrigenVisibilidad.RECIENTE -> if (restante != null) "Visible $restante más" else "Visto hace poco"
        OrigenVisibilidad.PASE -> if (restante != null) "Por pase · $restante" else "Por pase"
        OrigenVisibilidad.SUPER -> if (restante != null) "Súper · $restante" else "Súper · tu zona regional"
    }
}
