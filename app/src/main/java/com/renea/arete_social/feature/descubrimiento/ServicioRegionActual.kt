package com.renea.arete_social.feature.descubrimiento

import com.renea.arete_social.domain.model.Region
import com.renea.arete_social.domain.model.RegionesPredeterminadas
import com.renea.arete_social.domain.model.Ubicacion
import com.renea.arete_social.domain.util.SelectorRegional
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sabe cuál es tu región (la más cercana, con histéresis) y la comparte con quien la necesite:
 * la lista de chats destaca esa región y el Modo Súper abre su zona. Una sola fuente para que
 * nunca se contradigan.
 */
class ServicioRegionActual(regionesIniciales: List<Region> = RegionesPredeterminadas.todas) {

    data class Estado(
        val regiones: List<Region>,
        val ubicacion: Ubicacion? = null,
        val region: Region? = null
    )

    private var selector = SelectorRegional(regionesIniciales)
    private val _estado = MutableStateFlow(Estado(regionesIniciales))
    val estado: StateFlow<Estado> = _estado.asStateFlow()

    val selectorActual: SelectorRegional get() = selector

    /** Llamar con cada nueva posición propia. */
    @Synchronized
    fun actualizar(ubicacion: Ubicacion) {
        val actual = _estado.value
        val region = selector.elegir(ubicacion, actual.region)
        _estado.value = actual.copy(ubicacion = ubicacion, region = region)
    }

    /** Sustituye la lista de regiones (p. ej. al leer la tabla remota) y recalcula la tuya. */
    @Synchronized
    fun definirRegiones(regiones: List<Region>) {
        if (regiones.isEmpty()) return
        selector = SelectorRegional(regiones)
        val actual = _estado.value
        val region = actual.ubicacion?.let { selector.elegir(it, actual.region) }
        _estado.value = actual.copy(regiones = regiones, region = region)
    }
}
