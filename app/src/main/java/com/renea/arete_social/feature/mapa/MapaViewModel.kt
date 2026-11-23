package com.renea.arete_social.feature.mapa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renea.arete_social.domain.model.Evento
import com.renea.arete_social.domain.model.Lugar
import com.renea.arete_social.domain.model.Ubicacion
import com.renea.arete_social.domain.model.Visibilidad
import com.renea.arete_social.domain.proximity.DetectorProximidad
import com.renea.arete_social.domain.repository.RepositorioEventos
import com.renea.arete_social.domain.repository.RepositorioLugares
import com.renea.arete_social.domain.repository.RepositorioUsuarios
import com.renea.arete_social.feature.auth.GestorSesionLocal
import com.renea.arete_social.feature.descubrimiento.ServicioDescubrimiento
import com.renea.arete_social.feature.descubrimiento.ServicioPases
import com.renea.arete_social.feature.descubrimiento.ServicioRegionActual
import com.renea.arete_social.feature.descubrimiento.ServicioVisionRegional
import com.renea.arete_social.feature.tamagotchi.superpoderes.ServicioModoSuper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import android.content.Context
import com.renea.arete_social.feature.tamagotchi.data.PetRepository
import com.renea.arete_social.feature.tamagotchi.mensajes.TipoMensajeMichi
import com.renea.arete_social.feature.tamagotchi.notification.PetNotificationHelper
import com.renea.arete_social.feature.tamagotchi.mensajes.GestorHistorialMichi
import kotlinx.coroutines.flow.first

/**
 * Orquesta el mapa usando DetectorProximidad, RepositorioLugares, RepositorioEventos
 * y RepositorioUsuarios (domain/) — no conoce GPS ni detalles de red directamente.
 * Las implementaciones reales se inyectan desde donde se construya este ViewModel.
 *
 * Qué se ve en el mapa (ver MezclaMapa): lo que hay a [radioMetros], lo descubierto en las últimas
 * 24 h ([servicioDescubrimiento]) y, mientras el Michi esté en Modo Súper, los lugares y eventos
 * públicos de la zona regional ([servicioVisionRegional]). Los servicios son opcionales: sin ellos
 * el mapa se comporta como antes (solo lo cercano).
 */
