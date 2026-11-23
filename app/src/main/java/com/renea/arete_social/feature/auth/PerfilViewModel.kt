package com.renea.arete_social.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renea.arete_social.domain.model.Visibilidad
import com.renea.arete_social.domain.repository.RepositorioUsuarios
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import com.renea.arete_social.domain.model.PublicacionPerfil
import com.renea.arete_social.domain.model.SolicitudAmistad
import com.renea.arete_social.domain.model.Usuario
import com.renea.arete_social.domain.model.conLikeDe
import com.renea.arete_social.domain.repository.RepositorioAmistades
import com.renea.arete_social.domain.repository.RepositorioMensajes
import com.renea.arete_social.domain.repository.RepositorioPublicacionesPerfil
import com.renea.arete_social.feature.mensajeria.TextoMensaje

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull

data class MensajeDirectoItem(
    val chatId: String = "",
    val remitenteId: String = "",
    val remitenteNombre: String = "",
    val remitenteFoto: String? = null,
    val ultimoMensaje: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(kotlinx.coroutines.FlowPreview::class)
class PerfilViewModel(
    private val repositorioUsuarios: RepositorioUsuarios,
    private val usuarioId: String,
    private val repositorioMensajes: RepositorioMensajes? = null,
    private val gestorLlamadas: com.renea.arete_social.feature.llamadas.GestorLlamadasYAudio? = null,
    private val gestorOutboxLocal: com.renea.arete_social.data.local.outbox.GestorOutboxLocal? = null,
    private val esInvitado: Boolean = false,
    private val repositorioAmistades: RepositorioAmistades? = null,
    private val repositorioPublicaciones: RepositorioPublicacionesPerfil? = null,
    // Copia en texto de mi perfil para entregarlo por Bluetooth (público a cualquiera cercano; privado solo a mis amistades).
    private val cachePerfil: com.renea.arete_social.data.local.perfil.CachePerfilLocal? = null,
    // Para abrir el último mensaje cifrado y mostrar su texto real en la lista de chats.
    private val cifradorE2ee: com.renea.arete_social.domain.crypto.CifradorE2ee? = null,
    // Persistencia y caché local completa de perfiles (propio y amigos) para uso offline inmediato
    private val cacheUsuariosLocal: com.renea.arete_social.data.local.perfil.CacheUsuariosLocal? = null,
    // Callback invocado tras cada guardado de perfil para disparar el broadcast BLE/LAN inmediato.
    private val onPerfilGuardado: (() -> Unit)? = null
) : ViewModel() {

    // Con sesión local (sin cuenta en el servidor) todo sigue funcionando solo en el teléfono.
    private val sesionLocal = usuarioId.startsWith("invitado-local-")
    private val amistadesEnServidor = repositorioAmistades != null && !sesionLocal
    private val publicacionesEnServidor = repositorioPublicaciones != null && !sesionLocal

    private val _avisos = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Avisos breves para mostrar a la persona (p. ej. "No se pudo publicar"). */
    val avisos: SharedFlow<String> = _avisos.asSharedFlow()

    /** Indica si los cambios del perfil deben mantenerse solo en memoria. */
    val sesionEsInvitado: Boolean get() = esInvitado

    private val _estado = MutableStateFlow<PerfilUiState>(PerfilUiState.Cargando)
    val estado: StateFlow<PerfilUiState> = _estado.asStateFlow()

    private val _amigos = MutableStateFlow<List<Usuario>>(emptyList())
    val amigos: StateFlow<List<Usuario>> = _amigos.asStateFlow()

    val solicitudes: StateFlow<List<SolicitudAmistad>> = GestorAmistades.solicitudes

    private val _publicaciones = MutableStateFlow<List<PublicacionPerfil>>(emptyList())

    /** Mis estados y fotos vigentes: vienen del servidor (así los ven los demás según mi privacidad). */
    val publicaciones: StateFlow<List<PublicacionPerfil>> =
        if (publicacionesEnServidor) _publicaciones.asStateFlow() else GestorPerfilEfemero.publicaciones

    private val _mensajesRecibidos = MutableStateFlow<List<MensajeDirectoItem>>(emptyList())
    val mensajesRecibidos: StateFlow<List<MensajeDirectoItem>> = _mensajesRecibidos.asStateFlow()

    init {
        cargar()
        recargarPublicaciones()
        viewModelScope.launch(Dispatchers.IO) {
            GestorAmistades.amigosIds.collect {
                cargarAmigos()
                cargarMensajesRecibidos()
            }
        }
        // Reactividad: cualquier cambio en el historial local (mensaje recibido por BLE, chat nuevo
        // abierto desde Cercanos/Amigos, mensaje local…) refresca "Activos".
        // `debounce` agrupa ráfagas; antes cada notificación disparaba una recarga completa y la
        // recarga misma notificaba (purgarAntiguos) → bucle infinito.
        if (gestorOutboxLocal != null) {
            viewModelScope.launch(Dispatchers.IO) {
                gestorOutboxLocal.outboxCambios.debounce(400).collect { cargarMensajesRecibidos() }
            }
        }
        // Actualización periódica: los previews salen del historial local.
        // en el siguiente ciclo. Barato: los previews salen del historial local.
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(20_000)
                cargarMensajesRecibidos()
            }
        }
    }

    /** Fuerza la recarga completa del perfil (llamado desde el pull-to-refresh). */
    fun recargar() {
        cargar()
        recargarPublicaciones()
        cargarAmigos()
    }

    private var jobCargaMensajes: Job? = null

    private fun guardarCopiaBluetoothEstados() {
        val pubs = if (publicacionesEnServidor) _publicaciones.value else GestorPerfilEfemero.publicaciones.value
        runCatching { cachePerfil?.actualizarMisEstados(usuarioId, pubs) }
    }

    private fun guardarCopiaBluetoothPerfil(usuario: Usuario) {
        val foto = usuario.foto?.takeIf { it.isNotBlank() } ?: GestorSesionLocal.obtenerUltimaFoto()
        val portada = usuario.portada?.takeIf { it.isNotBlank() } ?: GestorSesionLocal.obtenerUltimaPortada()
        val fotoHash = foto?.let { f ->
            if (f.startsWith("/") || f.startsWith("file:")) {
                com.renea.arete_social.data.local.media.AlmacenMediaPerfil.obtenerOGenerarHashMiniatura(f, esAvatar = true)
            } else null
        }
        val portadaHash = portada?.let { p ->
            if (p.startsWith("/") || p.startsWith("file:")) {
                com.renea.arete_social.data.local.media.AlmacenMediaPerfil.obtenerOGenerarHashMiniatura(p, esAvatar = false)
            } else null
        }

        runCatching {
            cachePerfil?.actualizarMiPerfil(
                miId = usuarioId,
                nombre = usuario.nombre,
                descripcion = usuario.descripcion,
                privado = usuario.perfilPrivado,
                foto = foto,
                portada = portada,
                fotoHash = fotoHash,
                portadaHash = portadaHash,
                hobby = usuario.hobby,
                deporte = usuario.deporte,
                ritmoMusical = usuario.ritmoMusical,
                serieFavorita = usuario.serieFavorita,
                libroFavorito = usuario.libroFavorito,
                signoZodiacal = usuario.signoZodiacal
            )
        }
        // Disparar broadcast BLE/LAN inmediato para que peers cercanos reciban el perfil actualizado
        onPerfilGuardado?.invoke()
    }

    /** Descarga del servidor mis publicaciones vigentes y limpia las que ya vencieron. */
    fun recargarPublicaciones() {
        val repo = repositorioPublicaciones ?: return
        if (!publicacionesEnServidor) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repo.limpiarVencidasPropias(usuarioId) }
            runCatching { repo.listar(usuarioId) }.onSuccess {
                _publicaciones.value = it
                guardarCopiaBluetoothEstados()
            }
        }
    }

    fun publicarNotaTexto(texto: String) {
        val miNombre = (_estado.value as? PerfilUiState.Listo)?.usuario?.nombre ?: "Usuario"
        if (texto.isBlank()) return

        // 1. Guardado Local-First inmediato (memoria, outbox y réplica P2P)
        val pubLocal = GestorPerfilEfemero.agregarPublicacionTexto(usuarioId, miNombre, texto)
        if (pubLocal != null) {
            _publicaciones.update { listOf(pubLocal) + it.filterNot { p -> p.id == pubLocal.id } }
            gestorLlamadas?.transmitirPublicacionPerfil(pubLocal)
            guardarCopiaBluetoothEstados()
            onPerfilGuardado?.invoke()
        }

        // 2. Sincronización transparente con servidor si hay cuenta
        val repo = repositorioPublicaciones
        if (repo != null && publicacionesEnServidor) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { repo.publicarTexto(usuarioId, miNombre, texto) }
                    .onSuccess { nueva ->
                        _publicaciones.update { lista -> listOf(nueva) + lista.filterNot { it.id == pubLocal?.id || it.id == nueva.id } }
                        guardarCopiaBluetoothEstados()
                        onPerfilGuardado?.invoke()
                    }
                    .onFailure {
                        android.util.Log.d("PerfilViewModel", "Nota guardada localmente (servidor no disponible)")
                    }
            }
        }
    }

    /** [fotoBase64OUrl] llega como "data:image/jpeg;base64,..." desde la pantalla de perfil. */
    fun publicarFotoMatrix(fotoBase64OUrl: String) {
        val miNombre = (_estado.value as? PerfilUiState.Listo)?.usuario?.nombre ?: "Usuario"

        // 1. Guardado Local-First inmediato
        val pubLocal = GestorPerfilEfemero.agregarPublicacionFoto(usuarioId, miNombre, fotoBase64OUrl)
        if (pubLocal != null) {
            _publicaciones.update { listOf(pubLocal) + it.filterNot { p -> p.id == pubLocal.id } }
            gestorLlamadas?.transmitirPublicacionPerfil(pubLocal)
            guardarCopiaBluetoothEstados()
            onPerfilGuardado?.invoke()
        }

        // 2. Sincronización en segundo plano con servidor
        val repo = repositorioPublicaciones
        if (repo != null && publicacionesEnServidor) {
            val bytes = runCatching {
                android.util.Base64.decode(fotoBase64OUrl.substringAfter("base64,"), android.util.Base64.NO_WRAP)
            }.getOrNull()
            if (bytes != null && bytes.isNotEmpty()) {
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching { repo.publicarFoto(usuarioId, miNombre, bytes) }
                        .onSuccess { nueva ->
                            _publicaciones.update { lista -> listOf(nueva) + lista.filterNot { it.id == pubLocal?.id || it.id == nueva.id } }
                            guardarCopiaBluetoothEstados()
                            onPerfilGuardado?.invoke()
                        }
                        .onFailure {
                            android.util.Log.d("PerfilViewModel", "Foto guardada localmente (servidor no disponible)")
                        }
                }
            }
        }
    }

    fun toggleLike(publicacionId: String) {
        val yaDioLike = _publicaciones.value
            .firstOrNull { it.id == publicacionId }?.usuariosLike?.contains(usuarioId) == true
        val darLike = !yaDioLike
        _publicaciones.update { it.conLikeDe(publicacionId, usuarioId, darLike) }
        GestorPerfilEfemero.toggleLike(publicacionId, usuarioId)
        gestorLlamadas?.transmitirLikePublicacion(publicacionId, usuarioId)
        guardarCopiaBluetoothEstados()

        val repo = repositorioPublicaciones
        if (repo != null && publicacionesEnServidor) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { repo.definirLike(publicacionId, usuarioId, darLike) }
                    .onFailure { android.util.Log.w("PerfilViewModel", "No se pudo sincronizar like en servidor") }
            }
        }
    }

    fun eliminarPublicacion(publicacionId: String) {
        val repo = repositorioPublicaciones
        if (repo != null && publicacionesEnServidor) {
            val pub = _publicaciones.value.firstOrNull { it.id == publicacionId } ?: return
            _publicaciones.update { lista -> lista.filterNot { it.id == publicacionId } }
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { repo.eliminar(pub) }
                    .onSuccess { guardarCopiaBluetoothEstados() }
                    .onFailure {
                        _avisos.tryEmit("No se pudo eliminar la publicación.")
                        recargarPublicaciones()
                    }
            }
            return
        }
        GestorPerfilEfemero.eliminarPublicacion(publicacionId)
    }

    fun aceptarSolicitud(solicitud: SolicitudAmistad) {
        viewModelScope.launch {
            if (amistadesEnServidor) {
                runCatching {
                    repositorioAmistades!!.aceptar(usuarioId, solicitud.remitenteId)
                }.onFailure {
                    android.util.Log.w("PerfilViewModel", "No se pudo sincronizar aceptación en servidor (offline/Bluetooth): ${it.message}")
                }
            }
            GestorAmistades.agregarAmigo(solicitud.remitenteId)
            runCatching {
                val miPerfil = repositorioUsuarios.obtenerPorId(usuarioId)
                val nombreLocal = com.renea.arete_social.feature.auth.GestorSesionLocal.obtenerUltimoNombre()
                val miNombre = miPerfil?.nombre ?: nombreLocal ?: "Usuario"
                val miFoto = miPerfil?.foto ?: ""
                gestorLlamadas?.responderSolicitudAmistad(
                    destinatarioId = solicitud.remitenteId,
                    aceptada = true,
                    remitenteNombre = miNombre,
                    remitenteFoto = miFoto
                ) ?: run {
                    val senal = "[RESPUESTA_AMISTAD:ACEPTADA:${solicitud.remitenteId}:$usuarioId:$miNombre:$miFoto]"
                    repositorioMensajes?.enviarMensaje(
                        com.renea.arete_social.domain.repository.ID_CHAT_GENERAL,
                        usuarioId,
                        senal
                    )
                }
            }
            cargarAmigos()
        }
    }

    fun rechazarSolicitud(remitenteId: String) {
        viewModelScope.launch {
            if (amistadesEnServidor) {
                runCatching {
                    repositorioAmistades!!.eliminar(usuarioId, remitenteId)
                }.onFailure {
                    android.util.Log.w("PerfilViewModel", "No se pudo sincronizar rechazo en servidor (offline/Bluetooth): ${it.message}")
                }
            }
            GestorAmistades.rechazarSolicitud(remitenteId)
            runCatching {
                val miPerfil = repositorioUsuarios.obtenerPorId(usuarioId)
                val nombreLocal = com.renea.arete_social.feature.auth.GestorSesionLocal.obtenerUltimoNombre()
                val miNombre = miPerfil?.nombre ?: nombreLocal ?: "Usuario"
                val miFoto = miPerfil?.foto ?: ""
                gestorLlamadas?.responderSolicitudAmistad(
                    destinatarioId = remitenteId,
                    aceptada = false,
                    remitenteNombre = miNombre,
                    remitenteFoto = miFoto
                )
            }
        }
    }

    fun eliminarAmigo(amigoId: String) {
        viewModelScope.launch {
            if (amistadesEnServidor) {
                try {
                    repositorioAmistades!!.eliminar(usuarioId, amigoId)
                } catch (e: Exception) {
                    _avisos.tryEmit("No se pudo eliminar la amistad. Revisa tu conexión.")
                    return@launch
                }
            }
            GestorAmistades.eliminarAmigo(amigoId)
            cargarAmigos()
        }
    }

    fun cargarMensajesRecibidos() {
        val repoMsg = repositorioMensajes ?: return
        // Una sola carga a la vez: si llega otra petición se cancela la anterior.
        jobCargaMensajes?.cancel()
        jobCargaMensajes = viewModelScope.launch(Dispatchers.IO) {
            val items = mutableListOf<MensajeDirectoItem>()
            val limite48h = System.currentTimeMillis() - (48 * 3600 * 1000L)

            // 1. Chats directos: mapa local persistido.
            val chatsDirectosMap = runCatching { repoMsg.obtenerTodosMisChatsDirectos(usuarioId) }
                .getOrDefault(emptyMap()).toMutableMap()

            // 2. Amigos con chat ya existente.
            for (amigoId in GestorAmistades.amigosIds.value.filter { it != usuarioId }) {
                if (chatsDirectosMap.containsValue(amigoId)) continue
                val cId = runCatching { repoMsg.buscarChatDirectoExistente(usuarioId, amigoId) }.getOrNull()
                if (cId != null) chatsDirectosMap[cId] = amigoId
            }

            val chatIds = chatsDirectosMap.keys.toList()
            val ultimosMensajesMap = repoMsg.obtenerUltimosMensajes(chatIds)
            val timestampsLocalMap = gestorOutboxLocal?.obtenerChatsDirectosConTimestamp(usuarioId) ?: emptyMap()

            for ((chatId, otroUsuarioId) in chatsDirectosMap) {
                // "senales-mesh" es un canal técnico, no una conversación.
                if (chatId == "senales-mesh") continue
                runCatching {
                    val u = runCatching { repositorioUsuarios.obtenerPorId(otroUsuarioId) }.getOrNull()
                    val ultimoMsg = ultimosMensajesMap[chatId]
                        ?: repoMsg.obtenerUltimoMensajeLocal(chatId, usuarioId)

                    val chatTimestamp = ultimoMsg?.timestamp
                        ?: timestampsLocalMap[chatId]
                        ?: System.currentTimeMillis()

                    // Regla de 48h: si el último mensaje o la creación del chat es más antiguo que 48h, no se incluye
                    if (chatTimestamp <= limite48h) return@runCatching

                    val preview = if (ultimoMsg != null) {
                        val esPropio = ultimoMsg.remitenteId == usuarioId
                        val descifrado = descifrarParaVistaPrevia(
                            contenido = ultimoMsg.contenido,
                            remitenteId = ultimoMsg.remitenteId,
                            otroUsuarioId = otroUsuarioId,
                            clavePublicaOtro = u?.clavePublicaE2ee
                        )
                        TextoMensaje.vistaPrevia(ultimoMsg.contenido, esPropio, descifrado)
                    } else {
                        "Toca para iniciar conversación"
                    }
                    val nombre = u?.nombre
                        ?: _amigos.value.firstOrNull { it.id == otroUsuarioId }?.nombre
                        ?: "Usuario"

                    items.add(
                        MensajeDirectoItem(
                            chatId = chatId,
                            remitenteId = otroUsuarioId,
                            remitenteNombre = nombre,
                            remitenteFoto = u?.foto,
                            ultimoMensaje = preview,
                            timestamp = chatTimestamp
                        )
                    )
                }
            }
            _mensajesRecibidos.value = items.sortedByDescending { it.timestamp }
        }
    }

    /** Intenta abrir un mensaje cifrado; devuelve null si no está cifrado o no se pudo abrir. */
    private fun descifrarParaVistaPrevia(
        contenido: String,
        remitenteId: String,
        otroUsuarioId: String,
        clavePublicaOtro: String?
    ): String? {
        val cifrador = cifradorE2ee ?: return null
        if (!runCatching { cifrador.esCifrado(contenido) }.getOrDefault(false)) return null
        val clave = cifrador.obtenerClavePublicaConocida(otroUsuarioId) ?: clavePublicaOtro ?: return null
        val texto = runCatching {
            cifrador.descifrar(
                textoCifrado = contenido,
                clavePublicaContraparteBase64 = clave,
                miUsuarioId = usuarioId,
                remitenteOriginalId = remitenteId,
                destinatarioOriginalId = if (remitenteId == usuarioId) otroUsuarioId else usuarioId
            )
        }.getOrNull() ?: return null
        // El cifrador devuelve este texto cuando falla: no es un mensaje real.
        return texto.takeUnless { it.contains("Mensaje cifrado E2EE") }
    }

    fun cargarAmigos() {
        viewModelScope.launch(Dispatchers.IO) {
            val lista = mutableListOf<Usuario>()
            for (id in GestorAmistades.amigosIds.value) {
                val u = runCatching { repositorioUsuarios.obtenerPorId(id) }.getOrNull()
                if (u != null) {
                    lista.add(u)
                } else {
                    lista.add(
                        Usuario(
                            id = id,
                            nombre = "Cargando…",
                            foto = null,
                            descripcion = "Contacto guardado",
                            estadoVisibilidad = Visibilidad.VISIBLE,
                            ubicacion = null,
                            clavePublicaE2ee = null
                        )
                    )
                }
            }
            _amigos.value = lista
        }
    }

    private fun cargar() {
        // Carga síncrona/inmediata de la caché para mostrar datos instantáneamente si existen
        val usuarioEnCache = cacheUsuariosLocal?.obtener(usuarioId)
        if (usuarioEnCache != null) {
            val fotoFinal = usuarioEnCache.foto?.takeIf { it.isNotBlank() } ?: GestorSesionLocal.obtenerUltimaFoto()
            val portadaFinal = usuarioEnCache.portada?.takeIf { it.isNotBlank() } ?: GestorSesionLocal.obtenerUltimaPortada()
            val usuarioFinal = if (esInvitado) {
                GestorPerfilDatosTemporales.aplicar(usuarioEnCache.copy(foto = fotoFinal, portada = portadaFinal))
            } else {
                usuarioEnCache.copy(foto = fotoFinal, portada = portadaFinal)
            }
            _estado.value = PerfilUiState.Listo(usuarioFinal, sinConexion = false)
            guardarCopiaBluetoothPerfil(usuarioFinal)
            guardarCopiaBluetoothEstados()
        }

        viewModelScope.launch(Dispatchers.IO) {
            val usuario = runCatching { repositorioUsuarios.obtenerPorId(usuarioId) }.getOrNull()

            _estado.value = when {
                usuario != null -> {
                    cacheUsuariosLocal?.guardar(usuario)
                    GestorSesionLocal.guardarUltimoNombre(usuario.nombre)
                    if (!usuario.foto.isNullOrBlank()) {
                        GestorSesionLocal.guardarUltimaFoto(usuario.foto)
                    }
                    if (!usuario.portada.isNullOrBlank()) {
                        GestorSesionLocal.guardarUltimaPortada(usuario.portada)
                    }
                    val fotoFinal = usuario.foto?.takeIf { it.isNotBlank() } ?: GestorSesionLocal.obtenerUltimaFoto()
                    val portadaFinal = usuario.portada?.takeIf { it.isNotBlank() } ?: GestorSesionLocal.obtenerUltimaPortada()
                    val usuarioListo = usuario.copy(foto = fotoFinal, portada = portadaFinal)
                    guardarCopiaBluetoothPerfil(usuarioListo)
                    guardarCopiaBluetoothEstados()
                    PerfilUiState.Listo(if (esInvitado) GestorPerfilDatosTemporales.aplicar(usuarioListo) else usuarioListo)
                }
                usuarioEnCache != null -> {
                    val fotoFinal = usuarioEnCache.foto?.takeIf { it.isNotBlank() } ?: GestorSesionLocal.obtenerUltimaFoto()
                    val portadaFinal = usuarioEnCache.portada?.takeIf { it.isNotBlank() } ?: GestorSesionLocal.obtenerUltimaPortada()
                    val usuarioListo = usuarioEnCache.copy(foto = fotoFinal, portada = portadaFinal)
                    guardarCopiaBluetoothPerfil(usuarioListo)
                    guardarCopiaBluetoothEstados()
                    PerfilUiState.Listo(
                        usuario = if (esInvitado) GestorPerfilDatosTemporales.aplicar(usuarioListo) else usuarioListo,
                        sinConexion = true
                    )
                }
                else -> {
                    val nombreLocal = GestorSesionLocal.obtenerUltimoNombre()
                    val fotoLocal = GestorSesionLocal.obtenerUltimaFoto()
                    val portadaLocal = GestorSesionLocal.obtenerUltimaPortada()
                    if (nombreLocal != null) {
                        val usuarioRespaldo = Usuario(
                            id = usuarioId,
                            nombre = nombreLocal,
                            foto = fotoLocal,
                            descripcion = "",
                            estadoVisibilidad = Visibilidad.VISIBLE,
                            ubicacion = null,
                            clavePublicaE2ee = null,
                            portada = portadaLocal
                        )
                        guardarCopiaBluetoothPerfil(usuarioRespaldo)
                        guardarCopiaBluetoothEstados()
                        PerfilUiState.Listo(
                            usuario = usuarioRespaldo,
                            sinConexion = true
                        )
                    } else {
                        PerfilUiState.Error("No se encontró el perfil")
                    }
                }
            }
            cargarAmigos()
        }
    }

    /** Guarda nombre y descripción. La foto se maneja aparte, con [subirFoto]. */
    fun actualizarPerfil(nombre: String, descripcion: String) {
        val actual = _estado.value
        if (actual !is PerfilUiState.Listo) return
        val fotoVigente = actual.usuario.foto?.takeIf { it.isNotBlank() } ?: GestorSesionLocal.obtenerUltimaFoto()
        val portadaVigente = actual.usuario.portada?.takeIf { it.isNotBlank() } ?: GestorSesionLocal.obtenerUltimaPortada()
        val usuarioActualizado = actual.usuario.copy(
            nombre = nombre,
            descripcion = descripcion,
            foto = fotoVigente,
            portada = portadaVigente
        )

        if (esInvitado) {
            val previo = GestorPerfilDatosTemporales.obtener(usuarioId)
            GestorPerfilDatosTemporales.guardar(usuarioId, previo.copy(nombre = nombre))
            _estado.value = actual.copy(usuario = usuarioActualizado)
            cacheUsuariosLocal?.guardar(usuarioActualizado)
            GestorSesionLocal.guardarUltimoNombre(nombre)
            guardarCopiaBluetoothPerfil(usuarioActualizado)
            return
        }
        _estado.value = actual.copy(guardando = true)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repositorioUsuarios.actualizarPerfil(usuarioId, nombre, fotoVigente, descripcion)
            }
                .onSuccess {
                    GestorSesionLocal.guardarUltimoNombre(nombre)
                    cacheUsuariosLocal?.guardar(usuarioActualizado)
                    guardarCopiaBluetoothPerfil(usuarioActualizado)
                    cargar()
                }
                .onFailure { error ->
                    _estado.value = actual.copy(guardando = false)
                    _avisos.tryEmit("No se pudo guardar el perfil: ${error.message ?: "Revisa tu conexión"}")
                }
        }
    }

    /** Guarda los intereses del perfil; para invitados solo vive en memoria. */
    fun actualizarDatosPersonales(
        hobby: String, deporte: String, ritmoMusical: String,
        serieFavorita: String, libroFavorito: String, signoZodiacal: String
    ) {
        val actual = _estado.value as? PerfilUiState.Listo ?: return
        val datos = GestorPerfilDatosTemporales.obtener(usuarioId).copy(nombre = actual.usuario.nombre, hobby = hobby, deporte = deporte, ritmoMusical = ritmoMusical, serieFavorita = serieFavorita, libroFavorito = libroFavorito, signoZodiacal = signoZodiacal)
        if (esInvitado) {
            GestorPerfilDatosTemporales.guardar(usuarioId, datos)
            _estado.value = actual.copy(usuario = actual.usuario.copy(
                hobby = hobby, deporte = deporte, ritmoMusical = ritmoMusical,
                serieFavorita = serieFavorita, libroFavorito = libroFavorito, signoZodiacal = signoZodiacal
            ))
            return
        }
        _estado.value = actual.copy(guardando = true)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repositorioUsuarios.actualizarDatosPersonales(usuarioId, hobby, deporte, ritmoMusical, serieFavorita, libroFavorito, signoZodiacal) }
                .onSuccess {
                    cargar()
                    // Propagar inmediatamente el cambio a peers cercanos vía BLE/LAN
                    val usuarioActualizado = actual.usuario.copy(
                        hobby = hobby, deporte = deporte, ritmoMusical = ritmoMusical,
                        serieFavorita = serieFavorita, libroFavorito = libroFavorito, signoZodiacal = signoZodiacal
                    )
                    guardarCopiaBluetoothPerfil(usuarioActualizado)
                }
                .onFailure { error -> _estado.value = PerfilUiState.Error(error.message ?: "No se pudieron guardar los datos personales") }
        }
    }

    /**
     * Perfil público (todo el mundo ve mis estados, fotos y librería) o privado (solo mis amistades).
     * El cambio se refleja de inmediato y se revierte si el servidor no lo pudo guardar.
     */
    fun actualizarPerfilPrivado(privado: Boolean) {
        val actual = _estado.value as? PerfilUiState.Listo ?: return
        if (actual.usuario.perfilPrivado == privado) return
        val anterior = actual.usuario.perfilPrivado
        fun aplicar(valor: Boolean) = _estado.update { s ->
            if (s is PerfilUiState.Listo) s.copy(usuario = s.usuario.copy(perfilPrivado = valor)) else s
        }
        aplicar(privado)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repositorioUsuarios.actualizarPerfilPrivado(usuarioId, privado) }
                .onSuccess {
                    (_estado.value as? PerfilUiState.Listo)?.usuario?.let(::guardarCopiaBluetoothPerfil)
                }
                .onFailure {
                    aplicar(anterior)
                    _avisos.tryEmit("No se pudo cambiar la privacidad. Revisa tu conexión.")
                }
        }
    }

    fun actualizarVisibilidad(visibilidad: Visibilidad) {
        val actual = _estado.value
        if (actual !is PerfilUiState.Listo) return
        _estado.value = actual.copy(guardando = true)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repositorioUsuarios.actualizarVisibilidad(usuarioId, visibilidad) }
                .onSuccess {
                    GestorSesionLocal.guardarUltimaVisibilidad(visibilidad)
                    cargar()
                }
                .onFailure { error ->
                    _estado.update { s -> (s as? PerfilUiState.Listo)?.copy(guardando = false) ?: s }
                    _avisos.tryEmit("No se pudo actualizar la visibilidad: ${error.message ?: "Revisa tu conexión"}")
                }
        }
    }

    private fun aplicarPortada(url: String?, subiendo: Boolean) = _estado.update { s ->
        if (s is PerfilUiState.Listo) s.copy(usuario = s.usuario.copy(portada = url), subiendoPortada = subiendo) else s
    }

    /**
     * Guarda la portada ajustada en disco y genera miniaturas.
     * Persiste localmente tanto para invitados como usuarios con cuenta, y sincroniza si hay red.
     */
    fun subirPortada(bytes: ByteArray, extension: String) {
        val actual = _estado.value as? PerfilUiState.Listo ?: return
        val media = com.renea.arete_social.data.local.media.AlmacenMediaPerfil.guardarImagen(bytes, esAvatar = false, extension = extension)
        val local = media.rutaArchivo
        GestorSesionLocal.guardarUltimaPortada(local)
        GestorPerfilDatosTemporales.guardar(usuarioId, GestorPerfilDatosTemporales.obtener(usuarioId).copy(portada = local))
        val usuarioConPortada = actual.usuario.copy(portada = local)
        cacheUsuariosLocal?.guardar(usuarioConPortada)
        aplicarPortada(local, subiendo = false)
        guardarCopiaBluetoothPerfil(usuarioConPortada)

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val url = repositorioUsuarios.subirPortada(usuarioId, bytes, extension)
                if (url.isNotBlank()) {
                    repositorioUsuarios.actualizarPortada(usuarioId, url)
                    GestorSesionLocal.guardarUltimaPortada(url)
                }
            }
        }
    }

    /** Quita la portada y vuelve al degradado por defecto. */
    fun quitarPortada() {
        val actual = _estado.value as? PerfilUiState.Listo ?: return
        GestorSesionLocal.guardarUltimaPortada(null)
        GestorPerfilDatosTemporales.guardar(usuarioId, GestorPerfilDatosTemporales.obtener(usuarioId).copy(portada = ""))
        val usuarioSinPortada = actual.usuario.copy(portada = null)
        cacheUsuariosLocal?.guardar(usuarioSinPortada)
        aplicarPortada(null, subiendo = false)
        guardarCopiaBluetoothPerfil(usuarioSinPortada)

        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repositorioUsuarios.actualizarPortada(usuarioId, null) }
        }
    }

    /**
     * Sube [bytes] como su nueva foto de perfil, guardándola localmente en disco y sincronizándola si hay red.
     */
    fun subirFoto(bytes: ByteArray, extension: String) {
        val actual = _estado.value
        if (actual !is PerfilUiState.Listo) return

        val media = com.renea.arete_social.data.local.media.AlmacenMediaPerfil.guardarImagen(bytes, esAvatar = true, extension = extension)
        val local = media.rutaArchivo
        GestorSesionLocal.guardarUltimaFoto(local)
        val usuarioActualizado = actual.usuario.copy(foto = local)
        cacheUsuariosLocal?.guardar(usuarioActualizado)
        GestorPerfilDatosTemporales.guardar(usuarioId, GestorPerfilDatosTemporales.obtener(usuarioId).copy(foto = local))
        _estado.value = actual.copy(
            usuario = usuarioActualizado,
            subiendoFoto = false
        )
        guardarCopiaBluetoothPerfil(usuarioActualizado)

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val url = repositorioUsuarios.subirFoto(usuarioId, bytes, extension)
                if (url.isNotBlank()) {
                    repositorioUsuarios.actualizarPerfil(
                        usuarioId,
                        actual.usuario.nombre,
                        url,
                        actual.usuario.descripcion
                    )
                    GestorSesionLocal.guardarUltimaFoto(url)
                }
            }
        }
    }
}