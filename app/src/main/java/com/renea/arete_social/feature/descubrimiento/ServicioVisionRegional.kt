package com.renea.arete_social.feature.descubrimiento

import com.renea.arete_social.domain.model.Region
import com.renea.arete_social.domain.model.VisibilidadEvento
import com.renea.arete_social.domain.model.VisionRegional
import com.renea.arete_social.domain.model.aSnapshot
import com.renea.arete_social.domain.repository.RepositorioEventos
import com.renea.arete_social.domain.repository.RepositorioLugares
import com.renea.arete_social.domain.util.Reloj
import com.renea.arete_social.feature.tamagotchi.superpoderes.ServicioModoSuper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

/** Un evento ya terminado hace más de esto no se muestra en la visión regional. */
const val GRACIA_EVENTO_VISION_MS = 24 * 60 * 60 * 1000L

/**
 * Súper poder de Michi: mientras el Modo Súper está activo, trae los lugares y los eventos públicos
 * de la zona regional más cercana a la persona, aunque estén a decenas de kilómetros.
 *
 *  - Solo trabaja mientras [modoSuper] esté activo y haya región (si no, [vision] es null).
 *  - Se refresca al cambiar de región y cada [refrescoMillis] mientras dura el Súper.
 *  - Al terminar el Súper vacía el resultado de inmediato: lo visto solo por el Súper desaparece
 *    sin las 24 h de gracia del descubrimiento físico.
 *  - Necesita internet. Sin conexión conserva el último resultado hasta que venza el Súper.
 *  - Solo eventos PUBLICO; la zona sale de [ServicioRegionActual] (la misma región que se destaca en los chats).
 */
class ServicioVisionRegional(
    private val modoSuper: StateFlow<ServicioModoSuper.Estado>,
    private val region: ServicioRegionActual,
    private val repositorioLugares: RepositorioLugares,
    private val repositorioEventos: RepositorioEventos,
    private val scope: CoroutineScope,
    private val reloj: Reloj = Reloj.Sistema,
    private val refrescoMillis: Long = 5 * 60_000L,
    private val limite: Int = 300
) {
    private val _vision = MutableStateFlow<VisionRegional?>(null)
    val vision: StateFlow<VisionRegional?> = _vision.asStateFlow()

    private var trabajo: Job? = null

    @Synchronized
    fun iniciar() {
        if (trabajo?.isActive == true) return
        trabajo = scope.launch {
            launch { observar() }
            launch {
                repositorioEventos.eventosEliminados.collect { idEliminado ->
                    _vision.value = _vision.value?.let { v ->
                        v.copy(eventos = v.eventos.filterNot { it.id == idEliminado })
                    }
                }
            }
        }
    }

    @Synchronized
    fun detener() {
        trabajo?.cancel()
        trabajo = null
        _vision.value = null
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun observar() {
        val regionConSuper = combine(
            modoSuper.map { it.activo }.distinctUntilChanged(),
            region.estado.map { it.region }.distinctUntilChanged()
        ) { activo, actual -> if (activo) actual else null }
            .distinctUntilChanged()

        regionConSuper
            .transformLatest<Region?, VisionRegional?> { regionActual ->
                if (regionActual == null) {
                    emit(null)               // sin Súper o sin región: nada que ver
                } else {
                    if (_vision.value?.region?.id != regionActual.id) emit(null)  // cambió de región: se descarta lo anterior
                    while (true) {
                        cargar(regionActual)?.let { emit(it) }
                        delay(refrescoMillis)
                    }
                }
            }
            .collect { _vision.value = it }
    }

    /** Una sola carga de la zona de [region]. Null si no se pudo (se conserva lo anterior). Visible para tests. */
    internal suspend fun cargar(region: Region): VisionRegional? {
        val selector = this.region.selectorActual
        val zona = selector.zonaDe(region)
        val ahora = reloj.ahora()

        val lugares = runCatching { repositorioLugares.lugaresEnZona(zona, limite) }.getOrNull() ?: return null
        val eventos = runCatching {
            repositorioEventos.eventosPublicosEnZona(zona, desdeMillis = ahora - GRACIA_EVENTO_VISION_MS, limite = limite)
        }.getOrNull() ?: return null

        return VisionRegional(
            region = region,
            lugares = lugares
                .filter { selector.perteneceA(region, it.ubicacion) }
                .map { it.aSnapshot() },
            eventos = eventos
                .filter { it.visibilidad == VisibilidadEvento.PUBLICO }
                .filter { it.fecha + GRACIA_EVENTO_VISION_MS >= ahora }
                .filter { selector.perteneceA(region, it.ubicacion) }
                .map { it.aSnapshot() },
            cargadaEn = ahora
        )
    }
}