class MapaViewModel(
    private val detectorProximidad: DetectorProximidad,
    private val repositorioLugares: RepositorioLugares,
    private val repositorioEventos: RepositorioEventos,
    private val repositorioUsuarios: RepositorioUsuarios,
    private val usuarioId: String,
    private val radioMetros: Double = 500.0,
    private val servicioDescubrimiento: ServicioDescubrimiento? = null,
    private val servicioRegionActual: ServicioRegionActual? = null,
    private val servicioModoSuper: ServicioModoSuper? = null,
    private val servicioVisionRegional: ServicioVisionRegional? = null,
    private val servicioPases: ServicioPases? = null,
    private val repositorioLibreria: com.renea.arete_social.domain.repository.RepositorioLibreria? = null,
    private val petRepository: PetRepository? = null,
    private val context: Context? = null
) : ViewModel() {

    /** Avisos discretos (p. ej. «Alguien cercano te compartió 3 lugares por 24 h»). La pantalla los muestra como un aviso breve. */
    val avisos: kotlinx.coroutines.flow.Flow<String> = servicioPases?.avisos ?: kotlinx.coroutines.flow.emptyFlow()

    private val _estado = MutableStateFlow<MapaUiState>(MapaUiState.Cargando)
    val estado: StateFlow<MapaUiState> = _estado.asStateFlow()

    private val trabajos = mutableListOf<Job>()

    // Lo último que devolvió el servidor para «a 500 m de mí». Se mezcla con lo descubierto y con el Súper.
    private var lugaresCerca: List<Lugar> = emptyList()
    private var eventosCerca: List<Evento> = emptyList()
    private var obrasCercanas: List<com.renea.arete_social.domain.model.ObraLibreria> = emptyList()
    private var hayUbicacion = false

    /** Llamar solo después de que la UI haya confirmado el permiso de ubicación. */
    fun iniciar() {
        if (trabajos.isNotEmpty()) return

        var ultimaLoc: com.renea.arete_social.domain.model.Ubicacion? = null

        iniciarServiciosDeDescubrimiento()
        refrescarObrasCercanas(emptyList())

        trabajos += viewModelScope.launch {
            detectorProximidad.ubicacionPropia()
                .catch { error ->
                    _estado.value = MapaUiState.Error(error.message ?: "No se pudo obtener la ubicación")
                }
                .collect { ubicacion ->
                    ultimaLoc = ubicacion
                    hayUbicacion = true
                    servicioRegionActual?.actualizar(ubicacion)
                    actualizar { it.copy(ubicacionPropia = ubicacion) }
                    // Sin esto, nadie ve a nadie: cada dispositivo sabe su propia
                    // posición (por GPS) pero nunca la comparte, así que
                    // usuariosCercanos() de cualquier otro dispositivo siempre la
                    // encuentra vacía. Se publica en cada actualización de posición.
                    // H-05: Respetar la visibilidad elegida por el usuario
                    prepararUbicacionParaSubir(ubicacion)?.let { paraSubir ->
                        runCatching { repositorioUsuarios.actualizarUbicacion(usuarioId, paraSubir) }
                    }
                    // Mismo patrón para lugares y eventos: cada vez que cambia la
                    // posición propia, se vuelve a preguntar qué hay cerca.
                    runCatching { repositorioLugares.lugaresCercanos(ubicacion, radioMetros) }
                        .onSuccess { lugares -> lugaresCerca = lugares }
                    runCatching { repositorioEventos.eventosCercanos(ubicacion, radioMetros) }
                        .onSuccess { eventos -> eventosCerca = eventos }
                    // Lo que está cerca se anota como «visto ahora»: empieza (o se reinicia) su plazo de 24 h.
                    servicioDescubrimiento?.let { runCatching { it.registrarCercanos(ubicacion, lugaresCerca, eventosCerca) } }
                    recomponer()
                }
        }

        // Heartbeat de presencia cada 30 segundos:
        // Si el usuario no se mueve físicamente, el GPS deja de emitir. Con este refresco,
        // la ubicación se mantiene vigente en el radar local de los demás.
        trabajos += viewModelScope.launch {
            while (isActive) {
                delay(30_000L)
                ultimaLoc?.let { loc ->
                    val ahora = System.currentTimeMillis()
                    // B-09: No refrescar timestamp si la fijación GPS tiene más de 2 minutos
                    if (ahora - loc.timestamp <= 120_000L) {
                        val refrescada = loc.copy(timestamp = ahora)
                        prepararUbicacionParaSubir(refrescada)?.let { paraSubir ->
                            runCatching { repositorioUsuarios.actualizarUbicacion(usuarioId, paraSubir) }
                        }
                    }
                }
            }
        }

        trabajos += viewModelScope.launch {
            detectorProximidad.descubrir()
                .catch { /* si falla, se conserva la última lista de personas cercanas conocida */ }
                .collect { personas ->
                    actualizar { it.copy(personasCercanas = personas) }
                    refrescarObrasCercanas(personas)
                }
        }

        trabajos += viewModelScope.launch {
            repositorioEventos.eventosEliminados.collect { eventoId ->
                eliminarEventoLocal(eventoId)
            }
        }

        // Refresco reactivo inmediato: cuando un evento llega por BLE/mesh (o se crea/actualiza
        // localmente), el repositorio emite en eventosActualizados y aquí recargamos sin necesitar
        // un nuevo punto GPS. Así el mapa muestra el evento al instante en ambos teléfonos.
        trabajos += viewModelScope.launch {
            repositorioEventos.eventosActualizados.collect {
                refrescarCercanos()
            }
        }
    }

    /** Pone en marcha lo que hay detrás de «descubrir»: memoria de 24 h, Modo Súper y visión regional. */
    private fun iniciarServiciosDeDescubrimiento() {
        servicioDescubrimiento?.let { servicio ->
            servicio.vincularUsuario(usuarioId)
            trabajos += viewModelScope.launch { servicio.visibles.collect { recomponer() } }
            trabajos += viewModelScope.launch {
                runCatching { servicio.iniciar() }
                // Lo vencido debe desaparecer del mapa aunque la persona no se mueva.
                while (isActive) {
                    delay(REEVALUACION_VENCIMIENTOS_MS)
                    runCatching { servicio.refrescar() }
                    // Con internet, lo recibido por pase se comprueba contra el servidor (olvida lo que no existe).
                    servicioPases?.let { runCatching { it.verificarPendientes() } }
                }
            }
        }
        servicioModoSuper?.let { servicio ->
            servicio.iniciar(usuarioId)
            trabajos += viewModelScope.launch { servicio.estado.collect { recomponer() } }
        }
        servicioVisionRegional?.let { servicio ->
            servicio.iniciar()
            trabajos += viewModelScope.launch { servicio.vision.collect { recomponer() } }
        }
        servicioRegionActual?.let { servicio ->
            trabajos += viewModelScope.launch { servicio.estado.collect { recomponer() } }
        }
    }

    /** Recalcula lo visible. Hasta tener la primera ubicación no se toca el estado (sigue «Cargando»). */
    private fun recomponer() {
        if (!hayUbicacion) return
        val superEstado = servicioModoSuper?.estado?.value
        val mezcla = MezclaMapa.mezclar(
            lugaresCerca = lugaresCerca,
            eventosCerca = eventosCerca,
            descubiertos = servicioDescubrimiento?.visibles?.value.orEmpty(),
            vision = servicioVisionRegional?.vision?.value,
            superVenceEn = superEstado?.venceEn
        )
        actualizar {
            it.copy(
                lugaresCercanos = mezcla.lugares,
                eventosCercanos = mezcla.eventos,
                visibilidad = mezcla.visibilidad,
                modoSuperActivo = superEstado?.activo == true,
                regionActual = servicioRegionActual?.estado?.value?.region,
                obrasCercanasMapa = obrasCercanas
            )
        }
    }

    private val obrasNotificadasIds = mutableSetOf<String>()

    private fun refrescarObrasCercanas(personas: List<com.renea.arete_social.domain.model.Dispositivo>) {
        val repo = repositorioLibreria ?: return
        val ids = (personas.mapNotNull { it.usuarioId } + usuarioId).filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) {
            obrasCercanas = emptyList()
            actualizar { it.copy(obrasCercanasMapa = emptyList()) }
            return
        }
        viewModelScope.launch {
            val obras = runCatching { repo.listarObrasCercanasMapa(ids) }.getOrDefault(emptyList())
            obrasCercanas = obras
            actualizar { it.copy(obrasCercanasMapa = obras) }
            verificarYNotificarNuevasObras(obras)
        }
    }

    fun refrescarObras() {
        val personas = (_estado.value as? MapaUiState.Listo)?.personasCercanas.orEmpty()
        refrescarObrasCercanas(personas)
    }

    /** Elimina un evento localmente de inmediato del mapa y de los descubrimientos. */
    fun eliminarEventoLocal(eventoId: String) {
        eventosCerca = eventosCerca.filterNot { it.id == eventoId }
        viewModelScope.launch {
            servicioDescubrimiento?.olvidarEvento(eventoId)
        }
        recomponer()
    }

    /** Reevalúa los lugares y eventos cercanos con la última ubicación conocida. */
    fun refrescarCercanos() {
        // Usar la ubicación actual si existe, o una ubicación «cero» de fallback para que
        // RepositorioEventosLocalFirst (que no filtra en SQL) devuelva de todos modos los eventos locales.
        val loc = (_estado.value as? MapaUiState.Listo)?.ubicacionPropia
            ?: Ubicacion(0.0, 0.0)
        val radio = if ((_estado.value as? MapaUiState.Listo)?.ubicacionPropia != null) radioMetros
                    else Double.MAX_VALUE
        viewModelScope.launch {
            runCatching { repositorioEventos.eventosCercanos(loc, radio) }
                .onSuccess { eventos -> eventosCerca = eventos }
            if ((_estado.value as? MapaUiState.Listo)?.ubicacionPropia != null) {
                runCatching { repositorioLugares.lugaresCercanos(loc, radioMetros) }
                    .onSuccess { lugares -> lugaresCerca = lugares }
                servicioDescubrimiento?.let { runCatching { it.registrarCercanos(loc, lugaresCerca, eventosCerca) } }
            }
            recomponer()
        }
    }

    private suspend fun verificarYNotificarNuevasObras(obras: List<com.renea.arete_social.domain.model.ObraLibreria>) {
        val petRepo = petRepository ?: return
        val ctx = context ?: return
        val pet = petRepo.observePet(usuarioId).first() ?: return
        if (pet.isSleeping || pet.energy <= 50) return

        val gestorHistorial = GestorHistorialMichi(ctx, usuarioId)
        val nuevas = obras.filter { it.autorId != usuarioId && it.id !in obrasNotificadasIds }
        for (obra in nuevas) {
            obrasNotificadasIds.add(obra.id)
            val mascota = pet.name
            val por = if (!obra.autorNombre.isNullOrBlank()) " de ${obra.autorNombre}" else ""
            val texto = "Alguien compartió una obra$por: «${obra.titulo}» 📜"
            gestorHistorial.agregarMensaje(mascota, texto, TipoMensajeMichi.OBRA_COMPARTIDA)
            PetNotificationHelper.notificarAlguienCompartioObra(
                context = ctx,
                mascota = mascota,
                titulo = obra.titulo,
                autor = obra.autorNombre
            )
        }
    }

    private fun actualizar(transformar: (MapaUiState.Listo) -> MapaUiState.Listo) {
        val actual = _estado.value
        _estado.value = transformar(actual as? MapaUiState.Listo ?: MapaUiState.Listo())
    }

    fun detener() {
        trabajos.forEach { it.cancel() }
        trabajos.clear()
        detectorProximidad.detener()
    }

    override fun onCleared() {
        detener()
    }

    private fun prepararUbicacionParaSubir(ubicacion: Ubicacion): Ubicacion? {
        val vis = GestorSesionLocal.obtenerUltimaVisibilidad()
        return when (vis) {
            Visibilidad.INVISIBLE -> null
            Visibilidad.APROXIMADA -> ubicacion.copy(
                latitud = (ubicacion.latitud * 1000.0).let { kotlin.math.round(it) / 1000.0 },
                longitud = (ubicacion.longitud * 1000.0).let { kotlin.math.round(it) / 1000.0 }
            )
            else -> ubicacion
        }
    }

    private companion object {
        /** Cada cuánto se reevalúa qué descubrimientos ya vencieron. */
        const val REEVALUACION_VENCIMIENTOS_MS = 60_000L
    }
}