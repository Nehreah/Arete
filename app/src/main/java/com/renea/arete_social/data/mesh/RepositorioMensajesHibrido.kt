package com.renea.arete_social.data.mesh

import com.renea.arete_social.feature.mensajeria.TextoMensaje
import android.util.Log
import com.renea.arete_social.data.local.outbox.GestorOutboxLocal
import com.renea.arete_social.data.local.outbox.MensajeOutbox
import com.renea.arete_social.domain.repository.ID_CHAT_GENERAL
import com.renea.arete_social.domain.mesh.MessageRouter
import com.renea.arete_social.domain.mesh.PaqueteMesh
import com.renea.arete_social.domain.model.Chat
import com.renea.arete_social.domain.model.Mensaje
import com.renea.arete_social.domain.model.TipoChat
import com.renea.arete_social.domain.repository.RepositorioMensajes
import com.renea.arete_social.domain.util.toHex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val PREFIJO_LOCAL_DM = "local-dm-"
private const val MAX_PENDIENTES_REPUBLICAR = 50

/**
 * Repositorio de mensajería: red Mesh (BLE) + Wi-Fi LAN + historial/Outbox local (SQLite).
 *
 * ## Modelo v2 (simplificado)
 * - **El historial local (SQLite) es la única fuente que alimenta la UI.** Un receptor
 *   global persiste TODO paquete Mesh dirigido a mí (o de un chat que estoy siguiendo) en
 *   [GestorOutboxLocal]; [observarMensajes] observa esa tabla.
 * - **Un chat es directo solo si consta como directo** (mapa local persistido).
 * - Ids de chat: Mesh usa `local-dm-<u1_8>-<u2_8>` (determinista por par).
 */
