package com.renea.arete_social.domain.util

import com.renea.arete_social.domain.model.Region
import com.renea.arete_social.domain.model.Ubicacion
import com.renea.arete_social.domain.model.ZONA_REGIONAL_RADIO_MAX_M
import com.renea.arete_social.domain.model.ZonaRegional
import kotlin.math.max

/**
 * Reglas de «región más cercana» y de zona regional. Lógica pura, sin Android.
 *
 *  - Pertenencia: un punto pertenece a la región cuyo centro le queda más cerca (no hay
 *    huecos ni solapes) y solo si además está a [radioMaxMetros] o menos de ese centro.
 *  - Tu región: la más cercana a ti, con histéresis. Cambia solo cuando la otra queda
 *    CLARAMENTE más cerca (margen: [margenRelativo] de la distancia a la actual o
 *    [margenMinimoMetros], el que sea mayor). Así no «parpadea» al moverte por una zona
 *    intermedia entre dos ciudades.
 *
 * La región destacada en la lista de chats y la zona que abre el Modo Súper son la misma.
 */
class SelectorRegional(
    val regiones: List<Region>,
    private val radioMaxMetros: Double = ZONA_REGIONAL_RADIO_MAX_M,
    private val margenRelativo: Double = 0.10,
    private val margenMinimoMetros: Double = 2_000.0
) {

    /** Región con el centro más cercano a [punto], sin tope de distancia. Null si no hay regiones. */
    fun masCercana(punto: Ubicacion): Region? =
        regiones.minByOrNull { distanciaMetros(punto, it.ubicacion) }

    /**
     * Tu región con histéresis. [actual] es la que ya estaba destacada (o null la primera vez).
     */
    fun elegir(punto: Ubicacion, actual: Region?): Region? {
        val candidata = masCercana(punto) ?: return null
        val vigente = actual?.let { a -> regiones.firstOrNull { it.id == a.id } }
            ?: return candidata
        if (candidata.id == vigente.id) return vigente

        val dActual = distanciaMetros(punto, vigente.ubicacion)
        val dCandidata = distanciaMetros(punto, candidata.ubicacion)
        val margen = max(margenMinimoMetros, dActual * margenRelativo)
        return if (dCandidata < dActual - margen) candidata else vigente
    }

    /** Región a la que pertenece un lugar o evento ubicado en [punto] (null si queda fuera de toda zona). */
    fun regionDeContenido(punto: Ubicacion): Region? {
        val region = masCercana(punto) ?: return null
        return region.takeIf { distanciaMetros(punto, it.ubicacion) <= radioMaxMetros }
    }

    /** True si [punto] pertenece a [region] según las reglas de zona. */
    fun perteneceA(region: Region, punto: Ubicacion): Boolean =
        regionDeContenido(punto)?.id == region.id

    fun zonaDe(region: Region): ZonaRegional = ZonaRegional(region, radioMaxMetros)
}
