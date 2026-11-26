package com.renea.arete_social.feature.mensajeria

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renea.arete_social.domain.crypto.CifradoE2eeException
import com.renea.arete_social.domain.crypto.CifradorE2ee
import com.renea.arete_social.domain.mesh.MessageRouter
import com.renea.arete_social.domain.model.Mensaje
import com.renea.arete_social.domain.model.MotivoReporte
import com.renea.arete_social.domain.model.TipoReporte
import com.renea.arete_social.domain.repository.RepositorioMensajes
import com.renea.arete_social.domain.repository.RepositorioModeracion
import com.renea.arete_social.domain.repository.RepositorioUsuarios
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Gestiona un chat individual o grupal identificado por [chatId].
 * Si el chat es persona a persona (1-a-1) y ambos cuentan con claves públicas,
 * activa automáticamente el Cifrado de Extremo a Extremo (E2EE) con ECDH + AES-GCM.
 *
 * @param messageRouter Opcional. Cuando se proporciona, este ViewModel observa
 *   [MessageRouter.alertaClaveTofu] y expone los eventos de cambio de clave TOFU
 *   a la UI para mostrar un banner de advertencia de seguridad (§2.1).
 */
class ChatViewModel(
    private val repositorioMensajes: RepositorioMensajes,
    private val repositorioUsuarios: RepositorioUsuarios,
    private val cifradorE2ee: CifradorE2ee,
    private val chatId: String,
    private val usuarioId: String,
    private val gestorLlamadas: com.renea.arete_social.feature.llamadas.GestorLlamadasYAudio? = null,
    private val messageRouter: MessageRouter? = null,
    private val repositorioModeracion: RepositorioModeracion? = null,
    private val resolverPerfiles: com.renea.arete_social.domain.repository.ResolverPerfiles? = null,
    private val gestorPerfilMediaBle: com.renea.arete_social.data.mesh.GestorPerfilMediaBle? = null,
    private val resolverIdentidad: com.renea.arete_social.domain.repository.ResolverIdentidad? = null
) : ViewModel() {

    private val _estado = MutableStateFlow<ChatUiState>(ChatUiState.Listo(mensajes = emptyList()))
    val estado: StateFlow<ChatUiState> = _estado.asStateFlow()

    /**
     * §2.1 — ID del peer cuya clave TOFU cambió y no pudo validarse contra una clave previa conocida.
     * La UI muestra un banner de advertencia de seguridad mientras este valor no sea null.
     * El usuario puede descartar el banner llamando a [descartarAlertaTofu].
     */
    private val _alertaTofuPeerId = MutableStateFlow<String?>(null)
    val alertaTofuPeerId: StateFlow<String?> = _alertaTofuPeerId.asStateFlow()

    fun descartarAlertaTofu() { _alertaTofuPeerId.value = null }

    /**
     * Confirma la nueva identidad criptográfica de un contacto (por ejemplo tras reinstalación de app).
     * Reinicia el pin TOFU en el router, borra la clave E2EE obsoleta, solicita nuevo IDENT v2
     * y reintenta el descifrado de los mensajes en pantalla.
     */
    fun confiarEnNuevaIdentidad(peerId: String) {
        _alertaTofuPeerId.value = null
        messageRouter?.confiarEnNuevaIdentidad(peerId)
        cifradorE2ee.borrarClavePublicaConocida(peerId)
        clavesPublicasConocidas.remove(peerId)
        if (destinatarioIdDirecto == peerId) {
            clavePublicaDestinatario = null
        }
        solicitarClaveIdent(peerId)
        if (ultimoSnapshotServidor.isNotEmpty()) {
            viewModelScope.launch {
                actualizarMensajes(combinarConPendientes(ultimoSnapshotServidor))
            }
        }
    }

    private val nombresConocidos = mutableMapOf<String, String>()
    private val fotosConocidas = mutableMapOf<String, String?>()
    /** Remitentes cuya consulta falló: se reintenta pasado un rato, no en cada refresco del sondeo. */
    private val consultasFallidas = mutableMapOf<String, Long>()
    private val clavesPublicasConocidas = mutableMapOf<String, String?>()

    // Destinatario y clave pública en chats 1-a-1
    private var destinatarioIdDirecto: String? = null
    private var clavePublicaDestinatario: String? = null

    private var ultimoSnapshotServidor: List<Mensaje> = emptyList()
    private val pendientes = mutableMapOf<String, Mensaje>()
    private var jobObservar: kotlinx.coroutines.Job? = null
    /** Carga inicial (destinatario y clave E2EE). Enviar sin abrir el chat debe esperarla para cifrar bien. */
    private var jobInicial: kotlinx.coroutines.Job? = null

    init {
        jobInicial = viewModelScope.launch {
            // E2EE solo en chats 1-a-1. Qué chat es directo lo decide el repositorio.
            runCatching {
                destinatarioIdDirecto = repositorioMensajes.obtenerDestinatarioChatDirecto(chatId, usuarioId)
            }
            destinatarioIdDirecto?.let { destId ->
                val destUsuario = runCatching { repositorioUsuarios.obtenerPorId(destId) }.getOrNull()
                val claveRemota = destUsuario?.clavePublicaE2ee
                if (claveRemota != null) cifradorE2ee.guardarClavePublicaConocida(destId, claveRemota, origen = "verificado")
                clavePublicaDestinatario = claveRemota ?: resolverClavePublica(destId)
                if (clavePublicaDestinatario == null) {
                    solicitarClaveIdent(destId)
                }
                clavesPublicasConocidas[destId] = clavePublicaDestinatario
                destUsuario?.nombre?.let { nombresConocidos[destId] = it }
                fotosConocidas[destId] = destUsuario?.foto
                // Cabecera del chat 1-a-1: se publica ya, antes de que llegue el primer mensaje.
                val actual = _estado.value
                if (actual is ChatUiState.Listo) {
                    _estado.value = actual.copy(
                        interlocutor = InterlocutorUi(destId, nombreDe(destId), destUsuario?.foto)
                    )
                }
            }
            reanudar()
        }
        // §2.1 — Observar alertas de cambio de clave TOFU del router mesh.
        // Solo se suscribe si se proporcionó el router (chat DM con cifrado activo).
        messageRouter?.let { router ->
            viewModelScope.launch {
                router.alertaClaveTofu.collect { peerId ->
                    // Mostrar alerta si afecta al peer con quien estamos chateando o si aún no se resuelve el destinatario.
                    val destId = destinatarioIdDirecto
                    if (destId == null || coinciden(peerId, destId)) {
                        _alertaTofuPeerId.value = destId ?: peerId
                    }
                }
            }
            // Observar anuncios IDENT v2 para recibir reactivamente la clave pública E2EE del interlocutor
            viewModelScope.launch {
                router.todosLosPaquetesEntrantes().collect { p ->
                    if (p.contenido.startsWith("[IDENT:v2:")) {
                        val destId = destinatarioIdDirecto ?: return@collect
                        val coincide = coinciden(p.remitenteId, destId)
                        if (coincide) {
                            val partes = p.contenido.removePrefix("[IDENT:v2:").removeSuffix("]").split(":")
                            if (partes.size == 2 && partes[1].isNotBlank()) {
                                val e2eePub = partes[1]
                                clavePublicaDestinatario = e2eePub
                                clavesPublicasConocidas[destId] = e2eePub
                                cifradorE2ee.guardarClavePublicaConocida(destId, e2eePub, "mesh_no_verificada")
                                val actual = _estado.value
                                if (actual is ChatUiState.Listo && actual.errorEnvio != null) {
                                    _estado.value = actual.copy(errorEnvio = null)
                                }
                                if (ultimoSnapshotServidor.isNotEmpty()) {
                                    actualizarMensajes(combinarConPendientes(ultimoSnapshotServidor))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun coinciden(id1: String?, id2: String?): Boolean {
        if (id1 == null || id2 == null) return false
        if (id1.trim().equals(id2.trim(), ignoreCase = true)) return true
        val n1 = id1.replace("-", "").trim().lowercase()
        val n2 = id2.replace("-", "").trim().lowercase()
        if (n1.isNotEmpty() && n1 == n2) return true
        resolverIdentidad?.let { res ->
            val ik1 = res.ikDe(id1)
            val ik2 = res.ikDe(id2)
            if (ik1 != null && ik1.equals(id2, ignoreCase = true)) return true
            if (ik2 != null && ik2.equals(id1, ignoreCase = true)) return true
            val u1 = res.usuarioDe(id1)
            val u2 = res.usuarioDe(id2)
            if (u1 != null && u1.equals(id2, ignoreCase = true)) return true
            if (u2 != null && u2.equals(id1, ignoreCase = true)) return true
        }
        return false
    }

    private fun resolverClavePublica(destId: String): String? {
        val directa = cifradorE2ee.obtenerClavePublicaConocida(destId)
        if (directa != null) return directa
        resolverIdentidad?.let { res ->
            res.ikDe(destId)?.let { ik ->
                val claveIk = cifradorE2ee.obtenerClavePublicaConocida(ik)
                if (claveIk != null) return claveIk
            }
            res.usuarioDe(destId)?.let { uid ->
                val claveUid = cifradorE2ee.obtenerClavePublicaConocida(uid)
                if (claveUid != null) return claveUid
            }
        }
        return null
    }

    private fun solicitarClaveIdent(destId: String) {
        val router = messageRouter ?: return
        viewModelScope.launch {
            val req = com.renea.arete_social.domain.mesh.PaqueteMesh(
                id = "ident-req-${java.util.UUID.randomUUID()}",
                chatId = com.renea.arete_social.domain.model.ProtocoloCanalesMesh.CANAL_IDENT_SYNC,
                remitenteId = usuarioId,
                destinatarioId = destId,
                contenido = "[IDENT_REQ:$destId:$usuarioId]",
                timestamp = System.currentTimeMillis()
            )
            val directo = router.enviarADestino(destId, req)
            if (!directo) router.reenviar(req)
        }
    }

    fun reanudar() {
        if (jobObservar?.isActive == true) return
        jobObservar = viewModelScope.launch {
            repositorioMensajes.observarMensajes(chatId)
                .catch {
                    // Si falla el sondeo en la red, mantenemos los mensajes locales
                }
                .collect { mensajes ->
                    ultimoSnapshotServidor = mensajes
                    pendientes.keys.retainAll { id -> mensajes.none { it.id == id } }
                    actualizarMensajes(combinarConPendientes(mensajes))
                }
        }
    }

    fun pausar() {
        jobObservar?.cancel()
        jobObservar = null
    }

    private fun combinarConPendientes(mensajesServidor: List<Mensaje>): List<Mensaje> {
        val servidorIds = mensajesServidor.map { it.id }.toSet()
        val soloPendientes = pendientes.values.filter { p ->
            p.id !in servidorIds && mensajesServidor.none { s ->
                s.remitenteId == p.remitenteId &&
                s.contenido == p.contenido &&
                kotlin.math.abs(s.timestamp - p.timestamp) < 5_000L
            }
        }
        return (mensajesServidor + soloPendientes)
            .distinctBy { it.id }
            .sortedBy { it.timestamp }
    }

    private suspend fun actualizarMensajes(mensajes: List<Mensaje>) {
        val mensajesUi = mensajes.distinctBy { it.id }.map { mensaje ->
            val esCifrado = runCatching { cifradorE2ee.esCifrado(mensaje.contenido) }.getOrDefault(false)
            val textoVisible = if (esCifrado) {
                val claveContraparte = if (mensaje.remitenteId == usuarioId) {
                    // Mensaje propio: se descifra con la clave del destinatario
                    clavePublicaDestinatario ?: clavePublicaDe(destinatarioIdDirecto ?: "")
                } else {
                    // Mensaje ajeno: se descifra con la clave del remitente
                    clavePublicaDe(mensaje.remitenteId)
                }

                if (claveContraparte != null) {
                    runCatching {
                        cifradorE2ee.descifrar(
                            textoCifrado = mensaje.contenido,
                            clavePublicaContraparteBase64 = claveContraparte,
                            miUsuarioId = usuarioId,
                            remitenteOriginalId = mensaje.remitenteId,
                            destinatarioOriginalId = if (mensaje.remitenteId == usuarioId) {
                                destinatarioIdDirecto ?: ""
                            } else {
                                usuarioId
                            },
                            chatId = mensaje.chatId
                        )
                    }.getOrDefault("[🔒 Mensaje cifrado E2EE]")
                } else {
                    "[🔒 Mensaje cifrado E2EE]"
                }
            } else {
                mensaje.contenido
            }

            // Regalos, avisos de amistad, cifrado no abierto… siempre en texto claro, nunca JSON ni ENC:v3.
            val textoFormateado = TextoMensaje.textoBurbuja(textoVisible, mensaje.remitenteId == usuarioId)

            MensajeUi(
                id = mensaje.id,
                remitenteId = mensaje.remitenteId,
                remitenteNombre = nombreDe(mensaje.remitenteId),
                remitenteFoto = fotoDe(mensaje.remitenteId),
                contenido = textoFormateado,
                timestamp = mensaje.timestamp,
                esPropio = mensaje.remitenteId == usuarioId,
                esE2ee = esCifrado
            )
        }

        val esE2eeActivo = clavePublicaDestinatario != null
        val actual = _estado.value
        _estado.value = if (actual is ChatUiState.Listo) {
            actual.copy(mensajes = mensajesUi, esE2ee = esE2eeActivo)
        } else {
            ChatUiState.Listo(mensajes = mensajesUi, esE2ee = esE2eeActivo)
        }
    }

    private suspend fun clavePublicaDe(usuarioIdRemitente: String): String? {
        val enMemoria = clavesPublicasConocidas[usuarioIdRemitente]
        if (enMemoria != null) return enMemoria

        val resuelta = resolverClavePublica(usuarioIdRemitente)
        if (resuelta != null) {
            clavesPublicasConocidas[usuarioIdRemitente] = resuelta
            return resuelta
        }

        val usuario = runCatching { repositorioUsuarios.obtenerPorId(usuarioIdRemitente) }.getOrNull()
        val remota = usuario?.clavePublicaE2ee
        if (remota != null) {
            clavesPublicasConocidas[usuarioIdRemitente] = remota
            cifradorE2ee.guardarClavePublicaConocida(usuarioIdRemitente, remota, "verificado")
            return remota
        }
        return null
    }

    /**
     * Resuelve nombre y foto con una consulta priorizada:
     * 1. Directorio unificado de pares (ResolverPerfiles / Room + BLE)
     * 2. Repositorio de usuarios (CacheUsuariosLocal / Remoto)
     * 3. Fallback a nombre resuelto en el escaneo BLE
     */
    private suspend fun cargarPerfilRemitente(remitenteId: String) {
        if (nombresConocidos.containsKey(remitenteId) && fotosConocidas.containsKey(remitenteId)) return
        val ultimoFallo = consultasFallidas[remitenteId]
        if (ultimoFallo != null && System.currentTimeMillis() - ultimoFallo < REINTENTO_PERFIL_MS) return

        val perfil = resolverPerfiles?.obtenerPerfil(remitenteId)
        if (perfil != null && perfil.nombre.isNotBlank() && perfil.nombre != "Alguien") {
            consultasFallidas.remove(remitenteId)
            nombresConocidos[remitenteId] = perfil.nombre
            fotosConocidas[remitenteId] = perfil.foto
            return
        }

        val usuario = runCatching { repositorioUsuarios.obtenerPorId(remitenteId) }.getOrNull()
        if (usuario != null && usuario.nombre.isNotBlank()) {
            consultasFallidas.remove(remitenteId)
            nombresConocidos[remitenteId] = usuario.nombre
            fotosConocidas[remitenteId] = usuario.foto
            resolverPerfiles?.registrarInteraccionMensaje(remitenteId, usuario.nombre, usuario.foto)
            return
        }

        val nombreResuelto = resolverPerfiles?.resolverNombre(remitenteId)
        if (nombreResuelto != null && nombreResuelto != "Alguien") {
            consultasFallidas.remove(remitenteId)
            nombresConocidos[remitenteId] = nombreResuelto
            fotosConocidas[remitenteId] = resolverPerfiles?.resolverFoto(remitenteId)
            return
        }

        consultasFallidas[remitenteId] = System.currentTimeMillis()
    }

    private suspend fun nombreDe(remitenteId: String): String {
        cargarPerfilRemitente(remitenteId)
        return nombresConocidos[remitenteId]
            ?: resolverPerfiles?.resolverNombre(remitenteId)
            ?: "Alguien"
    }

    private suspend fun fotoDe(remitenteId: String): String? {
        cargarPerfilRemitente(remitenteId)
        return fotosConocidas[remitenteId]
            ?: resolverPerfiles?.resolverFoto(remitenteId)
    }

    private companion object {
        const val REINTENTO_PERFIL_MS = 30_000L
    }

    /**
     * Envía un mensaje directo sin tener el chat abierto (p. ej. responder al estado de un Michi desde el mapa).
     * Espera a conocer al destinatario y su clave pública para respetar el E2EE, igual que [enviar].
     * Devuelve true si el mensaje se entregó o quedó en cola; false si no se pudo cifrar o enviar.
     */
    suspend fun enviarSinAbrir(contenido: String): Boolean {
        val textoLimpio = contenido.trim()
        if (textoLimpio.isBlank()) return false
        jobInicial?.join()
        val esDirecto = destinatarioIdDirecto != null
        if (esDirecto && clavePublicaDestinatario == null) {
            clavePublicaDestinatario = cifradorE2ee.obtenerClavePublicaConocida(destinatarioIdDirecto!!)
            if (clavePublicaDestinatario != null) {
                clavesPublicasConocidas[destinatarioIdDirecto!!] = clavePublicaDestinatario
            }
        }
        val clavePub = clavePublicaDestinatario
        val contenidoParaEnviar = when {
            clavePub != null -> try {
                cifradorE2ee.cifrar(
                    textoPlano = textoLimpio,
                    clavePublicaDestinatarioBase64 = clavePub,
                    miUsuarioId = usuarioId,
                    destinatarioId = destinatarioIdDirecto ?: "",
                    chatId = chatId
                )
            } catch (e: CifradoE2eeException) {
                return false // nunca degradar a texto plano en silencio
            }
            esDirecto -> return false // En DMs nunca enviar en texto plano sin clave
            else -> textoLimpio
        }
        val enviado = runCatching {
            repositorioMensajes.enviarMensaje(chatId, usuarioId, contenidoParaEnviar)
        }.isSuccess
        if (enviado) {
            runCatching {
                gestorLlamadas?.notificarMensajeEnviado(
                    chatId = chatId,
                    destinatarioId = destinatarioIdDirecto,
                    remitenteNombre = nombreDe(usuarioId),
                    remitenteFoto = null,
                    contenidoPreview = textoLimpio
                )
            }
        }
        return enviado
    }

    fun enviar(contenido: String) {
        val textoLimpio = contenido.trim()
        if (textoLimpio.isBlank()) return
        gestorPerfilMediaBle?.notificarActividadChat()
        val actual = _estado.value
        if (actual !is ChatUiState.Listo) return

        // H-03: Si la carga inicial (destinatario + clave E2EE) aún no terminó,
        // esperar su finalización en lugar de degradar silenciosamente a texto plano.
        if (jobInicial?.isActive == true) {
            viewModelScope.launch {
                jobInicial?.join()
                enviarInterno(textoLimpio)
            }
            return
        }

        val esDirecto = destinatarioIdDirecto != null
        if (esDirecto && clavePublicaDestinatario == null) {
            clavePublicaDestinatario = resolverClavePublica(destinatarioIdDirecto!!)
            if (clavePublicaDestinatario == null) {
                viewModelScope.launch {
                    if (messageRouter != null) {
                        solicitarClaveIdent(destinatarioIdDirecto!!)
                    }
                    val inicio = System.currentTimeMillis()
                    while (clavePublicaDestinatario == null && System.currentTimeMillis() - inicio < 4500L) {
                        kotlinx.coroutines.delay(100)
                        clavePublicaDestinatario = resolverClavePublica(destinatarioIdDirecto!!)
                            ?: clavePublicaDe(destinatarioIdDirecto!!)
                    }
                    enviarInterno(textoLimpio)
                }
                return
            }
        }

        enviarInterno(textoLimpio)
    }

    private fun enviarInterno(textoLimpio: String) {
        val actual = _estado.value
        if (actual !is ChatUiState.Listo) return

        val esDirecto = destinatarioIdDirecto != null
        if (esDirecto && clavePublicaDestinatario == null) {
            clavePublicaDestinatario = resolverClavePublica(destinatarioIdDirecto!!)
            if (clavePublicaDestinatario != null) {
                clavesPublicasConocidas[destinatarioIdDirecto!!] = clavePublicaDestinatario
            }
        }
        val clavePub = clavePublicaDestinatario
        val contenidoParaEnviar = when {
            clavePub != null -> try {
                cifradorE2ee.cifrar(
                    textoPlano = textoLimpio,
                    clavePublicaDestinatarioBase64 = clavePub,
                    miUsuarioId = usuarioId,
                    destinatarioId = destinatarioIdDirecto ?: "",
                    chatId = chatId
                )
            } catch (e: CifradoE2eeException) {
                // NUNCA degradar a texto plano en silencio: el usuario cree que va cifrado.
                _estado.value = actual.copy(errorEnvio = "No se pudo cifrar el mensaje; NO se envió. Inténtalo de nuevo.")
                return
            }
            esDirecto -> {
                _estado.value = actual.copy(errorEnvio = "Intercambiando clave segura con esta persona. Por favor reintenta en unos segundos.")
                return
            }
            else -> textoLimpio
        }
        if (actual.errorEnvio != null) _estado.value = actual.copy(errorEnvio = null)

        val ahora = System.currentTimeMillis()
        val nuevoMensajeId = UUID.randomUUID().toString()

        // Eco local inmediato: guarda en pendientes con el ID definitivo
        val pendiente = Mensaje(
            id = nuevoMensajeId,
            chatId = chatId,
            remitenteId = usuarioId,
            contenido = contenidoParaEnviar,
            timestamp = ahora
        )
        pendientes[pendiente.id] = pendiente

        // Notificar en vivo al destinatario a través del módulo táctico
        viewModelScope.launch {
            gestorLlamadas?.notificarMensajeEnviado(
                chatId = chatId,
                destinatarioId = destinatarioIdDirecto,
                remitenteNombre = nombreDe(usuarioId),
                remitenteFoto = null,
                contenidoPreview = textoLimpio
            )
        }

        viewModelScope.launch {
            actualizarMensajes(combinarConPendientes(ultimoSnapshotServidor))
            _estado.value = (_estado.value as? ChatUiState.Listo)?.copy(enviando = true) ?: _estado.value

            val resultado = runCatching {
                repositorioMensajes.enviarMensaje(
                    chatId = chatId,
                    remitenteId = usuarioId,
                    contenido = contenidoParaEnviar,
                    idMensaje = nuevoMensajeId,
                    timestamp = ahora
                )
            }
            pendientes.remove(nuevoMensajeId)
            resultado.getOrNull()?.let { real ->
                if (ultimoSnapshotServidor.none { it.id == real.id }) {
                    pendientes[real.id] = real
                }
            }

            actualizarMensajes(combinarConPendientes(ultimoSnapshotServidor))
            _estado.value = (_estado.value as? ChatUiState.Listo)?.copy(enviando = false) ?: _estado.value
        }
    }

    // ========================================================
    // Moderación — solo aplica en chats 1-a-1
    // ========================================================

    /** Bloquea al interlocutor de este chat. No aplica en salas o chat general. */
    fun bloquearInterlocutor() {
        val destId = destinatarioIdDirecto ?: return
        val repo = repositorioModeracion ?: return
        viewModelScope.launch {
            runCatching { repo.bloquearUsuario(destId) }
            val aviso = if (runCatching { repo.estaBloqueado(destId) }.getOrDefault(false))
                "Usuario bloqueado." else "No se pudo bloquear. Intenta de nuevo."
            _estado.value = (_estado.value as? ChatUiState.Listo)
                ?.copy(avisoModeracion = aviso) ?: _estado.value
        }
    }

    /** Envía un reporte de conversación contra el interlocutor. */
    fun enviarReporteConversacion(motivo: MotivoReporte, descripcion: String) {
        val destId = destinatarioIdDirecto ?: return
        val repo = repositorioModeracion ?: return
        viewModelScope.launch {
            val resultado = runCatching {
                repo.enviarReporte(
                    reportadoId = destId,
                    tipo = TipoReporte.MENSAJE,
                    referenciaId = chatId,
                    motivo = motivo,
                    descripcion = descripcion
                )
            }
            val aviso = if (resultado.isSuccess) "Reporte enviado. Lo revisaremos en 24 h."
            else "No se pudo enviar el reporte. Intenta de nuevo."
            _estado.value = (_estado.value as? ChatUiState.Listo)
                ?.copy(avisoModeracion = aviso) ?: _estado.value
        }
    }

    /** Limpia el aviso de moderación una vez que la UI lo ha mostrado. */
    fun consumirAvisoModeracion() {
        _estado.value = (_estado.value as? ChatUiState.Listo)
            ?.copy(avisoModeracion = null) ?: _estado.value
    }

    /** Elimina un mensaje propio del chat (actualización optimista inmediata en UI y borrado en repositorio). */
    fun eliminarMensaje(mensajeId: String) {
        val actual = _estado.value
        if (actual !is ChatUiState.Listo) return

        pendientes.remove(mensajeId)
        ultimoSnapshotServidor = ultimoSnapshotServidor.filter { it.id != mensajeId }
        viewModelScope.launch {
            actualizarMensajes(combinarConPendientes(ultimoSnapshotServidor))
            repositorioMensajes.eliminarMensaje(mensajeId)
        }
    }

    /** Elimina la conversación entera con todos sus mensajes e historial. */
    fun eliminarConversacion(onCompletado: () -> Unit = {}) {
        viewModelScope.launch {
            repositorioMensajes.eliminarConversacion(chatId)
            pendientes.clear()
            ultimoSnapshotServidor = emptyList()
            actualizarMensajes(emptyList())
            onCompletado()
        }
    }

    /** Elimina cualquier conversación por su chatId directamente (ej. desde lista de Activos). */
    fun eliminarConversacionDirecta(chatIdABorrar: String, onCompletado: () -> Unit = {}) {
        viewModelScope.launch {
            repositorioMensajes.eliminarConversacion(chatIdABorrar)
            if (chatIdABorrar == chatId) {
                pendientes.clear()
                ultimoSnapshotServidor = emptyList()
                actualizarMensajes(emptyList())
            }
            onCompletado()
        }
    }
}
