package com.renea.arete_social.data.community

import com.renea.arete_social.data.identity.DeviceKeyManager
import com.renea.arete_social.data.identity.IdentityManager
import com.renea.arete_social.data.local.media.AlmacenMediaPerfil
import com.renea.arete_social.data.local.room.entity.CommunityEntity
import com.renea.arete_social.domain.model.Comunidad
import com.renea.arete_social.domain.repository.RepositorioComunidades
import com.renea.arete_social.feature.comunidades.ArchivoComunidad
import com.renea.arete_social.feature.comunidades.PublicacionMuro
import com.renea.arete_social.feature.comunidades.data.GestorMuroComunidades
import com.renea.arete_social.data.protocol.OwnerSpine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import com.renea.arete_social.domain.util.toHex
import com.renea.arete_social.domain.util.hexToByteArray

/**
 * Implementación híbrida de [RepositorioComunidades] bajo la arquitectura Local-First (Auditoría v3).
 *
 * Cuando [featureFlagP2p] está activo:
 * - Las comunidades creadas se registran de forma soberana en el ledger local ([CommunityRepositoryLocal])
 *   con su operación Génesis, par de claves y persistencia Room.
 * - [listarComunidades] combina las comunidades locales soberanas con las comunidades remotas existentes.
 * - Las consultas para comunidades locales se resuelven sin requerir conexión a internet ni servidores centrales.
 */
