package com.renea.arete_social.feature.encuentros

import com.renea.arete_social.domain.mesh.MessageRouter
import com.renea.arete_social.domain.mesh.PaqueteMesh
import com.renea.arete_social.domain.model.EncuentroRemoto
import com.renea.arete_social.domain.model.EstadoEncuentro
import com.renea.arete_social.domain.model.ModoRuta
import com.renea.arete_social.domain.model.SesionEncuentro
import com.renea.arete_social.domain.model.SolicitudEncuentro
import com.renea.arete_social.domain.model.Ubicacion
import com.renea.arete_social.domain.repository.RepositorioEncuentros
import com.renea.arete_social.domain.ruta.CalculadorRuta
import com.renea.arete_social.domain.util.distanciaMetros
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.util.Log
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val CHAT_ID_ENCUENTROS_MESH = "encuentros-mesh"

/**
 * Encuentros entre dos amistades en el mapa (§5B.5), con soporte en tiempo real
 * para Bluetooth Mesh / red mallada local y repositorios locales.
 *
 * Flujo completo:
 *  1. Yo pido un encuentro ([solicitarEncuentro]) -> sesión PENDIENTE; se envía la invitación
 *     por BLE Mesh y al servidor.
 *  2. La otra persona ve la invitación ([solicitudesRecibidas]) y la acepta o la rechaza ([responderSolicitud]).
 *  3. Al aceptar, el encuentro pasa a ACTIVO: ambas publican su posición periódicamente por BLE Mesh y
 *     servidor, viéndose en el mapa en tiempo real.
 *  4. Con las dos posiciones se calcula la ruta ([CalculadorRuta]) y se recalcula al moverse o cambiar de modo.
 *  5. Cualquiera de las dos puede terminar ([terminarEncuentro]); se notifica por BLE y servidor.
 */
object GestorEncuentros {

    private const val INTERVALO_SONDEO_MS = 5_000L
    private const val DESVIO_RECALCULO_M = 25.0
    private const val PAUSA_RECALCULO_MS = 15_000L
    private const val PAUSA_REINTENTO_RESPALDO_MS = 30_000L
    /** Tras crear/aceptar un encuentro, un sondeo que ya iba en curso puede no verlo todavía. */
    private const val GRACIA_SESION_NUEVA_MS = 8_000L

    private class Dependencias(
        val repositorio: RepositorioEncuentros,
        val calculador: CalculadorRuta,
        val miId: String,
        val nombreDe: suspend (String) -> String?,
        val router: MessageRouter? = null,
        val miNombreProveedor: (() -> String)? = null
    )

    private class UltimoCalculo(
        val origen: Ubicacion,
        val destino: Ubicacion,
        val modo: ModoRuta,
        val enMillis: Long,
        val porCalles: Boolean
    )

    private var dependencias: Dependencias? = null
    private var scope: CoroutineScope? = null
    private var sondeo: Job? = null
    private var observadorMeshJob: Job? = null
    private val mutex = Mutex()

    @Volatile private var miUbicacion: Ubicacion? = null
    @Volatile private var ultimoCalculo: UltimoCalculo? = null
    @Volatile private var fallosSeguidos = 0

    private val nombres = ConcurrentHashMap<String, String>()
    /** Invitaciones que estoy respondiendo ahora mismo: evita que el sondeo las vuelva a mostrar. */
    private val enRespuesta: MutableSet<String> = ConcurrentHashMap.newKeySet()
    /** Encuentros que acabo de terminar yo: evita que el sondeo los "resucite" antes de que el servidor responda. */
    private val cerrados: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val _sesionActiva = MutableStateFlow<SesionEncuentro?>(null)
    /** Mi encuentro en curso (esperando respuesta o activo); null si no tengo ninguno. */
    val sesionActiva: StateFlow<SesionEncuentro?> = _sesionActiva.asStateFlow()

    private val _solicitudesRecibidas = MutableStateFlow<List<SolicitudEncuentro>>(emptyList())
    /** Invitaciones de mis amistades que aún no respondí. */
    val solicitudesRecibidas: StateFlow<List<SolicitudEncuentro>> = _solicitudesRecibidas.asStateFlow()

