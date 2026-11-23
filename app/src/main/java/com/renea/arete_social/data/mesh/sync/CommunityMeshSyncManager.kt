package com.renea.arete_social.data.mesh.sync

import android.annotation.SuppressLint
import android.util.Base64
import com.renea.arete_social.data.community.CommunityAuthority
import com.renea.arete_social.data.community.CommunityLedger
import com.renea.arete_social.data.local.room.AppLocalDatabase
import com.renea.arete_social.data.local.room.entity.CommunityEntity
import com.renea.arete_social.data.local.room.entity.CommunityMemberEntity
import com.renea.arete_social.data.local.room.entity.LedgerOperationEntity
import com.renea.arete_social.data.local.room.entity.PeerProfileEntity
import com.renea.arete_social.data.protocol.OperationEnvelope
import com.renea.arete_social.data.protocol.OperationValidator
import com.renea.arete_social.domain.mesh.MessageRouter
import com.renea.arete_social.domain.mesh.PaqueteMesh
import com.renea.arete_social.data.community.CommunityRepositoryLocal
import com.renea.arete_social.domain.util.SyncLogger
import com.renea.arete_social.domain.util.toHex
import com.renea.arete_social.domain.util.hexToByteArray
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import com.renea.arete_social.feature.comunidades.PublicacionMuro
import com.renea.arete_social.feature.comunidades.data.GestorMuroComunidades
import java.util.UUID

/**
 * Gestor de sincronización de comunidades sobre la red mallada (Mesh) P2P.
 *
 * Utiliza [CommunitySyncProtocol] y empaqueta las tramas como [PaqueteMesh] con
 * identificador de canal "community:sync:<communityIdHex>".
 */
