package com.renea.arete_social.feature.mensajeria

import com.renea.arete_social.domain.model.ElementoVisible
import com.renea.arete_social.domain.model.EntidadSnapshot
import com.renea.arete_social.domain.model.OrigenVisibilidad
import com.renea.arete_social.domain.model.Region
import com.renea.arete_social.domain.model.Sala
import com.renea.arete_social.domain.model.TipoEntidadMapa
import com.renea.arete_social.domain.model.TipoSala
import com.renea.arete_social.domain.model.VisibilidadMapa
import com.renea.arete_social.domain.model.VisionRegional

/**
 * Construye las salas «zonales» de la lista de chats (lógica pura, probada con tests):
 *  - Una sala regional por cada ciudad principal (todas siempre visibles; la más cercana va primero).
 *  - Una sala por cada lugar o evento que ves (cerca, recientes, por pase o por el Modo Súper),
 *    mientras sea visible. Al ocultarse, su sala desaparece de la lista; los mensajes ya guardados
 *    en el teléfono no se borran y reaparecen si vuelve a ser visible.
 *
 * Estas salas se derivan en el teléfono (igual que las de comunidad): no se guardan en la tabla `salas`.
 */
object SalasZonales {

    const val PREFIJO_REGIONAL = "Regional · "

    fun salaRegional(region: Region): Sala = Sala(
        id = "sala-region-${region.id}",
        nombre = "$PREFIJO_REGIONAL${region.nombre}",
        descripcion = "Chat de la región de ${region.nombre}",
        chatId = region.chatId,
        creadorId = "sistema",
        esPublica = true,
        clave = null,
        tipo = TipoSala.REGIONAL,
        entidadId = region.id,
        fechaCreacion = 0L
    )

    /** Todas las regiones, con la [destacada] (la más cercana) primero y el resto por nombre. */
    fun salasRegionales(regiones: List<Region>, destacada: Region?): List<Sala> {
        val ordenadas = regiones.sortedWith(
            compareBy<Region>({ if (it.id == destacada?.id) 0 else 1 }, { it.nombre.lowercase() })
        )
        return ordenadas.map { salaRegional(it) }
    }

    /** Sala de un lugar o evento; null si no tiene chat. */
    fun salaDe(snapshot: EntidadSnapshot): Sala? {
        if (snapshot.chatId.isBlank()) return null
        val esLugar = snapshot.tipo == TipoEntidadMapa.LUGAR
        return Sala(
            id = if (esLugar) "sala-lugar-${snapshot.id}" else "sala-evento-${snapshot.id}",
            nombre = snapshot.nombre,
            descripcion = snapshot.descripcion.ifBlank { if (esLugar) "Chat del lugar" else "Chat del evento" },
            chatId = snapshot.chatId,
            creadorId = "mapa",
            esPublica = true,
            clave = null,
            tipo = if (esLugar) TipoSala.LUGAR else TipoSala.EVENTO,
            entidadId = snapshot.id,
            fechaCreacion = 0L
        )
    }

    data class SalasDeZona(
        val salas: List<Sala>,
        /** chatId → de dónde viene y cuándo se oculta (para las etiquetas de la lista). */
        val visibilidadPorChat: Map<String, VisibilidadMapa>
    )

    /**
     * Salas de lo que ves ahora. Si una cosa llega por varios orígenes se queda con el de mayor
     * prioridad (CERCA > RECIENTE > PASE > SUPER). Orden: por prioridad y luego por nombre.
     */
    fun salasDeElementos(
        visibles: List<ElementoVisible>,
        vision: VisionRegional?,
        superVenceEn: Long? = null
    ): SalasDeZona {
        val porChat = LinkedHashMap<String, Pair<EntidadSnapshot, VisibilidadMapa>>()

        fun agregar(snapshot: EntidadSnapshot, visibilidad: VisibilidadMapa) {
            if (snapshot.chatId.isBlank()) return
            val actual = porChat[snapshot.chatId]
            if (actual == null || visibilidad.origen.ordinal < actual.second.origen.ordinal) {
                porChat[snapshot.chatId] = snapshot to visibilidad
            }
        }
        visibles.forEach { agregar(it.snapshot, VisibilidadMapa(it.origen, it.venceEn)) }
        vision?.let { v ->
            (v.lugares + v.eventos).forEach { agregar(it, VisibilidadMapa(OrigenVisibilidad.SUPER, superVenceEn)) }
        }

        val ordenadas = porChat.values.sortedWith(
            compareBy({ it.second.origen.ordinal }, { it.first.nombre.lowercase() })
        )
        val salas = ordenadas.mapNotNull { (snapshot, _) -> salaDe(snapshot) }
        return SalasDeZona(
            salas = salas,
            visibilidadPorChat = ordenadas.associate { (snapshot, vis) -> snapshot.chatId to vis }
        )
    }
}