    private val _aviso = MutableStateFlow<String?>(null)
    /** Mensaje corto para mostrar una vez (rechazaron, terminó, error…). Llamar a [consumirAviso] al mostrarlo. */
    val aviso: StateFlow<String?> = _aviso.asStateFlow()

    fun consumirAviso() {
        _aviso.value = null
    }

    // ─── Ciclo de vida ───────────────────────────────────────────────────────

    /**
     * Arranca el gestor para [miId]. Es seguro llamarlo varias veces (rotación, volver al mapa):
     * si ya está corriendo solo actualiza las dependencias. Si cambió la persona con sesión iniciada,
     * descarta el estado de la anterior.
     */
    @Synchronized
    fun iniciar(
        repositorio: RepositorioEncuentros,
        calculador: CalculadorRuta,
        miId: String,
        nombreDe: suspend (String) -> String?,
        router: MessageRouter? = null,
        miNombreProveedor: (() -> String)? = null
    ) {
        if (dependencias?.miId != miId) limpiarEstado()
        dependencias = Dependencias(repositorio, calculador, miId, nombreDe, router, miNombreProveedor)

        val alcance = scope?.takeIf { it.isActive }
            ?: CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope = it }

        // Escuchar paquetes de la malla BLE / WiFi Mesh
        if (router != null && observadorMeshJob?.isActive != true) {
            observadorMeshJob?.cancel()
            observadorMeshJob = alcance.launch {
                router.todosLosPaquetesEntrantes().collect { p ->
                    procesarPaqueteMesh(p)
                }
            }
        }