class RepositorioComunidadesLocalFirst(
    private val localRepo: CommunityRepositoryLocal,
    private val remoteRepo: RepositorioComunidades? = null,
    private val identityManager: IdentityManager,
    private val deviceKeyManager: DeviceKeyManager,
    private val featureFlagP2p: Boolean = true,
    private val syncManagerProveedor: (() -> com.renea.arete_social.data.mesh.sync.CommunityMeshSyncManager?)? = null,
    private val deviceIdProvider: (() -> UUID) = { identityManager.getOrLoadDeviceId() },
    private val almacenMedia: AlmacenMediaPerfil? = null,
    private val context: android.content.Context? = null,
    private val gestorArchivosLanProveedor: (() -> com.renea.arete_social.data.network.wifi.GestorArchivosLan?)? = null,
    private val resolverIdentidad: com.renea.arete_social.domain.repository.ResolverIdentidad? = null
) : RepositorioComunidades {


    private val localDeviceId: UUID get() = deviceIdProvider()

    override suspend fun crearComunidad(
        nombre: String,
        descripcion: String,
        tipo: String,
        fotoBytes: ByteArray?,
        extension: String
    ): Comunidad {
        if (!featureFlagP2p && remoteRepo != null) {
            return remoteRepo.crearComunidad(nombre, descripcion, tipo, fotoBytes, extension)
        }

        // Si la IK no está cargada, la inicializamos (o usamos un fallback temporal)
        if (!identityManager.isLoaded) {
            identityManager.generateAndLoad()
        }

        val ownerIk = identityManager.ikPublicBytes
        val devId = localDeviceId

        val fotoGuardada = if (fotoBytes != null && fotoBytes.isNotEmpty() && almacenMedia != null) {
            almacenMedia.guardarImagen(fotoBytes, esAvatar = true, extension = extension.ifBlank { "jpg" })
        } else null
        val fotoGuardadaPath = fotoGuardada?.let { it.rutaMiniatura.ifBlank { it.rutaArchivo } }

        // Crear soberanamente en el ledger local
        val (communityIdHex, _) = localRepo.createCommunity(
            name = nombre,
            description = descripcion,
            ownerIk = ownerIk,
            deviceId = devId,
            deviceEpoch = 1,
            dksPrivateKey = identityManager.ikPrivateKey,
            tipo = tipo.ifBlank { "ABIERTA" },
            fotoUrl = fotoGuardadaPath
        )

        // Emitir resumen de inventario al mesh para propagación inmediata a pares cercanos
        runCatching {
            syncManagerProveedor?.invoke()?.emitirResumenInventario(hexToBytes(communityIdHex))
            syncManagerProveedor?.invoke()?.emitirTodosLosMiembros(communityIdHex)
        }

        return Comunidad(
            id = communityIdHex,
            nombre = nombre,
            descripcion = descripcion,
            tipo = tipo.ifBlank { "ABIERTA" },
            fotoUrl = fotoGuardadaPath,
            chatId = communityIdHex,
            liderId = bytesToHex(ownerIk),
            miembrosCount = 1
        )
    }

    override suspend fun listarComunidades(): List<Comunidad> {
        val entidadesLocales = if (featureFlagP2p) {
            runCatching { localRepo.getAllCommunities() }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val mappedLocales = entidadesLocales.map { entityToDomain(it) }

        // Sincronizar proactivamente con pares mesh conocidos
        if (featureFlagP2p) {
            runCatching {
                val syncMgr = syncManagerProveedor?.invoke()
                if (syncMgr != null) {
                    if (entidadesLocales.isNotEmpty()) {
                        syncMgr.anunciarTodasLasComunidades()
                    } else {
                        syncMgr.solicitarDescubrimientoComunidades()
                    }
                }
            }
        }

        // Intento remoto opcional con timeout corto
        val remotas = runCatching {
            remoteRepo?.let { withTimeoutOrNull(1000.milliseconds) { it.listarComunidades() } }
        }.getOrNull() ?: emptyList()

        if (!featureFlagP2p) return remotas

        // Combinamos garantizando que las comunidades locales soberanas tengan precedencia
        val map = linkedMapOf<String, Comunidad>()
        for (remota in remotas) {
            map[remota.id] = remota
        }
        for (local in mappedLocales) {
            map[local.id] = local
        }
        return map.values.toList()
    }

    override suspend fun obtenerComunidad(id: String): Comunidad? {
        if (featureFlagP2p) {
            val local = localRepo.getCommunity(id)
            if (local != null) {
                return entityToDomain(local)
            }
        }
        return runCatching {
            remoteRepo?.let { withTimeoutOrNull(1000.milliseconds) { it.obtenerComunidad(id) } }
        }.getOrNull()
    }

    override fun observarComunidad(id: String): Flow<Comunidad?> = flow {
        if (featureFlagP2p) {
            val local = localRepo.getCommunity(id)
            if (local != null) {
                emit(entityToDomain(local))
                return@flow
            }
        }
        remoteRepo?.observarComunidad(id)?.collect { emit(it) }
    }

    override suspend fun unirseAComunidad(comunidadId: String, usuarioId: String) {
        val local = localRepo.getCommunity(comunidadId)
        if (local != null) {
            val esPrivada = local.tipo.equals("PRIVADA", ignoreCase = true)
            val status = if (esPrivada) "REQUESTED" else "ACTIVE"
            localRepo.addLocalMember(comunidadId, usuarioId, role = "MEMBER", status = status)
            val miNombre = runCatching { com.renea.arete_social.feature.auth.GestorSesionLocal.obtenerUltimoNombre() }.getOrNull()
            runCatching {
                syncManagerProveedor?.invoke()?.emitirUnionMiembro(
                    communityIdHex = comunidadId,
                    authorIkHex = usuarioId,
                    role = "MEMBER",
                    status = status,
                    nombre = miNombre
                )
            }
            return
        }
        remoteRepo?.let { runCatching { withTimeoutOrNull(1000.milliseconds) { it.unirseAComunidad(comunidadId, usuarioId) } } }
    }

    override suspend fun salirDeComunidad(comunidadId: String, usuarioId: String) {
        val local = localRepo.getCommunity(comunidadId)
        if (local != null) {
            localRepo.removeLocalMember(comunidadId, usuarioId)
            runCatching {
                syncManagerProveedor?.invoke()?.emitirSalidaMiembro(comunidadId, usuarioId)
            }
            return
        }
        remoteRepo?.let { runCatching { withTimeoutOrNull(1000.milliseconds) { it.salirDeComunidad(comunidadId, usuarioId) } } }
    }

    override suspend fun transferirLiderazgo(comunidadId: String, nuevoLiderId: String) {
        val local = localRepo.getCommunity(comunidadId)
        if (local != null) {
            // Transferencia de liderazgo en la Columna del Dueño
            if (!identityManager.isLoaded) identityManager.generateAndLoad()
            val myIk = identityManager.ikPublicBytes
            val myIkHex = bytesToHex(myIk)
            if (!local.ownerIkHex.equals(myIkHex, ignoreCase = true)) return

            val devId = localDeviceId
            val dksKeyPair = deviceKeyManager.getOrCreateDksKeyPair(devId.toString())
            val ledger = runCatching { localRepo.getLedger(comunidadId) }.getOrNull() ?: return
            val state = ledger.getState()

            val body = CommunityLedger.RoleDesignateBody(targetIkHex = nuevoLiderId, role = "LEADER")
            val bodyBytes = kotlinx.serialization.json.Json.encodeToString(CommunityLedger.RoleDesignateBody.serializer(), body).toByteArray(Charsets.UTF_8)

            val op = com.renea.arete_social.data.protocol.OperationEnvelope(
                version = 1,
                operationType = com.renea.arete_social.data.protocol.OperationEnvelope.TYPE_LEADER_DESIGNATE,
                communityId = hexToBytes(comunidadId),
                authorIk = myIk,
                deviceId = devId,
                deviceEpoch = 1,
                deviceSeq = state.currentHeadSeq + 1,
                prevOpHash = state.currentHeadHash,
                ownerHeadSeq = state.currentHeadSeq,
                ownerHeadHash = state.currentHeadHash,
                epochId = hexToBytes(local.currentEpochIdHex),
                timestamp = System.currentTimeMillis(),
                bodyBytes = bodyBytes,
                signatureBytes = ByteArray(0)
            )

            val signed = com.renea.arete_social.data.identity.SignatureManager().signOperation(op, dksKeyPair.private)
            val resultado = localRepo.applyOperation(comunidadId, signed)
            if (resultado == OwnerSpine.EvaluationStatus.CONFIRMED) {
                localRepo.updateLeader(comunidadId, nuevoLiderId)
            }

            runCatching {
                syncManagerProveedor?.invoke()?.emitirResumenInventario(hexToBytes(comunidadId))
            }
            return
        }
        remoteRepo?.let { runCatching { withTimeoutOrNull(1000.milliseconds) { it.transferirLiderazgo(comunidadId, nuevoLiderId) } } }
    }

    override suspend fun asignarRol(comunidadId: String, usuarioId: String, rol: String) {
        val local = localRepo.getCommunity(comunidadId)
        if (local != null) {
            if (!identityManager.isLoaded) identityManager.generateAndLoad()
            val myIk = identityManager.ikPublicBytes
            val devId = localDeviceId
            val dksKeyPair = deviceKeyManager.getOrCreateDksKeyPair(devId.toString())
            val ledger = runCatching { localRepo.getLedger(comunidadId) }.getOrNull() ?: return
            val state = ledger.getState()

            val opType = when (rol.uppercase()) {
                "ADMIN" -> com.renea.arete_social.data.protocol.OperationEnvelope.TYPE_ADMIN_DESIGNATE
                "MODERATOR" -> com.renea.arete_social.data.protocol.OperationEnvelope.TYPE_MODERATOR_DESIGNATE
                else -> com.renea.arete_social.data.protocol.OperationEnvelope.TYPE_ROLE_REVOKE
            }

            val body = CommunityLedger.RoleDesignateBody(targetIkHex = usuarioId, role = rol.uppercase())
            val bodyBytes = kotlinx.serialization.json.Json.encodeToString(CommunityLedger.RoleDesignateBody.serializer(), body).toByteArray(Charsets.UTF_8)

            val op = com.renea.arete_social.data.protocol.OperationEnvelope(
                version = 1,
                operationType = opType,
                communityId = hexToBytes(comunidadId),
                authorIk = myIk,
                deviceId = devId,
                deviceEpoch = 1,
                deviceSeq = state.currentHeadSeq + 1,
                prevOpHash = state.currentHeadHash,
                ownerHeadSeq = state.currentHeadSeq,
                ownerHeadHash = state.currentHeadHash,
                epochId = hexToBytes(local.currentEpochIdHex),
                timestamp = System.currentTimeMillis(),
                bodyBytes = bodyBytes,
                signatureBytes = ByteArray(0)
            )

            val signed = com.renea.arete_social.data.identity.SignatureManager().signOperation(op, dksKeyPair.private)
            val resultado = localRepo.applyOperation(comunidadId, signed)
            if (resultado == OwnerSpine.EvaluationStatus.CONFIRMED) {
                localRepo.updateMemberRole(comunidadId, usuarioId, rol.uppercase())
            }

            runCatching {
                syncManagerProveedor?.invoke()?.emitirResumenInventario(hexToBytes(comunidadId))
            }
            return
        }
        remoteRepo?.let { runCatching { withTimeoutOrNull(1000.milliseconds) { it.asignarRol(comunidadId, usuarioId, rol) } } }
    }

    override suspend fun obtenerRolMiembro(comunidadId: String, usuarioId: String): String? {
        val local = localRepo.getCommunity(comunidadId)
        if (local != null) {
            val roomMember = localRepo.getActiveMembers(comunidadId).firstOrNull { it.authorIkHex == usuarioId }
            if (roomMember != null) return roomMember.role
            val ledger = runCatching { localRepo.getLedger(comunidadId) }.getOrNull()
            val member = ledger?.getState()?.members?.values?.firstOrNull {
                bytesToHex(it.authorIk) == usuarioId
            }
            return member?.role?.name
        }
        return remoteRepo?.let { runCatching { withTimeoutOrNull(1000.milliseconds) { it.obtenerRolMiembro(comunidadId, usuarioId) } }.getOrNull() }
    }

    override suspend fun listarMiembrosConRoles(comunidadId: String): Map<String, String> {
        val local = localRepo.getCommunity(comunidadId)
        if (local != null) {
            val rolesRoom = localRepo.getActiveMembers(comunidadId).associate { it.authorIkHex to it.role }
            val ledger = runCatching { localRepo.getLedger(comunidadId) }.getOrNull()
            val rolesLedger = ledger?.getState()?.members?.values?.associate { m ->
                val ikHex = bytesToHex(m.authorIk)
                val id = resolverIdentidad?.usuarioDe(ikHex) ?: ikHex
                id to m.role.name
            } ?: emptyMap()
            return rolesLedger + rolesRoom
        }
        return remoteRepo?.let { runCatching { withTimeoutOrNull(1000.milliseconds) { it.listarMiembrosConRoles(comunidadId) } }.getOrNull() } ?: emptyMap()
    }

    override suspend fun actualizarComunidad(id: String, nombre: String, descripcion: String, tipo: String, fotoUrl: String?) {
        val local = localRepo.getCommunity(id)
        if (local != null) {
            val actualizado = local.copy(
                name = nombre,
                description = descripcion,
                tipo = tipo.ifBlank { local.tipo },
                // Only update photo if a non-blank value is provided; never erase an existing photo
                fotoUrl = fotoUrl?.takeIf { it.isNotBlank() } ?: local.fotoUrl,
                updatedAtMs = System.currentTimeMillis()
            )
            localRepo.updateCommunity(actualizado)
            return
        }
        remoteRepo?.let { runCatching { withTimeoutOrNull(1000.milliseconds) { it.actualizarComunidad(id, nombre, descripcion, tipo, fotoUrl) } } }
    }

    override suspend fun eliminarComunidad(comunidadId: String) {
        val local = localRepo.getCommunity(comunidadId)
        if (local != null) {
            // Operación DISSOLVE en el ledger local
            if (!identityManager.isLoaded) identityManager.generateAndLoad()
            val myIk = identityManager.ikPublicBytes
            val myIkHex = bytesToHex(myIk)
            if (!local.ownerIkHex.equals(myIkHex, ignoreCase = true)) return

            val devId = localDeviceId
            val dksKeyPair = deviceKeyManager.getOrCreateDksKeyPair(devId.toString())
            val ledger = runCatching { localRepo.getLedger(comunidadId) }.getOrNull() ?: return
            val state = ledger.getState()

            val op = com.renea.arete_social.data.protocol.OperationEnvelope(
                version = 1,
                operationType = com.renea.arete_social.data.protocol.OperationEnvelope.TYPE_DISSOLVE,
                communityId = hexToBytes(comunidadId),
                authorIk = myIk,
                deviceId = devId,
                deviceEpoch = 1,
                deviceSeq = System.currentTimeMillis(),
                prevOpHash = state.currentHeadHash,
                ownerHeadSeq = state.currentHeadSeq,
                ownerHeadHash = state.currentHeadHash,
                epochId = hexToBytes(local.currentEpochIdHex),
                timestamp = System.currentTimeMillis(),
                bodyBytes = ByteArray(0),
                signatureBytes = ByteArray(0)
            )

            val signed = com.renea.arete_social.data.identity.SignatureManager().signOperation(op, dksKeyPair.private)
            localRepo.applyOperation(comunidadId, signed)
            localRepo.updateCommunity(local.copy(isDissolved = true, updatedAtMs = System.currentTimeMillis()))

            runCatching {
                syncManagerProveedor?.invoke()?.emitirResumenInventario(hexToBytes(comunidadId))
            }
            return
        }
        remoteRepo?.let { runCatching { withTimeoutOrNull(1000.milliseconds) { it.eliminarComunidad(comunidadId) } } }
    }

    override suspend fun subirAvatarComunidad(comunidadId: String, bytes: ByteArray, extension: String): String {
        // Local-first: save via AlmacenMediaPerfil and persist path+hash in Room
        val media = almacenMedia
        if (media != null && bytes.isNotEmpty()) {
            val resultado = runCatching {
                media.guardarImagen(bytes, esAvatar = true, extension = extension.ifBlank { "jpg" })
            }.getOrNull()
            if (resultado != null) {
                val ruta = resultado.rutaMiniatura.ifBlank { resultado.rutaArchivo }
                val hash = resultado.hashSha256
                if (ruta.isNotBlank()) {
                    val local = localRepo.getCommunity(comunidadId)
                    if (local != null) {
                        localRepo.updateCommunity(
                            local.copy(
                                fotoUrl = ruta,
                                updatedAtMs = System.currentTimeMillis()
                            )
                        )
                    }
                    return hash.ifBlank { ruta }
                }
            }
        }
        return remoteRepo?.let { runCatching { withTimeoutOrNull(2000.milliseconds) { it.subirAvatarComunidad(comunidadId, bytes, extension) } }.getOrNull() } ?: ""
    }

    override suspend fun esMiembro(comunidadId: String, usuarioId: String): Boolean {
        val local = localRepo.getCommunity(comunidadId)
        if (local != null) {
            val miembrosRoom = localRepo.getActiveMembers(comunidadId)
            // Check both by session ID and by IK hex (via resolverIdentidad bridge)
            val miIkHex = resolverIdentidad?.ikDe(usuarioId)
            if (miembrosRoom.any { m ->
                    m.authorIkHex.equals(usuarioId, ignoreCase = true) ||
                    (miIkHex != null && m.authorIkHex.equals(miIkHex, ignoreCase = true))
                }) {
                return true
            }
            val ledger = runCatching { localRepo.getLedger(comunidadId) }.getOrNull()
            return ledger?.getState()?.members?.values?.any { m ->
                (bytesToHex(m.authorIk).equals(usuarioId, ignoreCase = true) ||
                    (miIkHex != null && bytesToHex(m.authorIk).equals(miIkHex, ignoreCase = true))) &&
                        m.status == CommunityMemberState.MemberStatus.ACTIVE
            } ?: false
        }
        return runCatching { withTimeoutOrNull(1000.milliseconds) { remoteRepo?.esMiembro(comunidadId, usuarioId) } }.getOrNull() ?: false
    }

    override suspend fun listarMiembrosIds(comunidadId: String): List<String> {
        val local = localRepo.getCommunity(comunidadId)
        if (local != null) {
            val miembrosRoom = localRepo.getActiveMembers(comunidadId).map { it.authorIkHex }
            val ledger = runCatching { localRepo.getLedger(comunidadId) }.getOrNull()
            val miembrosLedger = ledger?.getState()?.members?.values
                ?.filter { it.status == CommunityMemberState.MemberStatus.ACTIVE }
                ?.map { bytesToHex(it.authorIk) } ?: emptyList()
            return (miembrosRoom + miembrosLedger).distinct()
        }
        return runCatching { withTimeoutOrNull(1000.milliseconds) { remoteRepo?.listarMiembrosIds(comunidadId) } }.getOrNull() ?: emptyList()
    }

    override suspend fun crearPublicacionMuro(
        comunidadId: String,
        autorId: String,
        autorNombre: String,
        contenido: String,
        imagenBytes: ByteArray?,
        extension: String,
        categoria: String
    ): PublicacionMuro {
        val local = localRepo.getCommunity(comunidadId)
        if (local != null) {
            var fotoGuardadaPath: String? = null
            val media = almacenMedia
            if (imagenBytes != null && imagenBytes.isNotEmpty() && media != null) {
                val guardado = media.guardarImagen(imagenBytes, esAvatar = false, extension = extension.ifBlank { "jpg" })
                fotoGuardadaPath = guardado.rutaMiniatura.ifBlank { guardado.rutaArchivo }
            }

            val pub = PublicacionMuro(
                id = UUID.randomUUID().toString(),
                autorId = autorId,
                autor = autorNombre,
                tiempo = "Ahora",
                texto = contenido,
                imagenUri = fotoGuardadaPath,
                categoria = categoria,
                likes = 0,
                likedPorMi = false
            )
            context?.let { ctx ->
                GestorMuroComunidades.inicializar(ctx)
                GestorMuroComunidades.guardarPublicacion(comunidadId, pub)
            }
            syncManagerProveedor?.invoke()?.emitirPublicacion(comunidadId, pub)
            return pub
        }
        return remoteRepo?.crearPublicacionMuro(
            comunidadId, autorId, autorNombre, contenido, imagenBytes, extension, categoria
        ) ?: PublicacionMuro(
            id = UUID.randomUUID().toString(),
            autorId = autorId,
            autor = autorNombre,
            tiempo = "Ahora",
            texto = contenido,
            imagenUri = null,
            categoria = categoria,
            likes = 0,
            likedPorMi = false
        )
    }

    override suspend fun listarPublicacionesMuro(comunidadId: String): List<PublicacionMuro> {
        val local = localRepo.getCommunity(comunidadId)
        if (local != null) {
            context?.let { ctx ->
                GestorMuroComunidades.inicializar(ctx)
                val lista = GestorMuroComunidades.obtenerPublicaciones(comunidadId)
                if (lista.isNotEmpty()) return lista
            }
            return emptyList()
        }
        return runCatching {
            remoteRepo?.let { withTimeoutOrNull(1000.milliseconds) { it.listarPublicacionesMuro(comunidadId) } }
        }.getOrNull() ?: emptyList()
    }

    override suspend fun toggleLikePublicacion(comunidadId: String, publicacionId: String, usuarioId: String): PublicacionMuro? {
        val local = localRepo.getCommunity(comunidadId)
        if (local != null) {
            context?.let { ctx ->
                GestorMuroComunidades.inicializar(ctx)
                val actualizado = GestorMuroComunidades.toggleLike(comunidadId, publicacionId, usuarioId)
                if (actualizado != null) {
                    syncManagerProveedor?.invoke()?.emitirLikePublicacion(comunidadId, publicacionId, usuarioId)
                }
                return actualizado
            }
            return null
        }
        return remoteRepo?.let { runCatching { withTimeoutOrNull(1000.milliseconds) { it.toggleLikePublicacion(comunidadId, publicacionId, usuarioId) } }.getOrNull() }
    }

    override suspend fun subirArchivoComunidad(
        comunidadId: String,
        autorId: String,
        autorNombre: String,
        nombreArchivo: String,
        bytes: ByteArray,
        tipoMime: String
    ): ArchivoComunidad {
        val fileId = UUID.randomUUID().toString()
        var rutaArchivo = ""
        context?.let { ctx ->
            runCatching {
                val dir = java.io.File(ctx.filesDir, "media/comunidades/$comunidadId")
                if (!dir.exists()) dir.mkdirs()
                val destFile = java.io.File(dir, "$fileId.dat")
                destFile.writeBytes(bytes)
                rutaArchivo = destFile.absolutePath
            }
        }
        val archivo = ArchivoComunidad(
            id = fileId,
            comunidadId = comunidadId,
            nombre = nombreArchivo,
            url = rutaArchivo,
            tipoMime = tipoMime,
            tamanoBytes = bytes.size.toLong(),
            autorNombre = autorNombre
        )
        context?.let { ctx ->
            com.renea.arete_social.feature.comunidades.data.GestorMuroComunidades.inicializar(ctx)
            com.renea.arete_social.feature.comunidades.data.GestorMuroComunidades.guardarArchivo(comunidadId, archivo)
        }
        return archivo
    }

    override suspend fun listarArchivosComunidad(comunidadId: String): List<ArchivoComunidad> {
        context?.let { ctx ->
            com.renea.arete_social.feature.comunidades.data.GestorMuroComunidades.inicializar(ctx)
            val archivos = com.renea.arete_social.feature.comunidades.data.GestorMuroComunidades.obtenerArchivos(comunidadId)
            if (archivos.isNotEmpty()) return archivos
        }
        return emptyList()
    }

    override fun solicitarArchivosLan(comunidadId: String, destinoPeerId: String) {
        gestorArchivosLanProveedor?.invoke()?.solicitarListaArchivos(comunidadId, destinoPeerId)
    }

    override fun descargarArchivoLan(comunidadId: String, archivoId: String, destinoPeerId: String) {
        gestorArchivosLanProveedor?.invoke()?.descargarArchivo(comunidadId, archivoId, destinoPeerId)
    }

    // ─── Bandeja de aprobación ───

    override suspend fun listarSolicitudesPendientes(comunidadId: String): List<String> {
        if (!featureFlagP2p) return emptyList()
        return runCatching {
            localRepo.getPendingRequests(comunidadId).map { it.authorIkHex }
        }.getOrDefault(emptyList())
    }

    override suspend fun aprobarMiembro(comunidadId: String, solicitanteId: String) {
        if (!featureFlagP2p) return
        val local = localRepo.getCommunity(comunidadId) ?: return

        // Necesitamos la clave IK del aprobador (líder/admin local)
        if (!identityManager.isLoaded) identityManager.generateAndLoad()
        val approverIk = identityManager.ikPublicBytes
        val devId = localDeviceId
        val dksKeyPair = deviceKeyManager.getOrCreateDksKeyPair(devId.toString())

        // Obtener el head actual del ledger para encadenar la operación
        val ledger = runCatching { localRepo.getLedger(comunidadId) }.getOrNull() ?: return
        val state = ledger.getState()

        val communityIdBytes = hexToBytes(comunidadId)
        val epochIdBytes = hexToBytes(local.currentEpochIdHex)

        // Cuerpo: RoleDesignateBody para que el CommunityLedger decodifique la membresía correctamente
        val body = CommunityLedger.RoleDesignateBody(targetIkHex = solicitanteId, role = "MEMBER")
        val bodyBytes = kotlinx.serialization.json.Json.encodeToString(CommunityLedger.RoleDesignateBody.serializer(), body).toByteArray(Charsets.UTF_8)

        val op = com.renea.arete_social.data.protocol.OperationEnvelope(
            version = 1,
            operationType = com.renea.arete_social.data.protocol.OperationEnvelope.TYPE_JOIN_ACCEPT,
            communityId = communityIdBytes,
            authorIk = approverIk,
            deviceId = devId,
            deviceEpoch = 1,
            deviceSeq = System.currentTimeMillis(),
            prevOpHash = state.currentHeadHash,
            ownerHeadSeq = state.currentHeadSeq,
            ownerHeadHash = state.currentHeadHash,
            epochId = epochIdBytes,
            timestamp = System.currentTimeMillis(),
            bodyBytes = bodyBytes,
            signatureBytes = ByteArray(0)
        )

        val signed = com.renea.arete_social.data.identity.SignatureManager().signOperation(op, dksKeyPair.private)
        runCatching { localRepo.applyOperation(comunidadId, signed) }
        localRepo.addLocalMember(comunidadId, solicitanteId, role = "MEMBER", status = "ACTIVE")

        // Emitir sincronización y membrecía al mesh para que el miembro aprobado se entere
        runCatching {
            syncManagerProveedor?.invoke()?.emitirResumenInventario(hexToBytes(comunidadId))
            syncManagerProveedor?.invoke()?.emitirUnionMiembro(comunidadId, solicitanteId, role = "MEMBER", status = "ACTIVE")
        }
    }

    private suspend fun entityToDomain(entity: CommunityEntity): Comunidad {
        val miembrosCount = runCatching { localRepo.getActiveMembers(entity.communityIdHex).size }.getOrDefault(1)
        return Comunidad(
            id = entity.communityIdHex,
            nombre = entity.name,
            descripcion = entity.description,
            tipo = entity.tipo,
            fotoUrl = entity.fotoUrl,
            chatId = entity.communityIdHex,
            liderId = entity.leaderIkHex ?: entity.ownerIkHex,
            miembrosCount = miembrosCount
        )
    }

    private fun bytesToHex(bytes: ByteArray): String = bytes.toHex()

    private fun hexToBytes(hex: String): ByteArray = hex.hexToByteArray()
}