@SuppressLint("NewApi")
class CommunityMeshSyncManager(
    private val database: AppLocalDatabase,
    private val messageRouter: MessageRouter,
    private val miUsuarioIdProveedor: () -> String?,
    private val communityRepositoryLocal: CommunityRepositoryLocal? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val json = Json { ignoreUnknownKeys = true }

    private val _eventosSincronizacion = MutableSharedFlow<SyncEvent>(replay = 1, extraBufferCapacity = 64)
    val eventosSincronizacion: SharedFlow<SyncEvent> = _eventosSincronizacion.asSharedFlow()

    sealed class SyncEvent {
        data class ResumenEnviado(val communityIdHex: String, val ownerHeadSeq: Long) : SyncEvent()
        data class OperacionesRecibidas(val communityIdHex: String, val cantidad: Int) : SyncEvent()
        data class MiembrosActualizados(val communityIdHex: String) : SyncEvent()
        data class PublicacionesActualizadas(val communityIdHex: String) : SyncEvent()
        data class ErrorSincronizacion(val communityIdHex: String, val mensaje: String) : SyncEvent()
    }

    companion object {
        private const val TAG = "CommunityMeshSync"
        const val CHAT_PREFIX = "community:sync:"
        const val CHAT_DISCOVER = "community:sync:discover"
        const val PREFIX_DISCOVER = "COMM_SYNC_DISCOVER"
        const val PREFIX_SUMMARY = "COMM_SYNC_SUMMARY:"
        const val PREFIX_REQ = "COMM_SYNC_REQ:"
        const val PREFIX_RESP = "COMM_SYNC_RESP:"
        const val PREFIX_MEMBER_JOIN = "COMM_SYNC_JOIN:"
        const val PREFIX_MEMBER_LEAVE = "COMM_SYNC_LEAVE:"
        const val PREFIX_MEMBERS_SYNC = "COMM_SYNC_MEMBERS:"
        const val PREFIX_POST_NEW = "COMM_SYNC_POST:"
        const val PREFIX_POSTS_SYNC = "COMM_SYNC_POSTS:"
        const val PREFIX_POST_LIKE = "COMM_SYNC_LIKE:"
        private const val CHUNK_SIZE_OPERACIONES = 5
    }

    /**
     * Anuncia todas las comunidades locales soberanas para que los pares cercanos las sincronicen,
     * e incluye la sincronización de miembros activos y publicaciones de la gaceta.
     */
    fun anunciarTodasLasComunidades() {
        scope.launch {
            runCatching {
                val comunidades = withContext(ioDispatcher) {
                    database.communityDao().getAllCommunities()
                }
                for (c in comunidades) {
                    val rawHex = c.communityIdHex
                    val clean = if (rawHex.length % 2 != 0) "0$rawHex" else rawHex
                    val bytes = ByteArray(clean.length / 2) { i ->
                        clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                    }
                    emitirResumenInventario(bytes)
                    emitirTodosLosMiembros(c.communityIdHex)
                    emitirTodasLasPublicaciones(c.communityIdHex)
                }
            }
        }
    }

    /**
     * Emite la adhesión o actualización de estado de un miembro de comunidad a toda la red mesh.
     */
    fun emitirUnionMiembro(
        communityIdHex: String,
        authorIkHex: String,
        role: String = "MEMBER",
        status: String = "ACTIVE",
        nombre: String? = null
    ) {
        val remitenteId = miUsuarioIdProveedor() ?: return
        scope.launch {
            runCatching {
                val nombreLimpio = nombre?.replace("|", " ")?.replace(";", " ") ?: ""
                val paquete = PaqueteMesh(
                    id = UUID.randomUUID().toString(),
                    chatId = "$CHAT_PREFIX$communityIdHex",
                    remitenteId = remitenteId,
                    contenido = "$PREFIX_MEMBER_JOIN$communityIdHex|$authorIkHex|$role|$status|$nombreLimpio"
                )
                messageRouter.reenviar(paquete)
            }
        }
    }

    /**
     * Emite la salida o desactivación de un miembro de comunidad a toda la red mesh.
     */
    fun emitirSalidaMiembro(communityIdHex: String, authorIkHex: String) {
        val remitenteId = miUsuarioIdProveedor() ?: return
        scope.launch {
            runCatching {
                val paquete = PaqueteMesh(
                    id = UUID.randomUUID().toString(),
                    chatId = "$CHAT_PREFIX$communityIdHex",
                    remitenteId = remitenteId,
                    contenido = "$PREFIX_MEMBER_LEAVE$communityIdHex|$authorIkHex"
                )
                messageRouter.reenviar(paquete)
            }
        }
    }

    /**
     * Emite la lista completa de miembros activos de una comunidad a los pares cercanos.
     */
    fun emitirTodosLosMiembros(communityIdHex: String, destinatarioId: String? = null) {
        val remitenteId = miUsuarioIdProveedor() ?: return
        scope.launch {
            runCatching {
                val miembros = withContext(ioDispatcher) {
                    database.communityDao().getActiveMembers(communityIdHex)
                }
                if (miembros.isNotEmpty()) {
                    val data = miembros.joinToString(";") { "${it.authorIkHex},${it.role},${it.status}" }
                    val paquete = PaqueteMesh(
                        id = UUID.randomUUID().toString(),
                        chatId = "$CHAT_PREFIX$communityIdHex",
                        remitenteId = remitenteId,
                        destinatarioId = destinatarioId,
                        contenido = "$PREFIX_MEMBERS_SYNC$communityIdHex|$data"
                    )
                    if (destinatarioId != null) {
                        val enviado = messageRouter.enviarADestino(destinatarioId, paquete)
                        if (!enviado) messageRouter.reenviar(paquete)
                    } else {
                        messageRouter.reenviar(paquete)
                    }
                }
            }
        }
    }

    /**
     * Emite una nueva publicación del muro/gaceta a todos los pares de la comunidad.
     */
    fun emitirPublicacion(communityIdHex: String, publicacion: PublicacionMuro) {
        val remitenteId = miUsuarioIdProveedor() ?: return
        scope.launch {
            runCatching {
                val jsonPub = json.encodeToString(PublicacionMuro.serializer(), publicacion)
                val paquete = PaqueteMesh(
                    id = UUID.randomUUID().toString(),
                    chatId = "$CHAT_PREFIX$communityIdHex",
                    remitenteId = remitenteId,
                    contenido = "$PREFIX_POST_NEW$communityIdHex|$jsonPub"
                )
                messageRouter.reenviar(paquete)
            }
        }
    }

    /**
     * Emite todas las publicaciones del muro/gaceta guardadas localmente para sincronización.
     */
    fun emitirTodasLasPublicaciones(communityIdHex: String, destinatarioId: String? = null) {
        val remitenteId = miUsuarioIdProveedor() ?: return
        scope.launch {
            runCatching {
                val publicaciones = GestorMuroComunidades.obtenerPublicaciones(communityIdHex)
                if (publicaciones.isEmpty()) return@launch
                val jsonPubs = json.encodeToString(ListSerializer(PublicacionMuro.serializer()), publicaciones)
                val paquete = PaqueteMesh(
                    id = UUID.randomUUID().toString(),
                    chatId = "$CHAT_PREFIX$communityIdHex",
                    remitenteId = remitenteId,
                    destinatarioId = destinatarioId,
                    contenido = "$PREFIX_POSTS_SYNC$communityIdHex|$jsonPubs"
                )
                if (destinatarioId != null) {
                    val enviado = messageRouter.enviarADestino(destinatarioId, paquete)
                    if (!enviado) messageRouter.reenviar(paquete)
                } else {
                    messageRouter.reenviar(paquete)
                }
            }
        }
    }

    /**
     * Emite un like/reacción sobre una publicación del muro/gaceta.
     */
    fun emitirLikePublicacion(communityIdHex: String, publicacionId: String, usuarioId: String) {
        val remitenteId = miUsuarioIdProveedor() ?: return
        scope.launch {
            runCatching {
                val paquete = PaqueteMesh(
                    id = UUID.randomUUID().toString(),
                    chatId = "$CHAT_PREFIX$communityIdHex",
                    remitenteId = remitenteId,
                    contenido = "$PREFIX_POST_LIKE$communityIdHex|$publicacionId|$usuarioId"
                )
                messageRouter.reenviar(paquete)
            }
        }
    }

    /**
     * Solicita a los pares mesh cercanos que anuncien sus comunidades locales.
     */
    fun solicitarDescubrimientoComunidades() {
        val remitenteId = miUsuarioIdProveedor() ?: return
        scope.launch {
            runCatching {
                val paquete = PaqueteMesh(
                    id = UUID.randomUUID().toString(),
                    chatId = CHAT_DISCOVER,
                    remitenteId = remitenteId,
                    contenido = PREFIX_DISCOVER
                )
                messageRouter.reenviar(paquete)
            }
        }
    }

    /**
     * Inicia la sincronización de una comunidad enviando un resumen de inventario por broadcast mesh.
     */
    fun emitirResumenInventario(communityId: ByteArray) {
        val communityIdHex = communityId.joinToString("") { "%02x".format(it) }
        val remitenteId = miUsuarioIdProveedor() ?: return

        scope.launch {
            runCatching {
                val headOp = withContext(ioDispatcher) {
                    database.ledgerDao().getOwnerHead(communityIdHex)
                }

                val summary = CommunitySyncProtocol.InventorySummary(
                    communityIdHex = communityIdHex,
                    ownerHeadSeq = headOp?.ownerHeadSeq ?: 0L,
                    ownerHeadHashHex = headOp?.ownerHeadHashHex ?: "00".repeat(32)
                )

                val summaryBytes = CommunitySyncProtocol.serializeSummary(summary)
                val base64 = Base64.encodeToString(summaryBytes, Base64.NO_WRAP)

                val paquete = PaqueteMesh(
                    id = UUID.randomUUID().toString(),
                    chatId = "$CHAT_PREFIX$communityIdHex",
                    remitenteId = remitenteId,
                    contenido = "$PREFIX_SUMMARY$base64"
                )

                messageRouter.reenviar(paquete)
                _eventosSincronizacion.emit(SyncEvent.ResumenEnviado(communityIdHex, summary.ownerHeadSeq))
            }.onFailure { e ->
                _eventosSincronizacion.emit(SyncEvent.ErrorSincronizacion(communityIdHex, e.message ?: "Error emitiendo resumen"))
            }
        }
    }

    /**
     * Procesa un paquete mesh recibido. Si corresponde al canal de sincronización de comunidad,
     * ejecuta la conciliación del protocolo de inventario.
     */
    suspend fun procesarPaqueteMesh(paquete: PaqueteMesh): Boolean {
        if (!paquete.chatId.startsWith(CHAT_PREFIX)) return false
        val remitenteId = miUsuarioIdProveedor() ?: return false
        if (paquete.remitenteId == remitenteId) return false // Ignorar paquetes propios

        if (paquete.chatId == CHAT_DISCOVER || paquete.contenido.startsWith(PREFIX_DISCOVER)) {
            anunciarTodasLasComunidades()
            return true
        }

        val communityIdHex = paquete.chatId.removePrefix(CHAT_PREFIX)

        return withContext(ioDispatcher) {
            runCatching {
                when {
                    paquete.contenido.startsWith(PREFIX_SUMMARY) -> {
                        val base64 = paquete.contenido.removePrefix(PREFIX_SUMMARY)
                        val summaryBytes = Base64.decode(base64, Base64.NO_WRAP)
                        val remoteSummary = CommunitySyncProtocol.deserializeSummary(summaryBytes)

                        val localHead = database.ledgerDao().getOwnerHead(communityIdHex)
                        val existeLocalmente = localHead != null
                        val localSeq = localHead?.ownerHeadSeq ?: -1L

                        if (!existeLocalmente || remoteSummary.ownerHeadSeq > localSeq) {
                            // El par remoto tiene operaciones más nuevas o una comunidad que no tenemos: solicitarlas
                            val fromSeq = if (!existeLocalmente) 0L else (localSeq + 1)
                            val req = CommunitySyncProtocol.InventoryRequest(
                                communityIdHex = communityIdHex,
                                fromOwnerSeq = fromSeq,
                                toOwnerSeq = remoteSummary.ownerHeadSeq
                            )
                            val reqBytes = CommunitySyncProtocol.serializeRequest(req)
                            val reqBase64 = Base64.encodeToString(reqBytes, Base64.NO_WRAP)

                            val reqPaquete = PaqueteMesh(
                                id = UUID.randomUUID().toString(),
                                chatId = paquete.chatId,
                                remitenteId = remitenteId,
                                destinatarioId = paquete.remitenteId,
                                contenido = "$PREFIX_REQ$reqBase64"
                            )
                            val enviadoDirecto = messageRouter.enviarADestino(paquete.remitenteId, reqPaquete)
                            if (!enviadoDirecto) {
                                messageRouter.reenviar(reqPaquete)
                            }
                        } else if (localSeq > remoteSummary.ownerHeadSeq) {
                            // Tenemos operaciones que el par no tiene: enviarlas completas
                            val opsLocales = database.ledgerDao().getOperationsInRange(
                                communityIdHex = communityIdHex,
                                fromSeq = remoteSummary.ownerHeadSeq + 1,
                                toSeq = localSeq
                            )
                            if (opsLocales.isNotEmpty()) {
                                enviarOperacionesEnChunks(
                                    communityIdHex = communityIdHex,
                                    chatId = paquete.chatId,
                                    remitenteId = remitenteId,
                                    destinatarioId = paquete.remitenteId,
                                    operaciones = opsLocales
                                )
                            }
                        }
                        true
                    }

                    paquete.contenido.startsWith(PREFIX_REQ) -> {
                        val base64 = paquete.contenido.removePrefix(PREFIX_REQ)
                        val reqBytes = Base64.decode(base64, Base64.NO_WRAP)
                        val req = CommunitySyncProtocol.deserializeRequest(reqBytes)

                        val from = req.fromOwnerSeq ?: 0L
                        val to = req.toOwnerSeq ?: Long.MAX_VALUE
                        val opsLocales = database.ledgerDao().getOperationsInRange(
                            communityIdHex = communityIdHex,
                            fromSeq = from,
                            toSeq = to
                        )

                        if (opsLocales.isNotEmpty()) {
                            enviarOperacionesEnChunks(
                                communityIdHex = communityIdHex,
                                chatId = paquete.chatId,
                                remitenteId = remitenteId,
                                destinatarioId = paquete.remitenteId,
                                operaciones = opsLocales
                            )
                        }
                        true
                    }

                    paquete.contenido.startsWith(PREFIX_RESP) -> {
                        val base64 = paquete.contenido.removePrefix(PREFIX_RESP)
                        val respBytes = Base64.decode(base64, Base64.NO_WRAP)
                        val resp = CommunitySyncProtocol.deserializeResponse(respBytes)

                        var opsInsertadas = 0
                        for (opHex in resp.operationsHex) {
                            val insertada = procesarOperacionEntrante(communityIdHex, opHex)
                            if (insertada) opsInsertadas++
                        }

                        _eventosSincronizacion.emit(
                            SyncEvent.OperacionesRecibidas(communityIdHex, opsInsertadas)
                        )
                        true
                    }

                    paquete.contenido.startsWith(PREFIX_MEMBER_JOIN) -> {
                        val payload = paquete.contenido.removePrefix(PREFIX_MEMBER_JOIN)
                        val parts = payload.split("|")
                        if (parts.size >= 4) {
                            val cIdHex = parts[0]
                            val memberIk = parts[1]
                            val role = parts[2]
                            val status = parts[3]
                            val nombre = parts.getOrNull(4)?.takeIf { it.isNotBlank() }

                            database.communityDao().upsertMember(
                                CommunityMemberEntity(
                                    communityIdHex = cIdHex,
                                    authorIkHex = memberIk,
                                    role = role,
                                    status = status,
                                    joinedAtOwnerSeq = 0L
                                )
                            )
                            if (nombre != null) {
                                runCatching {
                                    database.peerProfileDao().insertOrUpdate(
                                        PeerProfileEntity(
                                            usuarioId = memberIk,
                                            nombre = nombre,
                                            ultimaVezVisto = System.currentTimeMillis()
                                        )
                                    )
                                }
                            }
                            _eventosSincronizacion.emit(SyncEvent.MiembrosActualizados(cIdHex))

                            // Responder con la lista completa de miembros conocidos para sincronizar bidireccionalmente
                            emitirTodosLosMiembros(cIdHex, destinatarioId = paquete.remitenteId)
                        }
                        true
                    }

                    paquete.contenido.startsWith(PREFIX_MEMBER_LEAVE) -> {
                        val payload = paquete.contenido.removePrefix(PREFIX_MEMBER_LEAVE)
                        val parts = payload.split("|")
                        if (parts.size >= 2) {
                            val cIdHex = parts[0]
                            val memberIk = parts[1]
                            database.communityDao().updateMemberStatus(cIdHex, memberIk, "INACTIVE")
                            _eventosSincronizacion.emit(SyncEvent.MiembrosActualizados(cIdHex))
                        }
                        true
                    }

                    paquete.contenido.startsWith(PREFIX_MEMBERS_SYNC) -> {
                        val payload = paquete.contenido.removePrefix(PREFIX_MEMBERS_SYNC)
                        val parts = payload.split("|")
                        if (parts.size >= 2) {
                            val cIdHex = parts[0]
                            val miembrosList = parts[1].split(";")
                            for (m in miembrosList) {
                                val fields = m.split(",")
                                if (fields.size >= 3) {
                                    database.communityDao().upsertMember(
                                        CommunityMemberEntity(
                                            communityIdHex = cIdHex,
                                            authorIkHex = fields[0],
                                            role = fields[1],
                                            status = fields[2],
                                            joinedAtOwnerSeq = 0L
                                        )
                                    )
                                }
                            }
                            _eventosSincronizacion.emit(SyncEvent.MiembrosActualizados(cIdHex))
                        }
                        true
                    }

                    paquete.contenido.startsWith(PREFIX_POST_NEW) -> {
                        val payload = paquete.contenido.removePrefix(PREFIX_POST_NEW)
                        val sepIndex = payload.indexOf('|')
                        if (sepIndex > 0) {
                            val cIdHex = payload.substring(0, sepIndex)
                            val jsonPub = payload.substring(sepIndex + 1)
                            val pub = runCatching { json.decodeFromString(PublicacionMuro.serializer(), jsonPub) }.getOrNull()
                            if (pub != null) {
                                GestorMuroComunidades.guardarPublicacion(cIdHex, pub)
                                _eventosSincronizacion.emit(SyncEvent.PublicacionesActualizadas(cIdHex))
                                // Responder con nuestras publicaciones conocidas para sincronizar bidireccionalmente
                                emitirTodasLasPublicaciones(cIdHex, destinatarioId = paquete.remitenteId)
                            }
                        }
                        true
                    }

                    paquete.contenido.startsWith(PREFIX_POSTS_SYNC) -> {
                        val payload = paquete.contenido.removePrefix(PREFIX_POSTS_SYNC)
                        val sepIndex = payload.indexOf('|')
                        if (sepIndex > 0) {
                            val cIdHex = payload.substring(0, sepIndex)
                            val jsonPubs = payload.substring(sepIndex + 1)
                            val pubs = runCatching {
                                json.decodeFromString(ListSerializer(PublicacionMuro.serializer()), jsonPubs)
                            }.getOrDefault(emptyList())
                            if (pubs.isNotEmpty()) {
                                for (p in pubs) {
                                    GestorMuroComunidades.guardarPublicacion(cIdHex, p)
                                }
                                _eventosSincronizacion.emit(SyncEvent.PublicacionesActualizadas(cIdHex))
                            }
                        }
                        true
                    }

                    paquete.contenido.startsWith(PREFIX_POST_LIKE) -> {
                        val payload = paquete.contenido.removePrefix(PREFIX_POST_LIKE)
                        val parts = payload.split("|")
                        if (parts.size >= 3) {
                            val cIdHex = parts[0]
                            val pubId = parts[1]
                            val uId = parts[2]
                            GestorMuroComunidades.toggleLike(cIdHex, pubId, uId)
                            _eventosSincronizacion.emit(SyncEvent.PublicacionesActualizadas(cIdHex))
                        }
                        true
                    }

                    else -> false
                }
            }.getOrElse { e ->
                _eventosSincronizacion.emit(
                    SyncEvent.ErrorSincronizacion(communityIdHex, e.message ?: "Error procesando paquete")
                )
                false
            }
        }
    }

    private suspend fun enviarOperacionesEnChunks(
        communityIdHex: String,
        chatId: String,
        remitenteId: String,
        destinatarioId: String?,
        operaciones: List<LedgerOperationEntity>
    ) {
        operaciones.chunked(CHUNK_SIZE_OPERACIONES).forEach { chunk ->
            val serializedHexList = chunk.map { entity ->
                val envelope = entityToEnvelope(entity)
                bytesToHex(envelope.encodeFullOperation())
            }

            val resp = CommunitySyncProtocol.InventoryResponse(
                communityIdHex = communityIdHex,
                operationsHex = serializedHexList
            )
            val respBytes = CommunitySyncProtocol.serializeResponse(resp)
            val respBase64 = Base64.encodeToString(respBytes, Base64.NO_WRAP)

            val respPaquete = PaqueteMesh(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                remitenteId = remitenteId,
                destinatarioId = destinatarioId,
                contenido = "$PREFIX_RESP$respBase64"
            )
            messageRouter.reenviar(respPaquete)
        }
    }

    private suspend fun procesarOperacionEntrante(
        channelCommunityIdHex: String,
        opHex: String
    ): Boolean {
        return runCatching {
            val opBytes = hexToBytes(opHex)
            val valResult = OperationValidator.validateBasicEnvelope(opBytes)
            if (!valResult.isValid) return false

            val envelope = OperationEnvelope.decode(opBytes)
            val opIdHex = envelope.calculateOpIdHex()

            // Si ya existe la operación, no duplicarla
            if (database.ledgerDao().getOperation(opIdHex) != null) return false

            // Validación estricta de firma criptográfica
            if (envelope.operationType != OperationEnvelope.TYPE_GENESIS) {
                if (envelope.signatureBytes.isEmpty()) {
                    SyncLogger.w(TAG, "Operación mesh rechazada: firma ausente (tipo=${envelope.operationType})")
                    return false
                }
                val authorKey = OperationValidator.reconstructPublicKey(envelope.authorIk)
                val signatureValid = if (authorKey != null && OperationValidator.verifySignature(envelope, authorKey)) {
                    true
                } else {
                    val dksKey = OperationValidator.decodePublicKeyX509(envelope.authorIk)
                    dksKey != null && OperationValidator.verifySignature(envelope, dksKey)
                }
                if (!signatureValid) {
                    SyncLogger.w(TAG, "Operación mesh rechazada: firma criptográfica inválida (tipo=${envelope.operationType})")
                    return false
                }
            }

            val opCommunityIdHex = bytesToHex(envelope.communityId)

            if (envelope.operationType == OperationEnvelope.TYPE_GENESIS) {
                val existente = database.communityDao().getCommunity(opIdHex)
                if (existente == null) {
                    val bodyString = String(envelope.bodyBytes, Charsets.UTF_8)
                    val genesisBody = runCatching {
                        json.decodeFromString<CommunityLedger.GenesisBody>(bodyString)
                    }.getOrNull()

                    val nombre = genesisBody?.name ?: "Comunidad P2P"
                    val desc = genesisBody?.description ?: ""
                    val epochIdHex = genesisBody?.initialEpochIdHex ?: bytesToHex(envelope.epochId)
                    val tipo = genesisBody?.tipo ?: "PRIVADA"
                    val fotoUrl = genesisBody?.fotoUrl

                    database.communityDao().insertCommunity(
                        CommunityEntity(
                            communityIdHex = opIdHex,
                            name = nombre,
                            description = desc,
                            ownerIkHex = bytesToHex(envelope.authorIk),
                            currentHeadSeq = envelope.deviceSeq,
                            currentHeadHashHex = opIdHex,
                            currentEpochIdHex = epochIdHex,
                            rulesVersion = 1,
                            isDissolved = false,
                            tipo = tipo,
                            fotoUrl = fotoUrl
                        )
                    )

                    database.communityDao().upsertMember(
                        CommunityMemberEntity(
                            communityIdHex = opIdHex,
                            authorIkHex = bytesToHex(envelope.authorIk),
                            role = CommunityAuthority.Role.OWNER.name,
                            status = "ACTIVE",
                            joinedAtOwnerSeq = envelope.ownerHeadSeq
                        )
                    )
                }

                val entity = envelopeToEntity(opIdHex, envelope)
                database.ledgerDao().insertOperation(entity)
                runCatching { communityRepositoryLocal?.getLedger(opIdHex) }
                true
            } else {
                val targetCommunityIdHex = channelCommunityIdHex.ifBlank { opCommunityIdHex }
                val aplicado = runCatching {
                    communityRepositoryLocal?.applyOperation(targetCommunityIdHex, envelope)
                }.getOrNull()

                if (aplicado == null) {
                    val entity = envelopeToEntity(targetCommunityIdHex, envelope)
                    database.ledgerDao().insertOperation(entity)

                    val comm = database.communityDao().getCommunity(targetCommunityIdHex)
                    if (comm != null && envelope.ownerHeadSeq > comm.currentHeadSeq) {
                        database.communityDao().updateHead(
                            communityIdHex = targetCommunityIdHex,
                            headSeq = envelope.ownerHeadSeq,
                            headHashHex = bytesToHex(envelope.ownerHeadHash)
                        )
                    }
                }
                true
            }
        }.getOrDefault(false)
    }

    private fun entityToEnvelope(entity: LedgerOperationEntity): OperationEnvelope {
        return OperationEnvelope(
            version = entity.version.toShort(),
            operationType = entity.operationType.toShort(),
            communityId = if (entity.operationType.toShort() == OperationEnvelope.TYPE_GENESIS) ByteArray(32) else hexToBytes(entity.communityIdHex),
            authorIk = hexToBytes(entity.authorIkHex),
            deviceId = UUID.fromString(entity.deviceId),
            deviceEpoch = entity.deviceEpoch,
            deviceSeq = entity.deviceSeq,
            prevOpHash = hexToBytes(entity.prevOpHashHex),
            ownerHeadSeq = entity.ownerHeadSeq,
            ownerHeadHash = hexToBytes(entity.ownerHeadHashHex),
            epochId = hexToBytes(entity.epochIdHex),
            timestamp = entity.timestampMs,
            bodyBytes = hexToBytes(entity.bodyHex),
            signatureBytes = hexToBytes(entity.signatureHex)
        )
    }

    private fun envelopeToEntity(
        communityIdHex: String,
        envelope: OperationEnvelope
    ): LedgerOperationEntity {
        return LedgerOperationEntity(
            opIdHex = envelope.calculateOpIdHex(),
            communityIdHex = communityIdHex,
            version = envelope.version.toInt(),
            operationType = envelope.operationType.toInt(),
            authorIkHex = bytesToHex(envelope.authorIk),
            deviceId = envelope.deviceId.toString(),
            deviceEpoch = envelope.deviceEpoch,
            deviceSeq = envelope.deviceSeq,
            prevOpHashHex = bytesToHex(envelope.prevOpHash),
            ownerHeadSeq = envelope.ownerHeadSeq,
            ownerHeadHashHex = bytesToHex(envelope.ownerHeadHash),
            epochIdHex = bytesToHex(envelope.epochId),
            timestampMs = envelope.timestamp,
            bodyHex = bytesToHex(envelope.bodyBytes),
            signatureHex = bytesToHex(envelope.signatureBytes),
            validationStatus = "CONFIRMED"
        )
    }

    private fun hexToBytes(hex: String): ByteArray = hex.hexToByteArray()

    private fun bytesToHex(bytes: ByteArray): String = bytes.toHex()
}
