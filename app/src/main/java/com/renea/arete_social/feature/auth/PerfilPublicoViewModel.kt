package com.renea.arete_social.feature.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renea.arete_social.domain.model.Carta
import com.renea.arete_social.domain.model.NuevaCarta
import com.renea.arete_social.domain.model.SolicitudAmistad
import com.renea.arete_social.domain.model.MotivoReporte
import com.renea.arete_social.domain.model.ObraLibreria
import com.renea.arete_social.domain.model.PublicacionPerfil
import com.renea.arete_social.domain.model.TipoReporte
import com.renea.arete_social.domain.model.aObras
import com.renea.arete_social.data.local.perfil.aPublicaciones
import com.renea.arete_social.data.local.perfil.CachePerfilLocal
import com.renea.arete_social.data.mesh.GestorPerfilBle
import com.renea.arete_social.data.mesh.ResultadoPerfilBle
import com.renea.arete_social.domain.model.conLikeDe
import com.renea.arete_social.domain.repository.AmistadesServidor
import com.renea.arete_social.domain.repository.RepositorioAmistades
import com.renea.arete_social.domain.repository.RepositorioCartas
import com.renea.arete_social.domain.repository.RepositorioLibreria
import com.renea.arete_social.domain.repository.RepositorioModeracion
import com.renea.arete_social.domain.repository.RepositorioPublicacionesPerfil
import com.renea.arete_social.domain.repository.RepositorioMensajes
import com.renea.arete_social.domain.repository.RepositorioUsuarios
import com.renea.arete_social.domain.util.validarTextoCarta
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject



/**
 * Muestra el perfil de OTRA persona (o el propio en modo público):
 * carga sus datos, gestiona la amistad (solicitar / aceptar / cancelar / eliminar),
 * permite iniciar un chat privado directo (1-a-1) y la correspondencia por cartas con esa persona.
 *
 * Amistades, estados, fotos y librería viven en el servidor. Si el perfil es privado y no somos
 * amistades, el servidor no entrega nada de eso y la pantalla muestra un candado.
 */
