package com.renea.arete_social.feature.mapa.mimis

import com.renea.arete_social.domain.model.Dispositivo

/** Máximo de Mimis que se dibujan a la vez sobre el mapa. El módulo Cercanos no tiene este límite. */
const val MAX_MIMIS_EN_MAPA = 14

/**
 * Decide qué personas cercanas se muestran como Mimi en el mapa.
 *
 * Es lógica pura (sin Android ni Compose) para poder probarla:
 *  - Los Mimis que ya estaban en pantalla se conservan mientras su persona siga cerca, así el
 *    mapa no parpadea ni cambia de gato cada vez que el detector emite una lista nueva.
 *  - Los cupos libres se llenan con las personas más cercanas primero.
 *  - Cuando alguien se aleja, su Mimi sale y el cupo pasa a la siguiente persona.
 */
class SelectorMimisVisibles(private val maximo: Int = MAX_MIMIS_EN_MAPA) {

    fun seleccionar(
        personasCercanas: List<Dispositivo>,
        idsVisiblesActuales: List<String> = emptyList(),
        usuarioIdPropio: String? = null
    ): List<Dispositivo> {
        val candidatas = personasCercanas
            .filter { usuarioIdPropio == null || it.usuarioId != usuarioIdPropio }
            .distinctBy { it.id }
        val porId = candidatas.associateBy { it.id }

        val conservadas = idsVisiblesActuales.mapNotNull { porId[it] }.take(maximo)
        if (conservadas.size >= maximo) return conservadas

        val idsConservados = conservadas.map { it.id }.toSet()
        val nuevas = candidatas
            .filter { it.id !in idsConservados }
            .sortedWith(
                compareBy<Dispositivo>(
                    { it.distanciaMetros == null },
                    { it.distanciaMetros ?: 0.0 },
                    { it.id }
                )
            )
        return conservadas + nuevas.take(maximo - conservadas.size)
    }
}