        if (sondeo?.isActive == true) return
        sondeo = alcance.launch {
            while (isActive) {
                mutex.withLock { sincronizar() }
                delay(INTERVALO_SONDEO_MS)
            }
        }
    }

    /** Deja de sondear y de publicar mi ubicación. El estado se conserva por si la pantalla se recrea. */
    @Synchronized
    fun detener() {
        sondeo?.cancel()
        sondeo = null
        observadorMeshJob?.cancel()
        observadorMeshJob = null
    }

    private fun limpiarEstado() {
        _sesionActiva.value = null
        _solicitudesRecibidas.value = emptyList()
        _aviso.value = null
        ultimoCalculo = null
        miUbicacion = null
        nombres.clear()
        enRespuesta.clear()
        cerrados.clear()
    }

    /** La pantalla del mapa la llama cada vez que cambia mi GPS; se publica en el siguiente sondeo y por BLE. */
    fun actualizarMiUbicacion(ubicacion: Ubicacion) {
        miUbicacion = ubicacion
        val sesion = _sesionActiva.value
        val d = dependencias
        if (sesion?.estado == EstadoEncuentro.ACTIVO && d != null) {
            transmitirUbicacionMesh(d, sesion.id, sesion.amigoId, ubicacion)
            scope?.launch { mutex.withLock { recalcularRuta(d) } }
        }
    }

    // ─── Acciones del usuario ────────────────────────────────────────────────

    /** Pide un encuentro a una amistad. La ubicación no se comparte hasta que acepte. */
    fun solicitarEncuentro(amigoId: String, amigoNombre: String) {
        val d = dependencias
        val alcance = scope
        if (d == null || alcance == null) {
            _aviso.value = "No se pudo pedir el encuentro: abre el mapa e inténtalo de nuevo."
            return
        }
        if (_sesionActiva.value != null) {
            _aviso.value = "Ya tienes un encuentro en curso. Termínalo antes de pedir otro."
            return
        }
        val encuentroId = UUID.randomUUID().toString()
        nombres[amigoId] = amigoNombre
        cerrados.remove(encuentroId)
        _sesionActiva.value = SesionEncuentro(
            id = encuentroId,
            amigoId = amigoId,
            amigoNombre = amigoNombre,
            estado = EstadoEncuentro.PENDIENTE,
            soySolicitante = true
        )

        // Enviar por Bluetooth Mesh / red mallada local
        d.router?.let { router ->
            alcance.launch {
                val miNombre = d.miNombreProveedor?.invoke() ?: "Tu amistad"
                val json = JSONObject().apply {
                    put("type", "ENCUENTRO_SOLICITUD")
                    put("encuentroId", encuentroId)
                    put("remitenteId", d.miId)
                    put("remitenteNombre", miNombre)
                    put("targetId", amigoId)
                    put("timestamp", System.currentTimeMillis())
                }.toString()
                val paquete = PaqueteMesh(
                    id = UUID.randomUUID().toString(),
                    chatId = CHAT_ID_ENCUENTROS_MESH,
                    remitenteId = d.miId,
                    destinatarioId = amigoId,
                    contenido = json
                )
                router.reenviar(paquete)
            }
        }

        // Sincronizar en repositorio si está disponible
        alcance.launch {
            capturar { d.repositorio.solicitar(amigoId) }
                .onSuccess { servidorId ->
                    _sesionActiva.update { sesion ->
                        if (sesion?.id == encuentroId) sesion.copy(id = servidorId) else sesion
                    }
                    mutex.withLock { sincronizar() }
                }
                .onFailure {
                    Log.w("Encuentros", "Sincronización omitida (operando offline/Bluetooth): ${it.message}")
                }
        }
    }

    /** Acepta o rechaza una invitación recibida. */
    fun responderSolicitud(solicitud: SolicitudEncuentro, acepta: Boolean) {
        val d = dependencias ?: return
        val alcance = scope ?: return
        if (acepta && _sesionActiva.value != null) {
            _aviso.value = "Termina tu encuentro actual antes de aceptar otro."
            return
        }
        enRespuesta += solicitud.id
        _solicitudesRecibidas.update { lista -> lista.filterNot { it.id == solicitud.id } }

        if (acepta) {
            nombres[solicitud.remitenteId] = solicitud.remitenteNombre
            _sesionActiva.value = SesionEncuentro(
                id = solicitud.id,
                amigoId = solicitud.remitenteId,
                amigoNombre = solicitud.remitenteNombre,
                estado = EstadoEncuentro.ACTIVO,
                soySolicitante = false
            )
            ultimoCalculo = null
        }

        // Notificar respuesta por Mesh / Bluetooth
        d.router?.let { router ->
            alcance.launch {
                val miNombre = d.miNombreProveedor?.invoke() ?: "Tu amistad"
                val json = JSONObject().apply {
                    put("type", "ENCUENTRO_RESPUESTA")
                    put("encuentroId", solicitud.id)
                    put("remitenteId", d.miId)
                    put("remitenteNombre", miNombre)
                    put("targetId", solicitud.remitenteId)
                    put("acepta", acepta)
                }.toString()
                val paquete = PaqueteMesh(
                    id = UUID.randomUUID().toString(),
                    chatId = CHAT_ID_ENCUENTROS_MESH,
                    remitenteId = d.miId,
                    destinatarioId = solicitud.remitenteId,
                    contenido = json
                )
                router.reenviar(paquete)

                if (acepta) {
                    miUbicacion?.let { ubi ->
                        transmitirUbicacionMesh(d, solicitud.id, solicitud.remitenteId, ubi)
                    }
                }
            }
        }

        // Registrar también en el repositorio
        alcance.launch {
            capturar { d.repositorio.responder(solicitud.id, acepta) }
                .onFailure {
                    Log.w("Encuentros", "Respuesta omitida (operando offline/Bluetooth): ${it.message}")
                }
            enRespuesta -= solicitud.id
            mutex.withLock { sincronizar() }
        }
    }

    /** Termina mi encuentro o cancela la solicitud que sigue sin respuesta. */
    fun terminarEncuentro() {
        val sesion = _sesionActiva.value ?: return
        val d = dependencias
        cerrados += sesion.id
        _sesionActiva.value = null
        ultimoCalculo = null

        // Notificar terminación por Mesh / Bluetooth
        d?.router?.let { router ->
            scope?.launch {
                val json = JSONObject().apply {
                    put("type", "ENCUENTRO_TERMINAR")
                    put("encuentroId", sesion.id)
                    put("remitenteId", d.miId)
                    put("targetId", sesion.amigoId)
                }.toString()
                val paquete = PaqueteMesh(
                    id = UUID.randomUUID().toString(),
                    chatId = CHAT_ID_ENCUENTROS_MESH,
                    remitenteId = d.miId,
                    destinatarioId = sesion.amigoId,
                    contenido = json
                )
                router.reenviar(paquete)
            }
        }

        val alcance = scope
        if (d == null || alcance == null) return
        alcance.launch {
            capturar { d.repositorio.terminar(sesion.id) }
                .onFailure {
                    Log.w("Encuentros", "Cierre omitido (posiblemente offline/Bluetooth): ${it.message}")
                }
        }
    }

    /** Alterna el modo de ruta (pie -> bici -> auto -> pie) y recalcula el trazado. */
    fun alternarModoRuta() {
        val siguiente = _sesionActiva.value?.modo?.let {
            when (it) {
                ModoRuta.WALKING -> ModoRuta.BICYCLING
                ModoRuta.BICYCLING -> ModoRuta.DRIVING
                ModoRuta.DRIVING -> ModoRuta.WALKING
            }
        } ?: return
        _sesionActiva.update { it?.copy(modo = siguiente) }
        val d = dependencias ?: return
        scope?.launch { mutex.withLock { recalcularRuta(d) } }
    }

    // ─── Comunicación Mesh / Bluetooth ───────────────────────────────────────

    private fun transmitirUbicacionMesh(d: Dependencias, encuentroId: String, amigoId: String, ubi: Ubicacion) {
        val router = d.router ?: return
        val alcance = scope ?: return
        alcance.launch {
            val json = JSONObject().apply {
                put("type", "ENCUENTRO_UBICACION")
                put("encuentroId", encuentroId)
                put("remitenteId", d.miId)
                put("targetId", amigoId)
                put("lat", ubi.latitud)
                put("lon", ubi.longitud)
            }.toString()
            val paquete = PaqueteMesh(
                id = UUID.randomUUID().toString(),
                chatId = CHAT_ID_ENCUENTROS_MESH,
                remitenteId = d.miId,
                destinatarioId = amigoId,
                contenido = json
            )
            router.reenviar(paquete)
        }
    }

    private suspend fun procesarPaqueteMesh(p: PaqueteMesh) {
        val d = dependencias ?: return
        if (p.remitenteId.mismoId(d.miId)) return
        if (p.destinatarioId != null && !p.destinatarioId.mismoId(d.miId)) return
        if (p.chatId != CHAT_ID_ENCUENTROS_MESH && !p.contenido.startsWith("{\"type\":\"ENCUENTRO_")) return

        runCatching {
            val json = JSONObject(p.contenido)
            when (json.optString("type")) {
                "ENCUENTRO_SOLICITUD" -> {
                    val encuentroId = json.optString("encuentroId")
                    val remitenteId = json.optString("remitenteId")
                    val remitenteNombre = json.optString("remitenteNombre", "Tu amistad")
                    val ts = json.optLong("timestamp", System.currentTimeMillis())
                    if (encuentroId !in cerrados && encuentroId !in enRespuesta) {
                        nombres[remitenteId] = remitenteNombre
                        _solicitudesRecibidas.update { lista ->
                            if (lista.none { it.id == encuentroId }) {
                                listOf(SolicitudEncuentro(encuentroId, remitenteId, remitenteNombre, ts)) + lista
                            } else lista
                        }
                    }
                }
                "ENCUENTRO_RESPUESTA" -> {
                    val encuentroId = json.optString("encuentroId")
                    val remitenteId = json.optString("remitenteId")
                    val remitenteNombre = json.optString("remitenteNombre", "Tu amistad")
                    val acepta = json.optBoolean("acepta", false)

                    val actual = _sesionActiva.value
                    if (actual != null && (actual.id == encuentroId || actual.amigoId.mismoId(remitenteId))) {
                        if (acepta) {
                            nombres[remitenteId] = remitenteNombre
                            _sesionActiva.value = actual.copy(
                                id = encuentroId,
                                amigoId = remitenteId,
                                amigoNombre = remitenteNombre,
                                estado = EstadoEncuentro.ACTIVO
                            )
                            _aviso.value = "$remitenteNombre aceptó. Ya se comparten las ubicaciones."
                            miUbicacion?.let { ubi ->
                                transmitirUbicacionMesh(d, encuentroId, remitenteId, ubi)
                            }
                        } else {
                            _sesionActiva.value = null
                            ultimoCalculo = null
                            _aviso.value = "$remitenteNombre rechazó el encuentro."
                        }
                    }
                }
                "ENCUENTRO_UBICACION" -> {
                    val encuentroId = json.optString("encuentroId")
                    val remitenteId = json.optString("remitenteId")
                    val lat = json.optDouble("lat")
                    val lon = json.optDouble("lon")
                    val actual = _sesionActiva.value
                    if (actual != null && actual.estado == EstadoEncuentro.ACTIVO &&
                        (actual.id == encuentroId || actual.amigoId.mismoId(remitenteId))
                    ) {
                        val ubi = Ubicacion(latitud = lat, longitud = lon, timestamp = System.currentTimeMillis())
                        _sesionActiva.update { it?.copy(amigoUbicacion = ubi) }
                        recalcularRuta(d)
                    }
                }
                "ENCUENTRO_TERMINAR" -> {
                    val encuentroId = json.optString("encuentroId")
                    val remitenteId = json.optString("remitenteId")
                    val actual = _sesionActiva.value
                    if (actual != null && (actual.id == encuentroId || actual.amigoId.mismoId(remitenteId))) {
                        cerrados += encuentroId
                        val nombre = actual.amigoNombre
                        _sesionActiva.value = null
                        ultimoCalculo = null
                        _aviso.value = "$nombre terminó el encuentro."
                    }
                }
            }
        }.onFailure { Log.w("GestorEncuentros", "Error al procesar paquete mesh de encuentro", it) }
    }

    // ─── Sondeo ──────────────────────────────────────────────────────────────

    private suspend fun sincronizar() {
        val d = dependencias ?: return
        val sesionActual = _sesionActiva.value

        // Si la sesión está activa, transmitir periódicamente la posición por BLE Mesh
        if (sesionActual?.estado == EstadoEncuentro.ACTIVO && miUbicacion != null) {
            transmitirUbicacionMesh(d, sesionActual.id, sesionActual.amigoId, miUbicacion!!)
        }

        // Intento de sondeo de encuentros si hay conexión disponible
        val consulta = capturar { d.repositorio.obtenerAbiertos() }
        val abiertos = consulta.getOrNull()
        if (abiertos == null) {
            // Sin red: recalculamos ruta local si ya tenemos la ubicación recibida por BLE
            if (sesionActual?.estado == EstadoEncuentro.ACTIVO && sesionActual.amigoUbicacion != null) {
                recalcularRuta(d)
            }
            return
        }
        fallosSeguidos = 0
        val ahora = System.currentTimeMillis()
        val (vigentes, vencidos) = abiertos.partition { it.expiraEnMillis > ahora }
        vencidos.forEach { capturar { d.repositorio.terminar(it.id) } }

        sincronizarInvitaciones(d, vigentes)
        sincronizarMiSesion(d, vigentes, vencidos, ahora)

        val sesion = _sesionActiva.value
        if (sesion?.estado == EstadoEncuentro.ACTIVO) {
            miUbicacion?.let { capturar { d.repositorio.publicarUbicacion(sesion.id, it) } }
            capturar { d.repositorio.ubicacionDe(sesion.id, sesion.amigoId) }.getOrNull()?.let { ubicacion ->
                _sesionActiva.update { if (it?.id == sesion.id) it.copy(amigoUbicacion = ubicacion) else it }
            }
            recalcularRuta(d)
        }
    }

    private suspend fun sincronizarInvitaciones(d: Dependencias, vigentes: List<EncuentroRemoto>) {
        val nuevas = vigentes
            .filter {
                it.estado == EstadoEncuentro.PENDIENTE &&
                    it.invitadoId.mismoId(d.miId) &&
                    it.id !in enRespuesta &&
                    it.id !in cerrados
            }
            .map {
                SolicitudEncuentro(
                    id = it.id,
                    remitenteId = it.solicitanteId,
                    remitenteNombre = nombreDe(d, it.solicitanteId),
                    timestamp = it.creadoEnMillis
                )
            }
        // Combinar con invitaciones locales recibidas por Bluetooth
        _solicitudesRecibidas.update { actuales ->
            val servidorIds = nuevas.map { it.id }.toSet()
            nuevas + actuales.filter { it.id !in servidorIds && it.id !in cerrados && it.id !in enRespuesta }
        }
    }

    private suspend fun sincronizarMiSesion(
        d: Dependencias,
        vigentes: List<EncuentroRemoto>,
        vencidos: List<EncuentroRemoto>,
        ahora: Long
    ) {
        val local = _sesionActiva.value
        val mias = vigentes.filter {
            it.id !in cerrados && (
                it.estado == EstadoEncuentro.ACTIVO ||
                    (it.estado == EstadoEncuentro.PENDIENTE && it.solicitanteId.mismoId(d.miId))
                )
        }
        val propia = mias.firstOrNull { it.id == local?.id }
            ?: mias.firstOrNull { it.estado == EstadoEncuentro.ACTIVO }
            ?: mias.firstOrNull()

        if (propia != null) {
            val otroId = if (propia.solicitanteId.mismoId(d.miId)) propia.invitadoId else propia.solicitanteId
            val nombre = nombreDe(d, otroId)
            val anterior = local?.takeIf { it.id == propia.id }
            if (anterior == null) ultimoCalculo = null
            _sesionActiva.update { actual ->
                (actual?.takeIf { it.id == propia.id } ?: SesionEncuentro(
                    id = propia.id,
                    amigoId = otroId,
                    amigoNombre = nombre,
                    soySolicitante = propia.solicitanteId.mismoId(d.miId)
                )).copy(estado = propia.estado, amigoNombre = nombre)
            }
            if (anterior?.estado == EstadoEncuentro.PENDIENTE && propia.estado == EstadoEncuentro.ACTIVO) {
                _aviso.value = "$nombre aceptó. Ya se comparten las ubicaciones."
            }
            return
        }

        if (local == null) return
        // Recién creada o aceptada: el sondeo en curso pudo consultar antes de que existiera.
        if (ahora - local.timestamp < GRACIA_SESION_NUEVA_MS) return

        val vencio = vencidos.any { it.id == local.id }
        val cierre = if (vencio) null else capturar { d.repositorio.obtener(local.id) }.getOrNull()
        if (vencio || cierre?.estado in listOf(EstadoEncuentro.EXPIRADO, EstadoEncuentro.RECHAZADO, EstadoEncuentro.TERMINADO)) {
            _sesionActiva.update { if (it?.id == local.id) null else it }
            ultimoCalculo = null
            _aviso.value = when {
                vencio && local.estado == EstadoEncuentro.PENDIENTE -> "${local.amigoNombre} no respondió a tiempo."
                vencio || cierre?.estado == EstadoEncuentro.EXPIRADO -> "El encuentro con ${local.amigoNombre} expiró."
                cierre?.estado == EstadoEncuentro.RECHAZADO -> "${local.amigoNombre} rechazó el encuentro."
                else -> "${local.amigoNombre} terminó el encuentro."
            }
        }
    }

    private suspend fun recalcularRuta(d: Dependencias) {
        val sesion = _sesionActiva.value ?: return
        if (sesion.estado != EstadoEncuentro.ACTIVO) return
        val yo = miUbicacion ?: return
        val el = sesion.amigoUbicacion ?: return

        val previo = ultimoCalculo
        val ahora = System.currentTimeMillis()
        val transcurrido = if (previo == null) Long.MAX_VALUE else ahora - previo.enMillis
        val hayQueCalcular = previo == null ||
            previo.modo != sesion.modo ||
            (transcurrido >= PAUSA_RECALCULO_MS &&
                (distanciaMetros(previo.origen, yo) > DESVIO_RECALCULO_M ||
                    distanciaMetros(previo.destino, el) > DESVIO_RECALCULO_M)) ||
            (!previo.porCalles && transcurrido >= PAUSA_REINTENTO_RESPALDO_MS)
        if (!hayQueCalcular) return

        val ruta = capturar { d.calculador.calcular(yo, el, sesion.modo) }.getOrNull() ?: return
        ultimoCalculo = UltimoCalculo(yo, el, sesion.modo, ahora, ruta.porCalles)
        // Si mientras tanto cambió la sesión o el modo, este resultado ya no sirve.
        _sesionActiva.update { if (it?.id == sesion.id && it.modo == sesion.modo) it.copy(ruta = ruta) else it }
    }

    // ─── Utilidades ──────────────────────────────────────────────────────────

    private suspend fun nombreDe(d: Dependencias, id: String): String =
        nombres[id]
            ?: capturar { d.nombreDe(id) }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.also { nombres[id] = it }
            ?: "Tu amistad"

    /** Los UUID pueden llegar en mayúsculas o minúsculas o con/sin guiones: se comparan tolerando ambos formatos. */
    private fun String.mismoId(otro: String): Boolean {
        if (equals(otro, ignoreCase = true)) return true
        val u1 = replace("-", "").trim()
        val u2 = otro.replace("-", "").trim()
        return u1.isNotEmpty() && u1.equals(u2, ignoreCase = true)
    }

    /** Como runCatching, pero sin tragarse la cancelación de la corrutina. */
    private suspend inline fun <T> capturar(bloque: () -> T): Result<T> =
        try {
            Result.success(bloque())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    private fun mensajeDeError(error: Throwable): String {
        val texto = error.message.orEmpty()
        return when {
            texto.contains("ya te invitó", ignoreCase = true) ->
                "Esa persona ya te invitó: responde su invitación."
            texto.contains("amistades", ignoreCase = true) ->
                "Solo puedes encontrarte con tus amistades."
            texto.contains("schema cache", ignoreCase = true) || texto.contains("Could not find the function", ignoreCase = true) ->
                "Error en la base de datos de encuentros."
            else -> "No se pudo completar la acción. Revisa tu conexión e inténtalo de nuevo."
        }
    }

    /** Distancia geodésica (Haversine) en metros entre dos ubicaciones. */
    fun calcularDistanciaMetros(origen: Ubicacion, destino: Ubicacion): Double =
        distanciaMetros(origen, destino)

    /** Tiempo estimado en minutos dada la distancia y el modo de transporte. */
    fun calcularTiempoEstimadoMinutos(distanciaMetros: Double, modo: ModoRuta): Int {
        if (distanciaMetros <= 0.0) return 0
        val segundos = distanciaMetros / modo.velocidadMs
        return (segundos / 60.0).toInt().coerceAtLeast(1)
    }

    /** Puntos en línea recta entre origen y destino, para trazar algo cuando aún no hay ruta por calles. */
    fun interpolarPuntos(origen: Ubicacion, destino: Ubicacion, pasos: Int = 5): List<Ubicacion> {
        val puntos = mutableListOf(origen)
        for (i in 1 until pasos) {
            val fraccion = i.toDouble() / pasos
            puntos += Ubicacion(
                latitud = origen.latitud + (destino.latitud - origen.latitud) * fraccion,
                longitud = origen.longitud + (destino.longitud - origen.longitud) * fraccion,
                timestamp = System.currentTimeMillis()
            )
        }
        puntos += destino
        return puntos
    }
}
