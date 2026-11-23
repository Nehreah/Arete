package com.renea.arete_social.feature.mapa

import com.renea.arete_social.domain.model.ElementoVisible
import com.renea.arete_social.domain.model.EntidadSnapshot
import com.renea.arete_social.domain.model.Evento
import com.renea.arete_social.domain.model.Lugar
import com.renea.arete_social.domain.model.OrigenVisibilidad
import com.renea.arete_social.domain.model.TipoEntidadMapa
import com.renea.arete_social.domain.model.VisibilidadMapa
import com.renea.arete_social.domain.model.VisionRegional
import com.renea.arete_social.domain.model.aEvento
import com.renea.arete_social.domain.model.aLugar
import com.renea.arete_social.domain.model.claveEntidad

/**
 * Junta lo que se ve en el mapa de cuatro fuentes (lógica pura, probada con tests):
 *  1. Lo que está a 500 m ahora (consulta al servidor)            → CERCA
 *  2. Lo descubierto: recientes (24 h) y por pase                  → RECIENTE / PASE
 *  3. La visión regional del Modo Súper                            → SUPER
 *
 * Si algo llega por varias fuentes se muestra una sola vez, con el origen de mayor prioridad
 * (CERCA > RECIENTE > PASE > SUPER, el orden del enum) y, si hay objeto completo del servidor,
 * se prefiere ese sobre la copia guardada.
 */
object MezclaMapa {

    data class Resultado(
        val lugares: List<Lugar>,
        val eventos: List<Evento>,
        /** Clave «LUGAR:id» / «EVENTO:id» → de dónde viene y cuándo se oculta. */
        val visibilidad: Map<String, VisibilidadMapa>
    )

    fun mezclar(
        lugaresCerca: List<Lugar>,
        eventosCerca: List<Evento>,
        descubiertos: List<ElementoVisible>,
        vision: VisionRegional?,
        superVenceEn: Long? = null
    ): Resultado {
        val lugares = LinkedHashMap<String, Lugar>()
        val eventos = LinkedHashMap<String, Evento>()
        val visibilidad = LinkedHashMap<String, VisibilidadMapa>()

        fun anotar(tipo: TipoEntidadMapa, id: String, nueva: VisibilidadMapa) {
            val clave = claveEntidad(tipo, id)
            val actual = visibilidad[clave]
            if (actual == null || nueva.origen.ordinal < actual.origen.ordinal) visibilidad[clave] = nueva
        }

        // 1) Cerca: objetos completos del servidor.
        lugaresCerca.forEach { lugares[it.id] = it; anotar(TipoEntidadMapa.LUGAR, it.id, VisibilidadMapa(OrigenVisibilidad.CERCA)) }
        eventosCerca.forEach { eventos[it.id] = it; anotar(TipoEntidadMapa.EVENTO, it.id, VisibilidadMapa(OrigenVisibilidad.CERCA)) }

        // 2) Descubiertos: si ya vino completo del servidor se conserva; si no, se reconstruye de la copia.
        for (d in descubiertos) agregar(d.snapshot, VisibilidadMapa(d.origen, d.venceEn), lugares, eventos, ::anotar)

        // 3) Modo Súper.
        vision?.let { v ->
            (v.lugares + v.eventos).forEach {
                agregar(it, VisibilidadMapa(OrigenVisibilidad.SUPER, superVenceEn), lugares, eventos, ::anotar)
            }
        }

        return Resultado(lugares.values.toList(), eventos.values.toList(), visibilidad)
    }

    private fun agregar(
        snapshot: EntidadSnapshot,
        visibilidad: VisibilidadMapa,
        lugares: MutableMap<String, Lugar>,
        eventos: MutableMap<String, Evento>,
        anotar: (TipoEntidadMapa, String, VisibilidadMapa) -> Unit
    ) {
        when (snapshot.tipo) {
            TipoEntidadMapa.LUGAR -> if (snapshot.id !in lugares) lugares[snapshot.id] = snapshot.aLugar()
            TipoEntidadMapa.EVENTO -> if (snapshot.id !in eventos) eventos[snapshot.id] = snapshot.aEvento()
        }
        anotar(snapshot.tipo, snapshot.id, visibilidad)
    }
}