class RepositorioMensajesHibrido(
    private val messageRouter: MessageRouter,
    private val hayInternet: () -> Boolean = { true },
    private val miUsuarioIdProveedor: () -> String? = { null },
    private val gestorOutboxLocal: GestorOutboxLocal? = null,
    private val cifradorE2ee: com.renea.arete_social.domain.crypto.CifradorE2ee? = null,
    private val esSoloLocal: () -> Boolean = { true },
    private val pinesIdentidad: com.renea.arete_social.data.crypto.PinesIdentidad? = null,
    private val resolverIdentidad: com.renea.arete_social.domain.repository.ResolverIdentidad? = null,
    private val gestorIdentidad: com.renea.arete_social.data.crypto.GestorIdentidadEcdsa? = null
) : RepositorioMensajes {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutexDrenaje = Mutex()
    private val ultimoIdentEnviadoPorPeer = ConcurrentHashMap<String, Long>()

    /** Chats (ids UI y ids Mesh) que el usuario abrió: sus mensajes de grupo se persisten aunque la pantalla se cierre. */
    private val chatsSeguidos: MutableSet<String> = ConcurrentHashMap.newKeySet()

    @Volatile
    var onMensajeNuevoRecibido: ((remitenteId: String, chatId: String, contenido: String) -> Unit)? = null

    private fun coincidenIds(id1: String?, id2: String?): Boolean {
        if (id1 == null || id2 == null) return false
        return id1.trim().equals(id2.trim(), ignoreCase = true)
    }

    init {
        // Receptor global: ningún paquete se pierde por estar la pantalla del chat cerrada.
        scope.launch {
            messageRouter.todosLosPaquetesEntrantes().collect { paquete ->
                runCatching { persistirPaqueteEntrante(paquete) }
            }
        }
        // Al aparecer un peer BLE, republicar por Mesh lo que sigue sin sincronizar.
        scope.launch {
            messageRouter.peersConectados.collect { n ->
                if (n > 0) runCatching { republicarPendientesPorMesh() }
            }
        }
    }

    private suspend fun persistirPaqueteEntrante(p: PaqueteMesh) {
        if (p.contenido.startsWith("[IDENT_REQ:") && p.contenido.endsWith("]")) {
            val partes = p.contenido.removePrefix("[IDENT_REQ:").removeSuffix("]").split(":")
            if (partes.size >= 2) {
                val destId = partes[0]
                val requesterId = partes[1]
                val miId = miUsuarioIdProveedor()
                val coincide = coincidenIds(destId, miId) ||
                    (miId != null && resolverIdentidad?.esMio(destId) == true)
                if (coincide && miId != null) {
                    val pubFirma = gestorIdentidad?.obtenerClavePublicaBase64()
                    val pubE2ee = cifradorE2ee?.obtenerClavePublicaPropia(miId)
                    if (pubFirma != null && pubE2ee != null) {
                        val respuesta = PaqueteMesh(
                            id = "ident-resp-${java.util.UUID.randomUUID()}",
                            chatId = com.renea.arete_social.domain.model.ProtocoloCanalesMesh.CANAL_IDENT_SYNC,
                            remitenteId = miId,
                            destinatarioId = requesterId,
                            contenido = "[IDENT:v2:$pubFirma:$pubE2ee]",
                            timestamp = System.currentTimeMillis()
                        )
                        scope.launch {
                            val directo = messageRouter.enviarADestino(requesterId, respuesta)
                            if (!directo) messageRouter.reenviar(respuesta)
                        }
                    }
                }
            }
            return
        }

        if (p.contenido.startsWith("[IDENT:v2:") && p.contenido.endsWith("]")) {
            val partes = p.contenido.removePrefix("[IDENT:v2:").removeSuffix("]").split(":")
            if (partes.size == 2) {
                val firmaPub = partes[0]
                val e2eePub = partes[1]

                // 1. Clave de firma en pines
                if (firmaPub.isNotBlank()) {
                    pinesIdentidad?.fijar(p.remitenteId, firmaPub, com.renea.arete_social.data.crypto.EstadoVerificacionIdentidad.NO_VERIFICADO)
                }

                // 2. Clave E2EE en CifradorE2ee
                if (e2eePub.isNotBlank()) {
                    cifradorE2ee?.let { cifrador ->
                        cifrador.guardarClavePublicaConocida(p.remitenteId, e2eePub, "mesh_no_verificada")
                        // Sincronizar alias conocidos (IK <-> UUID)
                        resolverIdentidad?.let { res ->
                            res.ikDe(p.remitenteId)?.let { ik ->
                                cifrador.guardarClavePublicaConocida(ik, e2eePub, "mesh_no_verificada")
                            }
                            res.usuarioDe(p.remitenteId)?.let { uid ->
                                cifrador.guardarClavePublicaConocida(uid, e2eePub, "mesh_no_verificada")
                            }
                        }
                    }
                }

                // 3. Respuesta recíproca automática: garantiza que el peer remitente también obtenga nuestra clave E2EE
                val miId = miUsuarioIdProveedor()
                if (miId != null && !coincidenIds(p.remitenteId, miId)) {
                    val ahora = System.currentTimeMillis()
                    val ultimoEnvio = ultimoIdentEnviadoPorPeer[p.remitenteId] ?: 0L
                    if (ahora - ultimoEnvio > 15_000L) {
                        ultimoIdentEnviadoPorPeer[p.remitenteId] = ahora
                        val pubFirma = gestorIdentidad?.obtenerClavePublicaBase64()
                        val pubE2ee = cifradorE2ee?.obtenerClavePublicaPropia(miId)
                        if (pubFirma != null && pubE2ee != null) {
                            val respuesta = PaqueteMesh(
                                id = "ident-resp-${java.util.UUID.randomUUID()}",
                                chatId = com.renea.arete_social.domain.model.ProtocoloCanalesMesh.CANAL_IDENT_SYNC,
                                remitenteId = miId,
                                destinatarioId = p.remitenteId,
                                contenido = "[IDENT:v2:$pubFirma:$pubE2ee]",
                                timestamp = ahora
                            )
                            scope.launch {
                                val directo = messageRouter.enviarADestino(p.remitenteId, respuesta)
                                if (!directo) messageRouter.reenviar(respuesta)
                            }
                        }
                    }
                }
            }
            return
        }

        // Ignorar formato legado IDENT v1 sin modificar claves
        if (p.contenido.startsWith("[IDENT:") && p.contenido.endsWith("]")) {
            Log.d("RepositorioHibrido", "Ignorando paquete IDENT legado v1 de ${p.remitenteId}")
            return
        }

        // Canales dedicados al plano de control y sincronización P2P: nunca son mensajes de chat.
        if (com.renea.arete_social.domain.model.ProtocoloCanalesMesh.esCanalP2P(p.chatId)) return
        // Peticiones/respuestas de perfil por Bluetooth: las atiende GestorPerfilBle, no son mensajes.
        if (p.chatId == com.renea.arete_social.domain.model.ProtocoloPerfilBle.CHAT_ID) return
        // Pases de descubrimiento (lugares y eventos compartidos por cercanía): los atiende GestorPasesBle, no son mensajes.
        if (p.chatId == com.renea.arete_social.domain.model.ProtocoloPaseBle.CHAT_ID) return
        // Señales de llamadas y notificaciones de timbre: las procesa GestorLlamadasYAudio, nunca son mensajes de chat.
        if (p.chatId == "senales-mesh" || p.contenido.startsWith("[SENAL:")) return

        // Paquetes internos de la app (JSON con "type"): solo regalos y avisos de amistad se
        // conservan como mensaje (se ven en texto amable). El resto son datos técnicos: nunca
        // deben aparecer como mensajes ni crear chats fantasma.
        val esProtocolo = TextoMensaje.esProtocolo(p.contenido)
        if (esProtocolo && !TextoMensaje.protocoloVisibleEnChat(p.contenido)) return
        if (esProtocolo && p.destinatarioId == null) return

        val outbox = gestorOutboxLocal ?: return
        val miId = miUsuarioIdProveedor() ?: return
        if (coincidenIds(p.remitenteId, miId)) return                         // eco propio: ya está en la Outbox

        val esDirecto = p.destinatarioId != null

        // H-04: Para paquetes directos (DM), derivar SIEMPRE el chatDestino local determinístico
        // a partir de los dos IDs, en vez de confiar en p.chatId que puede ser manipulado.
        val chatDestino = if (esDirecto || esProtocolo) localChatId(miId, p.remitenteId) else p.chatId

        if (esDirecto) {
            if (!coincidenIds(p.destinatarioId, miId)) return                  // dirigido a otro: solo se retransmite
            outbox.registrarChatDirecto(chatDestino, miId, p.remitenteId)
        } else {
            // Difusión (general/sala/evento…): solo si el usuario sigue ese chat, para no llenar
            // el disco con mensajes de salas ajenas que solo estamos retransmitiendo.
            if (p.chatId != ID_CHAT_GENERAL && p.chatId !in chatsSeguidos) return
        }

        outbox.guardarRecibido(
            MensajeOutbox(
                id = p.id,
                chatId = chatDestino,
                remitenteId = p.remitenteId,
                destinatarioId = p.destinatarioId,               // null en difusión: NUNCA "yo"
                contenido = p.contenido,
                timestamp = p.timestamp
            )
        )
        onMensajeNuevoRecibido?.invoke(p.remitenteId, chatDestino, p.contenido)
    }

    /** Olvida el estado de sesión en memoria (chats seguidos). Se llama al cerrar sesión. */
    fun reiniciarSesion() {
        chatsSeguidos.clear()
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Deriva el chatId Mesh determinístico y único a partir de dos IDs de usuario. */
    private fun localChatId(uid1: String, uid2: String): String {
        val u1 = uid1.trim().lowercase()
        val u2 = uid2.trim().lowercase()
        val primero = if (u1 < u2) u1 else u2
        val segundo = if (u1 < u2) u2 else u1
        val hash = java.security.MessageDigest.getInstance("SHA-256")
            .digest("$primero:$segundo".toByteArray(Charsets.UTF_8))
            .toHex()
            .take(16)
        return "$PREFIJO_LOCAL_DM$hash"
    }

    /** Resuelve el id del interlocutor a partir de los mensajes guardados en el chat local. */
    private suspend fun resolverPorPrefijo(chatId: String, miId: String): String? {
        return gestorOutboxLocal?.obtenerMensajesPorChat(chatId)?.firstNotNullOfOrNull { m ->
            when {
                m.remitenteId != miId -> m.remitenteId
                m.destinatarioId != null && m.destinatarioId != miId -> m.destinatarioId
                else -> null
            }
        }
    }

    // ── chats ────────────────────────────────────────────────────────────────

    override suspend fun crearChat(tipo: TipoChat): Chat =
        Chat(id = UUID.randomUUID().toString(), tipo = tipo)

    override suspend fun obtenerOCrearChatDirecto(usuarioId1: String, usuarioId2: String): String {
        val miId = miUsuarioIdProveedor() ?: usuarioId1
        val otro = if (usuarioId1 == miId) usuarioId2 else usuarioId1
        val local = localChatId(usuarioId1, usuarioId2)
        gestorOutboxLocal?.registrarChatDirecto(local, miId, otro)
        return local
    }

    override suspend fun buscarChatDirectoExistente(usuarioId1: String, usuarioId2: String): String? {
        val local = localChatId(usuarioId1, usuarioId2)
        val tieneLocal = gestorOutboxLocal?.obtenerMensajesPorChat(local)?.isNotEmpty() == true
        return if (tieneLocal) local else null
    }

    override suspend fun obtenerDestinatarioChatDirecto(chatId: String, usuarioIdActual: String): String? {
        if (chatId == ID_CHAT_GENERAL) return null
        val outbox = gestorOutboxLocal

        // 1. Mapa local persistido (sin red, sin adivinar).
        outbox?.obtenerOtroDeChatDirecto(chatId, usuarioIdActual)?.let { return it }

        // 2. Historial local: solo filas con destinatario (DMs). Las de grupo tienen destinatario NULL.
        outbox?.obtenerDestinatarioDirecto(chatId, usuarioIdActual)?.let { return it }

        // 3. `local-dm-…`: resolver por prefijo o derivación directa
        if (chatId.startsWith(PREFIJO_LOCAL_DM)) return resolverPorPrefijo(chatId, usuarioIdActual)

        return null
    }

    override suspend fun obtenerTodosMisChatsDirectos(usuarioIdActual: String): Map<String, String> {
        val mapa = mutableMapOf<String, String>()
        val outbox = gestorOutboxLocal
        if (outbox != null) {
            outbox.obtenerChatsDirectosLocales(usuarioIdActual).forEach { (chat, otro) ->
                if (!mapa.containsKey(chat)) mapa[chat] = otro
            }
            // Historial de versiones anteriores: solo mensajes con destinatario (DMs), nunca grupos.
            for (msg in outbox.obtenerTodosLosMensajes()) {
                val dest = msg.destinatarioId ?: continue
                val otro = if (msg.remitenteId == usuarioIdActual) dest else msg.remitenteId
                if (otro != usuarioIdActual && !mapa.containsKey(msg.chatId)) mapa[msg.chatId] = otro
            }
        }
        return mapa.entries
            .groupBy({ it.value }, { it.key })
            .map { (otro, chats) -> (chats.firstOrNull { !it.startsWith(PREFIJO_LOCAL_DM) } ?: chats.first()) to otro }
            .toMap()
    }

    override suspend fun obtenerUltimoMensajeLocal(chatId: String, usuarioIdActual: String): Mensaje? {
        val outbox = gestorOutboxLocal ?: return null
        val otro = outbox.obtenerOtroDeChatDirecto(chatId, usuarioIdActual)
        val candidatos = setOfNotNull(chatId, otro?.let { localChatId(usuarioIdActual, it) })
        return candidatos.mapNotNull { outbox.obtenerUltimoMensaje(it) }
            .maxByOrNull { it.timestamp }?.toDomain()
    }

    override suspend fun obtenerUltimosMensajes(chatIds: List<String>): Map<String, Mensaje> {
        if (chatIds.isEmpty()) return emptyMap()
        val miId = miUsuarioIdProveedor() ?: ""
        val resultado = mutableMapOf<String, Mensaje>()

        for (cId in chatIds) {
            val localMsg = obtenerUltimoMensajeLocal(cId, miId)
            if (localMsg != null) {
                resultado[cId] = localMsg
            }
        }
        return resultado
    }

    // ── Outbox ───────────────────────────────────────────────────────────────

    /**
     * Drena la Outbox republicando paquetes pendientes a la red Mesh.
     */
    suspend fun drenarOutbox() {
        republicarPendientesPorMesh()
    }

    /** Store-and-forward PERSISTENTE: reemite por Mesh lo pendiente (sobrevive a reinicios del proceso). */
    suspend fun republicarPendientesPorMesh() {
        val outbox = gestorOutboxLocal ?: return
        val miId = miUsuarioIdProveedor() ?: return
        outbox.obtenerPendientes()
            .filter { it.remitenteId == miId && it.contenido.length <= PaqueteMesh.MAX_CONTENIDO_MESH_CHARS }
            .takeLast(MAX_PENDIENTES_REPUBLICAR)
            .forEach { item ->
                val meshChat = item.destinatarioId?.let { localChatId(miId, it) } ?: item.chatId
                messageRouter.reenviar(
                    PaqueteMesh(
                        id = item.id,
                        chatId = meshChat,
                        remitenteId = item.remitenteId,
                        destinatarioId = item.destinatarioId,
                        contenido = item.contenido,
                        timestamp = item.timestamp
                    )
                )
            }
    }

    // ── enviar ───────────────────────────────────────────────────────────────

    override suspend fun enviarMensaje(
        chatId: String,
        remitenteId: String,
        contenido: String,
        idMensaje: String?,
        timestamp: Long?
    ): Mensaje {
        val mensajeId = idMensaje ?: UUID.randomUUID().toString()
        val ahora = timestamp ?: System.currentTimeMillis()

        // null para general/salas/eventos: NUNCA se inventa un destinatario.
        val destinatario = obtenerDestinatarioChatDirecto(chatId, remitenteId)
        chatsSeguidos.add(chatId)

        // H-03 (defensa en profundidad): si es chat directo, el contenido DEBE ir cifrado
        // (salvo si es paquete de protocolo interno de la app)
        if (destinatario != null && !TextoMensaje.esProtocolo(contenido)) {
            val cifrado = cifradorE2ee?.esCifrado(contenido) == true
            if (!cifrado) {
                Log.e("RepositorioHibrido", "H-03: Intento de enviar DM sin cifrar a $destinatario en chat $chatId bloqueado.")
                throw IllegalStateException("DM sin cifrar bloqueado por seguridad")
            }
        }

        // 1. Persistir primero (garantía de no-pérdida).
        val outboxItem = MensajeOutbox(
            id = mensajeId,
            chatId = chatId,
            remitenteId = remitenteId,
            destinatarioId = destinatario,
            contenido = contenido,
            timestamp = ahora
        )
        gestorOutboxLocal?.guardarMensaje(outboxItem)

        // 2. Mesh.
        val meshChatId = destinatario?.let { localChatId(remitenteId, it) } ?: chatId
        val paquete = PaqueteMesh(
            id = mensajeId,
            chatId = meshChatId,
            remitenteId = remitenteId,
            destinatarioId = destinatario,
            contenido = contenido,
            ttl = PaqueteMesh.TTL_POR_DEFECTO,
            timestamp = ahora
        )
        // reenviar(): firma y difunde a los peers. enrutar(): marca el id como visto (evita que
        // vuelva a entrar por retransmisión) y entrega a suscriptores locales.
        // Los contenidos grandes (notas de voz en base64, cientos de KB) NO viajan por BLE: serían
        // miles de fragmentos reenviados con TTL 7 a toda la malla. Van solo por Wi-Fi LAN.
        // Un `local-dm-…` cuyo otro participante no se pudo resolver tampoco sale por Mesh: sin
        // destinatario iría como difusión (texto que era privado, retransmitido a toda la malla).
        val dmSinResolver = chatId.startsWith(PREFIJO_LOCAL_DM) && destinatario == null
        if (!dmSinResolver && contenido.length <= PaqueteMesh.MAX_CONTENIDO_MESH_CHARS) {
            messageRouter.reenviar(paquete)
            messageRouter.enrutar(paquete)
        }

        return outboxItem.toDomain()
    }

    // ── observar ─────────────────────────────────────────────────────────────

    override fun observarMensajes(chatId: String): Flow<List<Mensaje>> = channelFlow {
        val mapa = ConcurrentHashMap<String, Mensaje>()
        suspend fun emitir() = send(mapa.values.sortedBy { it.timestamp })
        send(emptyList())

        val miId = miUsuarioIdProveedor()
        val otroId = if (miId != null) runCatching { obtenerDestinatarioChatDirecto(chatId, miId) }.getOrNull() else null
        val meshChatId = when {
            chatId.startsWith(PREFIJO_LOCAL_DM) -> chatId
            otroId != null && miId != null -> localChatId(miId, otroId)
            else -> chatId
        }
        // Registrar ANTES de suscribirse: el receptor global ya guarda los mensajes de grupo de este chat.
        chatsSeguidos.add(chatId)
        chatsSeguidos.add(meshChatId)
        val chatsABuscar = setOf(chatId, meshChatId)

        val outbox = gestorOutboxLocal
        if (outbox != null) {
            launch {
                outbox.purgarAntiguos(48)
                // StateFlow: emite el valor actual al suscribirse (= carga inicial) y luego cada cambio.
                outbox.outboxCambios.collect {
                    for (c in chatsABuscar) outbox.obtenerMensajesPorChat(c)
                        .filter { !TextoMensaje.esProtocolo(it.contenido) || TextoMensaje.protocoloVisibleEnChat(it.contenido) }
                        .forEach { mapa[it.id] = it.toDomain() }
                    emitir()
                }
            }
        } else {
            // Sin persistencia local (tests): escuchar la Mesh en vivo.
            launch {
                messageRouter.paquetesEntrantes(meshChatId).collect { p ->
                    mapa[p.id] = Mensaje(p.id, chatId, p.remitenteId, p.contenido, p.timestamp)
                    emitir()
                }
            }
        }
    }

    override suspend fun eliminarMensaje(mensajeId: String): Boolean {
        gestorOutboxLocal?.eliminarMensaje(mensajeId)
        return true
    }

    override suspend fun eliminarConversacion(chatId: String): Boolean {
        val miId = miUsuarioIdProveedor()
        val otro = if (miId != null) obtenerDestinatarioChatDirecto(chatId, miId) else null
        val altChatId = if (miId != null && otro != null) localChatId(miId, otro) else null
        val borrado = gestorOutboxLocal?.eliminarConversacion(chatId, altChatId) ?: false
        chatsSeguidos.remove(chatId)
        altChatId?.let { chatsSeguidos.remove(it) }
        return borrado
    }
}
