package com.renea.arete_social.feature.tamagotchi.viewmodel

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.renea.arete_social.domain.model.Dispositivo
import com.renea.arete_social.data.local.perfil.GestorInteresesUsuario
import com.renea.arete_social.domain.model.Ubicacion
import com.renea.arete_social.domain.proximity.DetectorProximidad
import com.renea.arete_social.domain.repository.RepositorioEventos
import com.renea.arete_social.feature.tamagotchi.data.PetRepository
import com.renea.arete_social.feature.tamagotchi.logic.Cuidado
import com.renea.arete_social.feature.tamagotchi.logic.milisHasta
import com.renea.arete_social.feature.tamagotchi.logic.modoSuperActivo
import com.renea.arete_social.feature.tamagotchi.logic.milisHastaSiguienteEtapa
import com.renea.arete_social.feature.tamagotchi.logic.necesidadUrgente
import com.renea.arete_social.feature.tamagotchi.model.FoodType
import com.renea.arete_social.feature.tamagotchi.model.PetBalance
import com.renea.arete_social.feature.tamagotchi.model.PetEntity
import com.renea.arete_social.feature.tamagotchi.model.PetMood
import com.renea.arete_social.feature.tamagotchi.model.PetStage
import com.renea.arete_social.feature.tamagotchi.model.PetType
import com.renea.arete_social.feature.tamagotchi.model.ResultadoCuidado
import com.renea.arete_social.feature.tamagotchi.notification.PetNotificationHelper
import com.renea.arete_social.gui.tamagotchi.PetActionType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Orquesta el estado y las acciones de la mascota.
 *
 * Responsabilidades (cada una en su propia función para poder entenderla sola):
 *  1. Mantener a la mascota al día con el tiempo real (ciclo de vida + reloj).
 *  2. Ejecutar los cuidados y explicar por qué algo no se pudo hacer.
 *  3. Detectar amigos cercanos (ganar chispas) y eventos que coinciden con tus gustos.
 *  4. Cambiar el nombre. (Las chispas y la medicina se envían desde el mapa, no desde aquí.)
 *
 * @param ownerId dueño de la mascota que se muestra.
 * @param viewerId quien está mirando ahora (si no es el dueño, la pantalla es solo de lectura).
 */
import com.renea.arete_social.domain.repository.RepositorioLibreria
import com.renea.arete_social.feature.tamagotchi.mensajes.GestorHistorialMichi
import com.renea.arete_social.feature.tamagotchi.mensajes.MensajeMichiLocal
import com.renea.arete_social.feature.tamagotchi.mensajes.TipoMensajeMichi