class PerfilPublicoViewModel(
    private val repositorioUsuarios: RepositorioUsuarios,
    private val repositorioMensajes: RepositorioMensajes,
    val usuarioId: String,
    val usuarioIdActual: String,
    private val gestorLlamadas: com.renea.arete_social.feature.llamadas.GestorLlamadasYAudio? = null,
    private val repositorioCartas: RepositorioCartas? = null,
    esInvitado: Boolean = false,
    private val repositorioAmistades: RepositorioAmistades? = null,
    private val repositorioPublicaciones: RepositorioPublicacionesPerfil? = null,
    private val repositorioLibreria: RepositorioLibreria? = null,
    // Perfil en texto por Bluetooth (sin internet; sin fotos ni PDF).
    private val gestorPerfilBle: GestorPerfilBle? = null,
    private val cachePerfil: CachePerfilLocal? = null,
    // Persistencia y caché local completa de perfiles (amigos y contactos) para visualización inmediata offline
    private val cacheUsuariosLocal: com.renea.arete_social.data.local.perfil.CacheUsuariosLocal? = null,
    private val repositorioModeracion: RepositorioModeracion? = null
) : ViewModel() {

    private val _estado = MutableStateFlow<PerfilPublicoUiState>(PerfilPublicoUiState.Cargando)
    val estado: StateFlow<PerfilPublicoUiState> = _estado.asStateFlow()

    private val sesionLocal = usuarioIdActual.startsWith("invitado-local-")
    private val amistadesEnServidor = repositorioAmistades != null && !sesionLocal
    private val publicacionesEnServidor = repositorioPublicaciones != null && !sesionLocal

    // Las cartas funcionan P2P entre amistades sobre la malla local o en servidor.
    private val cartasDisponibles = repositorioCartas != null && usuarioId != usuarioIdActual

    private val _cartas = MutableStateFlow(CartasPerfilUiState(disponible = cartasDisponibles))
    val cartas: StateFlow<CartasPerfilUiState> = _cartas.asStateFlow()

    private val _publicaciones = MutableStateFlow<List<PublicacionPerfil>>(emptyList())

    /** Estados y fotos de este perfil (ya filtradas por el servidor o recibidas vía Bluetooth/Mesh). */
    val publicaciones: StateFlow<List<PublicacionPerfil>> = _publicaciones.asStateFlow()

    private val _libreria = MutableStateFlow(LibreriaPerfilUiState())
    val libreria: StateFlow<LibreriaPerfilUiState> = _libreria.asStateFlow()

    private val _bluetooth = MutableStateFlow(BluetoothPerfilUiState())
    val bluetooth: StateFlow<BluetoothPerfilUiState> = _bluetooth.asStateFlow()

    // Bluetooth disponible mirando a otra persona cuando el gestor está activo
    private val bluetoothDisponible: Boolean
        get() = gestorPerfilBle != null && cachePerfil != null && usuarioId != usuarioIdActual

    init {
        cargarCartas()

        // Mostrar de inmediato el perfil en caché si ya se había cargado previamente
        val usuarioEnCache = cacheUsuariosLocal?.obtener(usuarioId)
        if (usuarioEnCache != null) {
            _estado.value = PerfilPublicoUiState.Listo(
                usuario = usuarioEnCache,
                esPropio = (usuarioId == usuarioIdActual),
                abriendoChat = false,
                esAmigo = GestorAmistades.esAmigo(usuarioId),
                solicitudEnviada = GestorAmistades.tieneSolicitudEnviada(usuarioId),
                solicitudRecibida = GestorAmistades.tieneSolicitud(usuarioId)
            )
        }

        if (bluetoothDisponible) {
            if (cachePerfil?.recibido(usuarioId) != null) {
                aplicarCopiaBluetooth()
            } else {
                buscarPorBluetooth()
            }
            viewModelScope.launch {
                cachePerfil?.observarRecibido(usuarioId)?.collect { snapshot ->
                    if (snapshot != null) {
                        aplicarCopiaBluetooth()
                    }
                }
            }
        }

        (repositorioCartas as? com.renea.arete_social.data.local.cartas.RepositorioCartasLocalFirst)?.let { localCartas ->
            viewModelScope.launch {
                localCartas.cartasActualizadas.collect {
                    cargarCartas()
                }
            }
        }

        if (usuarioId == usuarioIdActual) {
            viewModelScope.launch {
                com.renea.arete_social.feature.auth.GestorPerfilEfemero.publicaciones.collect { lista ->
                    _publicaciones.value = lista
                }
            }
        } else {
            viewModelScope.launch {
                com.renea.arete_social.feature.auth.GestorPerfilEfemero.publicaciones.collect { todas ->
                    val delUsuario = todas.filter { it.usuarioId == usuarioId && it.estaVigenteSemanal() }
                    if (delUsuario.isNotEmpty()) {
                        _publicaciones.update { actuales ->
                            (delUsuario + actuales).distinctBy { it.id }.sortedByDescending { it.timestamp }
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            val bloqueado = repositorioModeracion?.estaBloqueado(usuarioId) ?: false
            if (bloqueado) {
                _estado.update {
                    if (it is PerfilPublicoUiState.Listo) it.copy(estaBloqueado = true) else it
                }
            }

            // Al abrir un perfil se pone al día la lista de amistades para decidir bien qué mostrar.
            if (amistadesEnServidor) GestorAmistades.sincronizar(repositorioAmistades!!, usuarioIdActual)

            val usuario = runCatching { repositorioUsuarios.obtenerPorId(usuarioId) }.getOrNull()
            if (usuario != null) {
                cacheUsuariosLocal?.guardar(usuario)
                _estado.value = PerfilPublicoUiState.Listo(
                    usuario = usuario,
                    esPropio = (usuarioId == usuarioIdActual),
                    abriendoChat = false,
                    esAmigo = GestorAmistades.esAmigo(usuarioId),
                    solicitudEnviada = GestorAmistades.tieneSolicitudEnviada(usuarioId),
                    solicitudRecibida = GestorAmistades.tieneSolicitud(usuarioId)
                )
                cargarContenido()
            } else if (usuarioEnCache != null) {
                // Sin red: conservamos los datos cacheados y refrescamos estado de amistad
                _estado.value = PerfilPublicoUiState.Listo(
                    usuario = usuarioEnCache,
                    esPropio = (usuarioId == usuarioIdActual),
                    abriendoChat = false,
                    esAmigo = GestorAmistades.esAmigo(usuarioId),
                    solicitudEnviada = GestorAmistades.tieneSolicitudEnviada(usuarioId),
                    solicitudRecibida = GestorAmistades.tieneSolicitud(usuarioId)
                )
                aplicarCopiaBluetooth()
            } else {
                // Fallback para nodos descubiertos por radiofrecuencia local BLE / Mesh sin caché previa
                _estado.value = PerfilPublicoUiState.Listo(
                    usuario = com.renea.arete_social.domain.model.Usuario(
                        id = usuarioId,
                        nombre = "Cargando…",
                        foto = null,
                        descripcion = "Nodo detectado por radiofrecuencia local en la red Aréte.",
                        estadoVisibilidad = com.renea.arete_social.domain.model.Visibilidad.VISIBLE,
                        ubicacion = null,
                        clavePublicaE2ee = null
                    ),
                    esPropio = (usuarioId == usuarioIdActual),
                    abriendoChat = false,
                    esAmigo = GestorAmistades.esAmigo(usuarioId),
                    solicitudEnviada = GestorAmistades.tieneSolicitudEnviada(usuarioId),
                    solicitudRecibida = GestorAmistades.tieneSolicitud(usuarioId)
                )
                aplicarCopiaBluetooth()
            }
        }

        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                GestorAmistades.amigosIds,
                GestorAmistades.solicitudes,
                GestorAmistades.solicitudesEnviadasIds
            ) { amigos, solicitudes, enviadas ->
                Triple(amigos.contains(usuarioId), solicitudes.any { it.remitenteId == usuarioId }, enviadas.contains(usuarioId))
            }.collect { (esAmigo, tieneSol, enviada) ->
                val actual = _estado.value
                if (actual is PerfilPublicoUiState.Listo) {
                    val cambioAmistad = actual.esAmigo != esAmigo
                    _estado.value = actual.copy(
                        esAmigo = esAmigo,
                        solicitudRecibida = tieneSol,
                        solicitudEnviada = enviada
                    )
                    // Al volverse amistades (o dejar de serlo) cambia lo que el servidor deja ver.
                    if (cambioAmistad) cargarContenido()
                }
            }
        }
    }

    // ── Amistad (servidor primero, luego estado local y aviso en vivo) ───────

    private fun avisar(mensaje: String?) {
        _estado.update { if (it is PerfilPublicoUiState.Listo) it.copy(error = mensaje) else it }
    }

    fun consumirError() = avisar(null)

    fun consumirAvisoModeracion() {
        _estado.update { if (it is PerfilPublicoUiState.Listo) it.copy(avisoModeracion = null) else it }
    }

    fun bloquearUsuario() {
        val actual = _estado.value
        if (actual !is PerfilPublicoUiState.Listo || actual.esPropio) return
        viewModelScope.launch {
            val repo = repositorioModeracion ?: return@launch
            repo.bloquearUsuario(usuarioId)
                .onSuccess {
                    _estado.update {
                        if (it is PerfilPublicoUiState.Listo) it.copy(
                            estaBloqueado = true,
                            avisoModeracion = "Has bloqueado a ${actual.usuario.nombre}."
                        ) else it
                    }
                }
                .onFailure {
                    avisar(it.message ?: "No se pudo bloquear al usuario.")
                }
        }
    }

    fun desbloquearUsuario() {
        val actual = _estado.value
        if (actual !is PerfilPublicoUiState.Listo || actual.esPropio) return
        viewModelScope.launch {
            val repo = repositorioModeracion ?: return@launch
            repo.desbloquearUsuario(usuarioId)
                .onSuccess {
                    _estado.update {
                        if (it is PerfilPublicoUiState.Listo) it.copy(
                            estaBloqueado = false,
                            avisoModeracion = "Has desbloqueado a ${actual.usuario.nombre}."
                        ) else it
                    }
                }
                .onFailure {
                    avisar(it.message ?: "No se pudo desbloquear al usuario.")
                }
        }
    }

    fun enviarReporte(motivo: MotivoReporte, descripcion: String, bloquearTambien: Boolean) {
        val actual = _estado.value
        if (actual !is PerfilPublicoUiState.Listo || actual.esPropio) return
        viewModelScope.launch {
            val repo = repositorioModeracion ?: return@launch
            repo.enviarReporte(
                tipo = TipoReporte.USUARIO,
                motivo = motivo,
                reportadoId = usuarioId,
                referenciaId = null,
                descripcion = descripcion,
                bloquearTambien = bloquearTambien
            ).onSuccess {
                _estado.update {
                    if (it is PerfilPublicoUiState.Listo) it.copy(
                        estaBloqueado = if (bloquearTambien) true else it.estaBloqueado,
                        avisoModeracion = "Reporte enviado. Nuestro equipo lo revisará en menos de 24 horas."
                    ) else it
                }
            }.onFailure {
                avisar(it.message ?: "No se pudo enviar el reporte.")
            }
        }
    }

    private suspend fun datosPropiosParaSenal(): Pair<String, String> {
        val miPerfil = runCatching { repositorioUsuarios.obtenerPorId(usuarioIdActual) }.getOrNull()
        val nombreLocal = com.renea.arete_social.feature.auth.GestorSesionLocal.obtenerUltimoNombre()
        return (miPerfil?.nombre ?: nombreLocal ?: "Usuario") to (miPerfil?.foto ?: "")
    }

    fun enviarSolicitudAmistad() {
        val actual = _estado.value
        if (actual !is PerfilPublicoUiState.Listo || actual.esPropio) return

        viewModelScope.launch {
            var quedaronAmigos = false
            if (amistadesEnServidor) {
                runCatching {
                    quedaronAmigos = repositorioAmistades!!.solicitar(usuarioIdActual, usuarioId)
                }.onFailure {
                    android.util.Log.w("PerfilPublico", "No se pudo sincronizar solicitud con servidor (modo offline/Bluetooth): ${it.message}")
                }
            }
            avisar(null)
            if (quedaronAmigos) {
                GestorAmistades.agregarAmigo(usuarioId)
            } else {
                GestorAmistades.marcarSolicitudEnviada(usuarioId)
            }
            // Aviso en vivo a la otra persona por BLE Mesh / WiFi / Servidor
            runCatching {
                val (miNombre, miFoto) = datosPropiosParaSenal()
                if (quedaronAmigos) {
                    gestorLlamadas?.responderSolicitudAmistad(usuarioId, true, miNombre, miFoto)
                } else {
                    gestorLlamadas?.enviarSolicitudAmistad(usuarioId, miNombre, miFoto) ?: run {
                        val senal = "[SOLICITUD_AMISTAD:$usuarioId:$usuarioIdActual:$miNombre:$miFoto]"
                        repositorioMensajes.enviarMensaje(
                            com.renea.arete_social.domain.repository.ID_CHAT_GENERAL,
                            usuarioIdActual,
                            senal
                        )
                    }
                }
            }
        }
    }

    fun cancelarSolicitudAmistad() {
        val actual = _estado.value
        if (actual !is PerfilPublicoUiState.Listo || actual.esPropio) return
        viewModelScope.launch {
            if (amistadesEnServidor) quitarEnServidor()
            GestorAmistades.cancelarSolicitudEnviada(usuarioId)
        }
    }

    fun aceptarSolicitudAmistad() {
        val actual = _estado.value
        if (actual !is PerfilPublicoUiState.Listo || actual.esPropio) return
        viewModelScope.launch {
            if (amistadesEnServidor) {
                runCatching {
                    repositorioAmistades!!.aceptar(usuarioIdActual, usuarioId)
                }.onFailure {
                    android.util.Log.w("PerfilPublico", "No se pudo sincronizar aceptación con servidor (modo offline/Bluetooth): ${it.message}")
                }
            }
            avisar(null)
            GestorAmistades.agregarAmigo(usuarioId)
            runCatching {
                val (miNombre, miFoto) = datosPropiosParaSenal()
                gestorLlamadas?.responderSolicitudAmistad(usuarioId, true, miNombre, miFoto) ?: run {
                    val senal = "[RESPUESTA_AMISTAD:ACEPTADA:$usuarioId:$usuarioIdActual:$miNombre:$miFoto]"
                    repositorioMensajes.enviarMensaje(
                        com.renea.arete_social.domain.repository.ID_CHAT_GENERAL,
                        usuarioIdActual,
                        senal
                    )
                }
            }
        }
    }

    /** Declina la solicitud recibida (antes solo se podía aceptar desde esta pantalla). */
    fun rechazarSolicitudAmistad() {
        val actual = _estado.value
        if (actual !is PerfilPublicoUiState.Listo || actual.esPropio || !actual.solicitudRecibida) return
        viewModelScope.launch {
            if (amistadesEnServidor) quitarEnServidor()
            GestorAmistades.rechazarSolicitud(usuarioId)
            runCatching {
                val (miNombre, miFoto) = datosPropiosParaSenal()
                gestorLlamadas?.responderSolicitudAmistad(usuarioId, false, miNombre, miFoto)
            }
        }
    }

    fun eliminarAmigo() {
        val actual = _estado.value
        if (actual !is PerfilPublicoUiState.Listo || actual.esPropio) return
        viewModelScope.launch {
            if (amistadesEnServidor) quitarEnServidor()
            GestorAmistades.eliminarAmigo(usuarioId)
        }
    }

    /** Borra la fila de amistad/solicitud en el servidor. Devuelve false (y loguea) si falló. */
    private suspend fun quitarEnServidor(): Boolean = try {
        repositorioAmistades!!.eliminar(usuarioIdActual, usuarioId)
        avisar(null)
        true
    } catch (e: Exception) {
        android.util.Log.w("PerfilPublico", "No se pudo eliminar en servidor (offline/Bluetooth): ${e.message}")
        false
    }

    fun toggleAmigo() {
        val actual = _estado.value
        if (actual !is PerfilPublicoUiState.Listo || actual.esPropio) return
        if (actual.esAmigo) {
            eliminarAmigo()
        } else if (actual.solicitudRecibida) {
            aceptarSolicitudAmistad()
        } else if (actual.solicitudEnviada) {
            cancelarSolicitudAmistad()
        } else {
            enviarSolicitudAmistad()
        }
    }

    // ── Contenido del perfil: estados, fotos y librería ──────────────────────

    /**
     * Pide al servidor los estados, fotos y librería de esta persona. El servidor decide: si el
     * perfil es privado y no somos amistades, no devuelve nada (por eso ni se intenta).
     */
    fun cargarContenido() {
        val actual = _estado.value as? PerfilPublicoUiState.Listo ?: return
        if (!actual.contenidoVisible) {
            _publicaciones.value = emptyList()
            _libreria.value = LibreriaPerfilUiState()
            return
        }
        val repoPub = repositorioPublicaciones
        if (repoPub != null && publicacionesEnServidor) {
            viewModelScope.launch {
                runCatching { repoPub.listar(usuarioId) }
                    .onSuccess {
                        _publicaciones.value = it
                        // Con internet manda el servidor: la copia Bluetooth deja de mostrarse.
                        _bluetooth.update { b -> b.copy(copiaRecibidaEn = null) }
                    }
                    .onFailure { aplicarCopiaBluetooth() }
            }
        } else {
            aplicarCopiaBluetooth()
        }
        cargarLibreria()
    }

    // ── Perfil por Bluetooth (solo texto) ────────────────────────────────────

    /** Muestra la última copia en texto recibida por Bluetooth de esta persona, si hay una vigente. */
    private fun aplicarCopiaBluetooth() {
        if (!bluetoothDisponible) return
        val copia = cachePerfil?.recibido(usuarioId) ?: return
        _publicaciones.value = copia.aPublicaciones()
        _libreria.update { it.copy(cargando = false, error = false, obras = copia.aObras()) }
        _bluetooth.update { it.copy(copiaRecibidaEn = copia.actualizadoEn) }
        _estado.update { actual ->
            if (actual is PerfilPublicoUiState.Listo) {
                actual.copy(
                    usuario = actual.usuario.copy(
                        nombre = copia.nombre.ifBlank { actual.usuario.nombre },
                        descripcion = copia.descripcion.ifBlank { actual.usuario.descripcion },
                        foto = copia.foto ?: actual.usuario.foto,
                        portada = copia.portada ?: actual.usuario.portada,
                        hobby = copia.hobby ?: actual.usuario.hobby,
                        deporte = copia.deporte ?: actual.usuario.deporte,
                        ritmoMusical = copia.ritmoMusical ?: actual.usuario.ritmoMusical,
                        serieFavorita = copia.serieFavorita ?: actual.usuario.serieFavorita,
                        libroFavorito = copia.libroFavorito ?: actual.usuario.libroFavorito,
                        signoZodiacal = copia.signoZodiacal ?: actual.usuario.signoZodiacal
                    )
                )
            } else actual
        }
    }

    /**
     * Pide a los dispositivos cercanos el perfil de esta persona por Bluetooth. Solo responde ella
     * misma y si su perfil es público, o privado y eres su amistad; llega únicamente texto (sin fotos ni PDF).
     */
    fun buscarPorBluetooth() {
        val gestor = gestorPerfilBle ?: return
        if (!bluetoothDisponible || _bluetooth.value.buscando) return
        _bluetooth.update { it.copy(buscando = true, mensaje = null) }
        viewModelScope.launch {
            val resultado = runCatching { gestor.solicitar(usuarioId) }.getOrNull()
            when (resultado) {
                is ResultadoPerfilBle.Recibido,
                is ResultadoPerfilBle.ManifiestoRecibido -> {
                    aplicarCopiaBluetooth()
                    _bluetooth.update { it.copy(buscando = false, mensaje = null) }
                }
                ResultadoPerfilBle.SinDispositivos -> _bluetooth.update {
                    it.copy(buscando = false, mensaje = "No hay dispositivos cercanos conectados por Bluetooth.")
                }
                ResultadoPerfilBle.SinRespuesta, null -> _bluetooth.update {
                    it.copy(
                        buscando = false,
                        mensaje = "No respondió. Debe estar cerca, con la app abierta y, si su perfil es privado, ser tu amistad."
                    )
                }
            }
        }
    }

    fun consumirMensajeBluetooth() = _bluetooth.update { it.copy(mensaje = null) }

    fun cargarLibreria() {
        val repo = repositorioLibreria ?: return
        if (sesionLocal) return
        viewModelScope.launch {
            _libreria.update { it.copy(cargando = true, error = false) }
            runCatching { repo.listar(usuarioId) }
                .onSuccess { obras -> _libreria.update { it.copy(cargando = false, obras = obras) } }
                .onFailure {
                    // Sin internet: si hay una copia en texto recibida por Bluetooth, se muestra esa.
                    if (bluetoothDisponible && cachePerfil?.recibido(usuarioId) != null) {
                        aplicarCopiaBluetooth()
                    } else {
                        _libreria.update { it.copy(cargando = false, error = true) }
                    }
                }
        }
    }

    /** Enlace temporal para abrir el PDF de una obra; el servidor solo lo firma si puedo ver el perfil. */
    fun abrirPdf(obra: ObraLibreria, alObtener: (String) -> Unit) {
        val repo = repositorioLibreria ?: return
        viewModelScope.launch {
            runCatching { repo.urlTemporalPdf(obra) }
                .onSuccess(alObtener)
                .onFailure { avisar("No se pudo abrir el PDF.") }
        }
    }

    fun toggleLike(publicacionId: String) {
        val repoPub = repositorioPublicaciones
        if (repoPub != null && publicacionesEnServidor) {
            val yaDioLike = _publicaciones.value
                .firstOrNull { it.id == publicacionId }?.usuariosLike?.contains(usuarioIdActual) == true
            val darLike = !yaDioLike
            _publicaciones.update { it.conLikeDe(publicacionId, usuarioIdActual, darLike) }
            viewModelScope.launch {
                runCatching { repoPub.definirLike(publicacionId, usuarioIdActual, darLike) }
                    .onFailure { cargarContenido() }
            }
        } else {
            GestorPerfilEfemero.toggleLike(publicacionId, usuarioIdActual)
            gestorLlamadas?.transmitirLikePublicacion(publicacionId, usuarioIdActual)
        }
    }

    // ── Cartas ──────────────────────────────────────────────────────────────

    /** Carga (o recarga) la correspondencia entre yo y esta persona. */
    fun cargarCartas() {
        val repo = repositorioCartas ?: return
        if (!cartasDisponibles) return
        viewModelScope.launch {
            _cartas.update { it.copy(cargando = true, errorCarga = false) }
            runCatching { repo.conversacion(usuarioIdActual, usuarioId) }
                .onSuccess { lista -> _cartas.update { it.copy(cargando = false, cartas = lista) } }
                .onFailure { _cartas.update { it.copy(cargando = false, errorCarga = true) } }
        }
    }

    /**
     * Escribe una carta a esta persona. Solo entre amistades (misma regla que la bandeja de
     * cartas del perfil propio). [alTerminar] recibe null si salió bien o el motivo del fallo.
     */
    fun enviarCarta(texto: String, alTerminar: (error: String?) -> Unit) {
        val repo = repositorioCartas
        val actual = _estado.value
        if (repo == null || !cartasDisponibles) return alTerminar("Crea una cuenta para enviar cartas.")
        if (actual !is PerfilPublicoUiState.Listo || actual.esPropio) return alTerminar("No puedes escribirte a ti mismo.")
        if (actual.usuario.perfilPrivado && !actual.esAmigo) return alTerminar("Solo puedes escribirle a tus amistades si su perfil es privado.")
        val (limpio, error) = validarTextoCarta(texto)
        if (limpio == null) return alTerminar(error)

        viewModelScope.launch {
            _cartas.update { it.copy(enviando = true) }
            val miNombre = runCatching { repositorioUsuarios.obtenerPorId(usuarioIdActual)?.nombre }.getOrNull()
                ?: GestorSesionLocal.obtenerUltimoNombre()
                ?: "Alguien"
            runCatching {
                repo.enviar(usuarioIdActual, miNombre, NuevaCarta(usuarioId, actual.usuario.nombre, limpio))
            }.onSuccess { carta ->
                _cartas.update {
                    it.copy(
                        enviando = false,
                        cartas = listOf(carta) + it.cartas,
                        mensaje = "Carta enviada a ${actual.usuario.nombre}"
                    )
                }
                alTerminar(null)
            }.onFailure {
                _cartas.update { it.copy(enviando = false) }
                alTerminar("No se pudo enviar la carta. Revisa tu conexión.")
            }
        }
    }

    /** Marca como leída (optimista) una carta que esta persona me escribió. */
    fun abrirCarta(carta: Carta) {
        val repo = repositorioCartas ?: return
        if (carta.destinatarioId != usuarioIdActual || carta.leida) return
        _cartas.update { s -> s.copy(cartas = s.cartas.map { if (it.id == carta.id) it.copy(leida = true) else it }) }
        viewModelScope.launch { runCatching { repo.marcarLeida(carta.id) } }
    }

    fun eliminarCarta(carta: Carta) {
        val repo = repositorioCartas ?: return
        viewModelScope.launch {
            runCatching { repo.eliminar(carta.id) }
                .onSuccess { _cartas.update { s -> s.copy(cartas = s.cartas.filterNot { it.id == carta.id }) } }
                .onFailure { _cartas.update { it.copy(mensaje = "No se pudo eliminar la carta.") } }
        }
    }

    fun consumirMensajeCartas() = _cartas.update { it.copy(mensaje = null) }

    fun abrirChatDirecto(onChatListo: (chatId: String, nombre: String) -> Unit) {
        val actual = _estado.value
        if (actual !is PerfilPublicoUiState.Listo || actual.abriendoChat || actual.esPropio) return

        viewModelScope.launch {
            _estado.value = actual.copy(abriendoChat = true)
            try {
                val chatId = repositorioMensajes.obtenerOCrearChatDirecto(usuarioIdActual, usuarioId)
                _estado.value = actual.copy(abriendoChat = false)
                onChatListo(chatId, actual.usuario.nombre)
            } catch (e: Exception) {
                _estado.value = actual.copy(abriendoChat = false)
            }
        }
    }
}