class PetViewModel(
    private val repository: PetRepository,
    private val ownerId: String,
    private val ownerNombre: String = "Usuario",
    private val viewerId: String,
    private val detectorProximidad: DetectorProximidad? = null,
    private val repositorioEventos: RepositorioEventos? = null,
    private val repositorioLibreria: RepositorioLibreria? = null,
    private val appContext: Context? = null
) : ViewModel(), DefaultLifecycleObserver {

    private val isOwner = ownerId == viewerId

    @Volatile
    private var isAppForeground = true

    val gestorHistorialMichi: GestorHistorialMichi? = if (appContext != null && isOwner) {
        GestorHistorialMichi(appContext, ownerId)
    } else null
    val historialMensajes: StateFlow<List<MensajeMichiLocal>> = gestorHistorialMichi?.mensajes
        ?: MutableStateFlow(emptyList())

    // ── Estado transitorio ───────────────────────────────────────────────────
    private val _activeAnimation = MutableStateFlow<PetActionType?>(null)
    private val _nearbyFriends = MutableStateFlow<List<Dispositivo>>(emptyList())
    private val _mensaje = MutableStateFlow<String?>(null)
    private val _radarReport = MutableStateFlow<String?>(null)

    private var mensajeJob: Job? = null
    private var lastLocation: Ubicacion? = null
    private var energiaAnterior: Int? = null
    /** Instante de activación del Súper visto en la emisión anterior (null hasta la primera: así no se avisa al abrir). */
    private var superDesdeAnterior: Long? = null
    private val ultimoEncuentroPorAmigo = HashMap<String, Long>()
    private val eventosNotificados = HashSet<String>()

    /** Reloj de 1 segundo: hace que las cuentas atrás de espera avancen en pantalla. */
    private val reloj: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    /** Si miras la mascota de otra persona y no hay datos locales, se muestra una de visita. */
    private val mascotaVisitante: PetEntity by lazy {
        val ahora = System.currentTimeMillis()
        PetEntity(
            ownerId = ownerId,
            name = PetBalance.DEFAULT_NAME,
            petType = PetType.GATO,
            stage = PetStage.ADULTO,
            hunger = 85, happiness = 90, energy = 85, hygiene = 90, health = 100,
            birthMillis = ahora - PetStage.ADULTO.minAgeMillis,
            lastTickMillis = ahora
        )
    }

    private data class Transitorio(
        val animacion: PetActionType?,
        val amigos: List<Dispositivo>,
        val mensaje: String?,
        val resumen: String?
    )

    val uiState: StateFlow<PetUiState> = combine(
        repository.observePet(ownerId).map { it ?: if (!isOwner) mascotaVisitante else null },
        combine(_activeAnimation, _nearbyFriends, _mensaje, _radarReport) { a, f, m, r ->
            Transitorio(a, f, m, r)
        },
        reloj
    ) { pet, t, ahora ->
        PetUiState(
            ownerId = ownerId,
            ownerNombre = ownerNombre,
            isOwner = isOwner,
            pet = pet,
            mood = pet?.let { PetMood.from(it) } ?: PetMood.NORMAL,
            alertMessage = t.mensaje ?: pet?.takeIf { isOwner }?.necesidadUrgente(),
            activeAnimation = t.animacion,
            nearbyFriends = t.amigos,
            radarReport = t.resumen,
            cooldownAlimentarSecs = pet.segundosHasta(Cuidado.ALIMENTAR, ahora),
            cooldownJugarSecs = pet.segundosHasta(Cuidado.JUGAR, ahora),
            cooldownLimpiarSecs = pet.segundosHasta(Cuidado.BANAR, ahora),
            segundosParaCrecer = pet?.milisHastaSiguienteEtapa(ahora)?.let { (it + 999L) / 1000L }
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PetUiState(ownerId = ownerId, ownerNombre = ownerNombre, isOwner = isOwner)
    )

    private fun PetEntity?.segundosHasta(cuidado: Cuidado, ahora: Long): Int =
        if (this == null) 0 else ((milisHasta(cuidado, ahora) + 999L) / 1000L).toInt()

    init {
        if (isOwner) {
            runCatching { ProcessLifecycleOwner.get().lifecycle.addObserver(this) }
            iniciarCicloDeVida()
            iniciarRadarDeAmigos()
            iniciarSensorDeEventos()
            vigilarEnergiaLlena()
            vigilarModoSuper()
            vigilarLecturasObras()
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 1. Ciclo de vida y reloj
    // ═════════════════════════════════════════════════════════════════════════

    private fun iniciarCicloDeVida() {
        viewModelScope.launch {
            repository.ensurePetExists(ownerId = ownerId, name = NOMBRE_POR_DEFECTO, type = PetType.GATO)
            while (isActive) {
                if (isAppForeground) repository.tick(ownerId)
                delay(PetBalance.FOREGROUND_TICK_INTERVAL_MILLIS.milliseconds)
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        isAppForeground = true
        viewModelScope.launch { repository.tick(ownerId) }
    }

    override fun onStop(owner: LifecycleOwner) {
        isAppForeground = false
    }

    override fun onCleared() {
        if (isOwner) {
            runCatching { ProcessLifecycleOwner.get().lifecycle.removeObserver(this) }
        }
    }

    /** Fuerza una sincronización del estado de la mascota (llamado desde pull-to-refresh). */
    fun recargar() {
        viewModelScope.launch { repository.tick(ownerId) }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 2. Cuidados
    // ═════════════════════════════════════════════════════════════════════════

    fun feed(food: FoodType) = ejecutar(PetActionType.FEED) { repository.feed(ownerId, food) }
    fun play() = ejecutar(PetActionType.PLAY) { repository.play(ownerId) }
    fun clean() = ejecutar(PetActionType.CLEAN) { repository.clean(ownerId) }
    fun heal() = ejecutar(PetActionType.HEAL) { repository.heal(ownerId) }

    /**
     * Dormir / despertar. Si duerme lo suficiente y despierta, gana XP doble (modo turbo):
     * en ese caso se muestra la animación de turbo en vez de la de descanso.
     */
    fun toggleSleep() = runIfOwner {
        val antes = repository.observePet(ownerId).first()
        val resultado = repository.toggleSleep(ownerId)
        val nombre = antes?.name ?: NOMBRE_POR_DEFECTO
        if (!resultado.exito) {
            mostrarMensaje(resultado.texto(nombre))
            return@runIfOwner
        }
        val despues = repository.observePet(ownerId).first()
        val horas = PetBalance.SLEEP_MILLIS_FOR_TURBO / 3_600_000L
        val estabaDormida = antes?.isSleeping == true
        val ganoTurbo = estabaDormida && antes?.modoTurbo == false && despues?.modoTurbo == true
        _activeAnimation.value = if (ganoTurbo) PetActionType.TRAIN else PetActionType.SLEEP
        when {
            ganoTurbo -> mostrarMensaje("$nombre descansó bien: ¡mejor ánimo y XP doble por un rato!")
            estabaDormida -> mostrarMensaje("$nombre despertó. Con $horas h de sueño o más despierta feliz y con XP doble.")
            else -> mostrarMensaje("$nombre duerme. Si la dejas toda la noche (7-8 h) recupera toda la energía y despierta con XP doble.", 7_000L)
        }
    }
    fun reboot() = ejecutar(PetActionType.REBOOT) { repository.reboot(ownerId) }

    /**
     * Un juego externo (Miau Snake) ya aplicó su recompensa a través de su puente: aquí solo se
     * celebra en pantalla. No toca el repositorio ni el estado de la mascota.
     */
    fun celebrarJuegoExterno(mensaje: String) = runIfOwner {
        _activeAnimation.value = PetActionType.PLAY
        mostrarMensaje(mensaje)
    }

    fun actualizarPet(pet: PetEntity) = runIfOwner {
        repository.actualizarPet(ownerId, pet)
    }

    /** Mimos al tocar a la mascota. Las pausas cortas se ignoran en silencio. */
    fun cuddle() = runIfOwner {
        val resultado = repository.cuddle(ownerId)
        if (resultado != ResultadoCuidado.OK && resultado != ResultadoCuidado.EN_ESPERA) {
            mostrarMensaje(resultado.texto(nombreMascota()))
        }
    }

    /** Cambia el nombre de la mascota. Devuelve true si se guardó (la pantalla cierra el diálogo). */
    fun renombrar(nuevoNombre: String, alTerminar: (Boolean) -> Unit = {}) = runIfOwner {
        val resultado = repository.rename(ownerId, nuevoNombre)
        if (resultado.exito) {
            mostrarMensaje("¡Ahora se llama ${nombreMascota()}!")
        } else {
            mostrarMensaje(resultado.texto(nombreMascota()))
        }
        alTerminar(resultado.exito)
    }

    fun clearAnimation() {
        _activeAnimation.value = null
    }

    fun dismissRadarReport() {
        _radarReport.value = null
    }

    private fun ejecutar(animacion: PetActionType, accion: suspend () -> ResultadoCuidado) = runIfOwner {
        val resultado = accion()
        if (resultado.exito) {
            _activeAnimation.value = animacion
            // Si había un aviso viejo (p. ej. "tiene hambre") se quita para que no confunda.
            mensajeJob?.cancel()
            _mensaje.value = null
        } else {
            mostrarMensaje(resultado.texto(nombreMascota()))
        }
    }

    private suspend fun nombreMascota(): String =
        repository.observePet(ownerId).first()?.name ?: NOMBRE_POR_DEFECTO

    /** Muestra un mensaje un rato. Un mensaje nuevo reemplaza al anterior sin que se pisen los temporizadores. */
    private fun mostrarMensaje(texto: String, duracionMs: Long = 5_000L) {
        mensajeJob?.cancel()
        _mensaje.value = texto
        mensajeJob = viewModelScope.launch {
            delay(duracionMs)
            _mensaje.value = null
        }
    }

    private fun runIfOwner(bloque: suspend () -> Unit) {
        if (isOwner) viewModelScope.launch { bloque() }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 3. Amigos y eventos cercanos
    // ═════════════════════════════════════════════════════════════════════════

    private fun iniciarRadarDeAmigos() {
        val detector = detectorProximidad ?: return
        viewModelScope.launch {
            detector.ubicacionPropia().catch { }.collect { lastLocation = it }
        }
        viewModelScope.launch {
            detector.descubrir().catch { }.collect { lista ->
                _nearbyFriends.value = lista
                val ahora = System.currentTimeMillis()
                lista.forEach { amigo ->
                    val ultimo = ultimoEncuentroPorAmigo[amigo.id]
                    if (ultimo == null || ahora - ultimo >= PetBalance.FRIEND_SPARK_COOLDOWN_MILLIS) {
                        ultimoEncuentroPorAmigo[amigo.id] = ahora
                        saludarAmigo(amigo)
                    }
                }
            }
        }
    }

    /** Un amigo apareció cerca: la mascota lo avisa y la persona gana chispas (con tope diario). */
    private fun saludarAmigo(amigo: Dispositivo) {
        viewModelScope.launch {
            val mascota = nombreMascota()
            val amigoNombre = amigo.nombre.ifBlank { "un amigo" }
            val distancia = amigo.distanciaMetros?.toInt()
            val resultado = repository.earnSparkFromFriend(ownerId)
            val texto = PetNotificationHelper.textoAmigoCercano(mascota, amigoNombre, distancia)
            val textoCompleto = if (resultado.exito) "$texto Ganaste chispas ✦" else texto
            mostrarMensaje(textoCompleto, 7_000L)
            gestorHistorialMichi?.agregarMensaje(mascota, textoCompleto, TipoMensajeMichi.AMIGO)
            if (!isAppForeground) {
                appContext?.let { PetNotificationHelper.notificarAmigoCercano(it, mascota, amigoNombre, distancia) }
            }
        }
    }

    private fun iniciarSensorDeEventos() {
        val repo = repositorioEventos ?: return
        viewModelScope.launch {
            while (isActive) {
                if (isAppForeground) {
                    val origen = lastLocation ?: UBICACION_POR_DEFECTO
                    val eventos = runCatching { repo.eventosCercanos(origen, RADIO_EVENTOS_METROS) }
                        .getOrDefault(emptyList())
                    val gustos = GestorInteresesUsuario.intereses.value
                    val afin = eventos.firstOrNull { ev ->
                        ev.id !in eventosNotificados && gustos.coincideCon("${ev.nombre} ${ev.descripcion}")
                    }
                    if (afin != null) {
                        eventosNotificados.add(afin.id)
                        val mascota = nombreMascota()
                        val textoEvento = "$mascota encontró un plan para ti: «${afin.nombre}». ¿Vamos?"
                        mostrarMensaje(textoEvento, 8_000L)
                        gestorHistorialMichi?.agregarMensaje(mascota, textoEvento, TipoMensajeMichi.EVENTO)
                        appContext?.let {
                            PetNotificationHelper.notificarEventoInteres(it, mascota, afin.nombre, afin.descripcion)
                        }
                    }
                }
                delay(35.seconds)
            }
        }
        // Si la persona cambia sus gustos, los eventos se vuelven a evaluar.
        viewModelScope.launch {
            GestorInteresesUsuario.intereses.collect { eventosNotificados.clear() }
        }
    }

    /** Cuando la energía CRUZA el 100 % (no cada vez que se abre la pantalla) se muestra un resumen. */
    /**
     * Cuando el Modo Súper se activa con la pantalla de la mascota a la vista, se celebra con un mensaje
     * y la animación de turbo. No avisa al abrir la pantalla con un Súper que ya estaba en marcha.
     */
    private fun vigilarModoSuper() {
        viewModelScope.launch {
            repository.observePet(ownerId).filterNotNull().collect { pet ->
                val antes = superDesdeAnterior
                superDesdeAnterior = pet.superDesdeMillis
                if (antes != null && pet.superDesdeMillis > antes && pet.modoSuperActivo()) {
                    val horas = PetBalance.SUPER_DURATION_MILLIS / 3_600_000L
                    _activeAnimation.value = PetActionType.TRAIN
                    val textoSuper = "¡${pet.name} despertó sus súper poderes! Durante $horas h ves los lugares y eventos de tu zona regional."
                    mostrarMensaje(textoSuper, 8_000L)
                    gestorHistorialMichi?.agregarMensaje(pet.name, textoSuper, TipoMensajeMichi.SUPER)
                }
            }
        }
    }

    private fun vigilarEnergiaLlena() {
        viewModelScope.launch {
            repository.observePet(ownerId).filterNotNull().collect { pet ->
                val antes = energiaAnterior
                energiaAnterior = pet.energy
                if (antes != null && antes < 100 && pet.energy >= 100) {
                    val reporte = construirResumen(pet.name)
                    _radarReport.value = reporte
                    gestorHistorialMichi?.agregarMensaje(pet.name, reporte, TipoMensajeMichi.ENERGIA)
                }
            }
        }
    }

    private fun vigilarLecturasObras() {
        val repo = repositorioLibreria ?: return
        viewModelScope.launch {
            repo.escucharLecturasObras(ownerId).collect { obraId ->
                val petActual = repository.observePet(ownerId).first()
                if (petActual != null && !petActual.isSleeping && petActual.energy > 50) {
                    val mascota = petActual.name
                    val textoLectura = "Alguien está leyendo tu obra en el mapa 📖"
                    mostrarMensaje("¡$mascota te avisa!: $textoLectura", 8_000L)
                    gestorHistorialMichi?.agregarMensaje(mascota, textoLectura, TipoMensajeMichi.LECTURA)
                    appContext?.let { PetNotificationHelper.notificarAlguienLeyendo(it, mascota) }
                }
            }
        }
    }

    private suspend fun construirResumen(mascota: String): String {
        val amigos = _nearbyFriends.value
        val origen = lastLocation ?: UBICACION_POR_DEFECTO
        val eventos = runCatching { repositorioEventos?.eventosCercanos(origen, RADIO_EVENTOS_METROS) ?: emptyList() }
            .getOrDefault(emptyList())

        return buildString {
            append("¡$mascota está a tope de energía! Esto hay cerca de ti:\n\n")
            if (amigos.isNotEmpty()) {
                val nombres = amigos.take(3).joinToString(", ") { it.nombre.ifBlank { "Un amigo" } }
                append("👥 Amigos: $nombres")
                if (amigos.size > 3) append(" y ${amigos.size - 3} más")
                append("\n")
            } else {
                append("👥 No hay amigos cerca por ahora.\n")
            }
            if (eventos.isNotEmpty()) {
                append("📍 Planes: ${eventos.take(2).joinToString(", ") { "«${it.nombre}»" }}\n")
            } else {
                append("📍 No hay planes registrados cerca hoy.\n")
            }
            append("\n¡Aprovechemos para salir a explorar!")
        }
    }

    private companion object {
        const val NOMBRE_POR_DEFECTO = PetBalance.DEFAULT_NAME
        const val RADIO_EVENTOS_METROS = 5_000.0
        // Sin GPS todavía se usa el centro de Cali, que es donde está la comunidad de Aréte.
        val UBICACION_POR_DEFECTO = Ubicacion(3.4516, -76.5320)
    }
}

class PetViewModelFactory(
    private val repository: PetRepository,
    private val ownerId: String,
    private val ownerNombre: String = "Usuario",
    private val viewerId: String,
    private val detectorProximidad: DetectorProximidad? = null,
    private val repositorioEventos: RepositorioEventos? = null,
    private val repositorioLibreria: RepositorioLibreria? = null,
    private val appContext: Context? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return PetViewModel(
            repository, ownerId, ownerNombre, viewerId,
            detectorProximidad, repositorioEventos, repositorioLibreria, appContext
        ) as T
    }
}

