package com.renea.arete_social.data.community

import com.renea.arete_social.data.identity.DeviceCertificate
import com.renea.arete_social.data.identity.SignatureManager
import com.renea.arete_social.data.local.room.AppLocalDatabase
import com.renea.arete_social.data.local.room.entity.CommunityEntity
import com.renea.arete_social.data.local.room.entity.CommunityMemberEntity
import com.renea.arete_social.data.local.room.entity.EpochKeyEntity
import com.renea.arete_social.data.local.room.entity.LedgerOperationEntity
import com.renea.arete_social.data.protocol.OperationEnvelope
import com.renea.arete_social.data.protocol.OwnerSpine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import com.renea.arete_social.domain.util.toHex
import com.renea.arete_social.domain.util.hexToByteArray
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import android.util.Log
import java.math.BigInteger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Repositorio local para la gestión de comunidades en arquitectura Local-First.
 * Coordina la base de datos Room (persistencia) con los `CommunityLedger` en memoria.
 */
class CommunityRepositoryLocal(
    private val database: AppLocalDatabase,
    private val signatureManager: SignatureManager,
    private val epochKeyManager: EpochKeyManager,
    private val epochKeyStorageProtector: EpochKeyStorageProtector = EpochKeyStorageProtector()
) {
    private val communityDao = database.communityDao()
    private val ledgerDao = database.ledgerDao()

    private val activeLedgers = ConcurrentHashMap<String, CommunityLedger>()
    private val mutex = Mutex()

    /**
     * Observa la lista de comunidades locales activas.
     */
    fun observeCommunities(): Flow<List<CommunityEntity>> {
        return communityDao.observeActiveCommunities()
    }

    /**
     * Observa los miembros de una comunidad.
     */
    fun observeMembers(communityIdHex: String): Flow<List<CommunityMemberEntity>> {
        return communityDao.observeMembers(communityIdHex)
    }

    /**
     * Obtiene los metadatos de una comunidad persistida.
     */
    suspend fun getCommunity(communityIdHex: String): CommunityEntity? {
        return communityDao.getCommunity(communityIdHex)
    }

    /**
     * Obtiene todas las comunidades locales persistidas en Room.
     */
    suspend fun getAllCommunities(): List<CommunityEntity> {
        return communityDao.getAllCommunities()
    }

    /**
     * Actualiza una comunidad local existente en Room.
     */
    suspend fun updateCommunity(community: CommunityEntity) {
        communityDao.updateCommunity(community)
    }

    suspend fun addLocalMember(communityIdHex: String, authorIkHex: String, role: String = "MEMBER", status: String = "ACTIVE") {
        communityDao.upsertMember(
            com.renea.arete_social.data.local.room.entity.CommunityMemberEntity(
                communityIdHex = communityIdHex,
                authorIkHex = authorIkHex,
                role = role,
                status = status,
                joinedAtOwnerSeq = 0L
            )
        )
    }

    suspend fun removeLocalMember(communityIdHex: String, authorIkHex: String) {
        communityDao.updateMemberStatus(
            communityIdHex = communityIdHex,
            authorIkHex = authorIkHex,
            status = "INACTIVE"
        )
    }

    suspend fun updateLeader(communityIdHex: String, leaderIkHex: String?) {
        communityDao.updateLeader(communityIdHex, leaderIkHex)
    }

    suspend fun updateMemberRole(communityIdHex: String, authorIkHex: String, role: String) {
        communityDao.updateMemberRole(communityIdHex, authorIkHex, role)
    }

    suspend fun getActiveMembers(communityIdHex: String): List<com.renea.arete_social.data.local.room.entity.CommunityMemberEntity> {
        return communityDao.getActiveMembers(communityIdHex)
    }

    /** Devuelve miembros en estado REQUESTED (pendientes de aprobación). */
    suspend fun getPendingRequests(communityIdHex: String): List<com.renea.arete_social.data.local.room.entity.CommunityMemberEntity> {
        return communityDao.getPendingRequests(communityIdHex)
    }

    /**
     * Obtiene o rehidrata el `CommunityLedger` en memoria desde Room.
     */
    suspend fun getLedger(communityIdHex: String): CommunityLedger = mutex.withLock {
        activeLedgers.getOrPut(communityIdHex) {
            val community = communityDao.getCommunity(communityIdHex)
                ?: error("Comunidad no encontrada: $communityIdHex")

            val ownerIk = hexToBytes(community.ownerIkHex)
            val ledger = CommunityLedger(
                communityId = hexToBytes(communityIdHex),
                ownerIk = ownerIk,
                initialName = community.name,
                initialDescription = community.description
            )

            // Rehidratar operaciones históricas ordenadas
            val operations = ledgerDao.getOperationsForCommunity(communityIdHex)
            for (op in operations) {
                val envelope = entityToEnvelope(op)
                ledger.applyOperation(envelope)
            }

            // ARETE-FUNC-001: Rehidratar claves de época en EpochKeyManager tras reinicio
            rehydrateEpochKeysLocked(communityIdHex)

            ledger
        }
    }

    /**
     * Rehidrata las claves de época almacenadas en Room hacia el EpochKeyManager en memoria (ARETE-FUNC-001).
     */
    private suspend fun rehydrateEpochKeysLocked(communityIdHex: String) {
        val storedEpochKeys = ledgerDao.getAllEpochKeys(communityIdHex)
        val communityIdBytes = hexToBytes(communityIdHex)
        for (entity in storedEpochKeys) {
            try {
                val ke = epochKeyStorageProtector.decryptKe(entity.keHex)
                val epochIdBytes = hexToBytes(entity.epochIdHex)
                epochKeyManager.storeEpochKey(
                    communityId = communityIdBytes,
                    epochId = epochIdBytes,
                    ke = ke,
                    isCanonical = entity.isCanonical
                )
            } catch (e: Exception) {
                Log.w("CommunityRepository", "Error al descifrar clave de época ${entity.epochIdHex} para comunidad $communityIdHex", e)
            }
        }
    }

    suspend fun rehydrateEpochKeys(communityIdHex: String) = mutex.withLock {
        rehydrateEpochKeysLocked(communityIdHex)
    }

    /**
     * Almacena de forma segura y cifrada una clave de época recibida (ARETE-SEC-001).
     */
    suspend fun storeEpochKey(
        communityIdHex: String,
        epochIdHex: String,
        ke: ByteArray,
        isCanonical: Boolean = true
    ) = mutex.withLock {
        val encryptedKe = epochKeyStorageProtector.encryptKe(ke)
        val entity = EpochKeyEntity(
            communityIdHex = communityIdHex,
            epochIdHex = epochIdHex,
            keHex = encryptedKe,
            isCanonical = isCanonical
        )
        ledgerDao.upsertEpochKey(entity)
        epochKeyManager.storeEpochKey(
            communityId = hexToBytes(communityIdHex),
            epochId = hexToBytes(epochIdHex),
            ke = ke,
            isCanonical = isCanonical
        )
    }

    /**
     * Crea una nueva comunidad local donde el usuario local es el Creador/Dueño.
     * Genera la operación génesis y la clave de época inicial Ke_0.
     */
    suspend fun createCommunity(
        name: String,
        description: String,
        ownerIk: ByteArray,
        deviceId: UUID,
        deviceEpoch: Int,
        dksPrivateKey: PrivateKey,
        tipo: String = "PRIVADA",
        fotoUrl: String? = null
    ): Pair<String, OperationEnvelope> = mutex.withLock {
        // Generar Ke inicial (32 bytes) y derivar epochId = SHA-256(Ke)
        val initialKe = epochKeyManager.generateEpochKey()
        val initialEpochId = MessageDigest.getInstance("SHA-256").digest(initialKe)
        val initialEpochIdHex = bytesToHex(initialEpochId)

        // Payload génesis
        val genesisBody = CommunityLedger.GenesisBody(
            name = name,
            description = description,
            initialEpochIdHex = initialEpochIdHex,
            tipo = tipo,
            fotoUrl = fotoUrl
        )
        val bodyBytes = Json.encodeToString(CommunityLedger.GenesisBody.serializer(), genesisBody).toByteArray(Charsets.UTF_8)

        // El ID de comunidad inicial es un placeholder temporal de 32 bytes para firmar
        val genesisOp = OperationEnvelope(
            version = 1,
            operationType = OperationEnvelope.TYPE_GENESIS,
            communityId = ByteArray(32),
            authorIk = ownerIk,
            deviceId = deviceId,
            deviceEpoch = deviceEpoch,
            deviceSeq = 1L,
            prevOpHash = ByteArray(32),
            ownerHeadSeq = 0L,
            ownerHeadHash = ByteArray(32),
            epochId = initialEpochId,
            timestamp = System.currentTimeMillis(),
            bodyBytes = bodyBytes,
            signatureBytes = ByteArray(0)
        )

        val signedGenesis = signatureManager.signOperation(genesisOp, dksPrivateKey)
        val communityIdBytes = signedGenesis.calculateOpId()
        val communityIdHex = bytesToHex(communityIdBytes)

        // Registrar clave canónica de la comunidad bajo su communityId real
        epochKeyManager.storeEpochKey(communityIdBytes, initialEpochId, initialKe, isCanonical = true)

        // Persistir en Room
        val communityEntity = CommunityEntity(
            communityIdHex = communityIdHex,
            name = name,
            description = description,
            ownerIkHex = bytesToHex(ownerIk),
            leaderIkHex = null,
            currentHeadSeq = 1L,
            currentHeadHashHex = communityIdHex,
            currentEpochIdHex = initialEpochIdHex,
            rulesVersion = 1,
            isDissolved = false,
            tipo = tipo,
            fotoUrl = fotoUrl
        )
        communityDao.insertCommunity(communityEntity)

        val memberEntity = CommunityMemberEntity(
            communityIdHex = communityIdHex,
            authorIkHex = bytesToHex(ownerIk),
            role = CommunityAuthority.Role.OWNER.name,
            status = "ACTIVE",
            joinedAtOwnerSeq = 1L
        )
        communityDao.upsertMember(memberEntity)

        val opEntity = envelopeToEntity(communityIdHex, signedGenesis, OwnerSpine.EvaluationStatus.CONFIRMED.name)
        ledgerDao.insertOperation(opEntity)

        val epochEntity = EpochKeyEntity(
            communityIdHex = communityIdHex,
            epochIdHex = initialEpochIdHex,
            keHex = epochKeyStorageProtector.encryptKe(initialKe),
            isCanonical = true
        )
        ledgerDao.upsertEpochKey(epochEntity)

        // Instanciar ledger en memoria
        val ledger = CommunityLedger(
            communityId = communityIdBytes,
            ownerIk = ownerIk,
            initialName = name,
            initialDescription = description
        )
        ledger.applyOperation(signedGenesis)
        activeLedgers[communityIdHex] = ledger

        Pair(communityIdHex, signedGenesis)
    }

    /**
     * Recibe y aplica una operación remota o local (sobre firmado) a una comunidad.
     */
    suspend fun applyOperation(
        communityIdHex: String,
        envelope: OperationEnvelope,
        certificate: DeviceCertificate? = null,
        authorIkPublicKey: PublicKey? = null
    ): OwnerSpine.EvaluationStatus = mutex.withLock {
        val ledger = activeLedgers.getOrPut(communityIdHex) {
            val community = communityDao.getCommunity(communityIdHex)
                ?: error("Comunidad desconocida para aplicar operación: $communityIdHex")
            CommunityLedger(
                communityId = hexToBytes(communityIdHex),
                ownerIk = hexToBytes(community.ownerIkHex),
                initialName = community.name,
                initialDescription = community.description
            )
        }

        if (certificate != null) {
            val ikPub = authorIkPublicKey ?: reconstructPublicKey(certificate.authorIk)
            if (ikPub != null) {
                signatureManager.registerCertificate(certificate, ikPub)
            }
        }

        val status = ledger.applyOperation(envelope)

        if (status != OwnerSpine.EvaluationStatus.INVALID_CUTOFF &&
            status != OwnerSpine.EvaluationStatus.INVALID_AUTHORITY
        ) {
            // Guardar en el log append-only
            val entity = envelopeToEntity(communityIdHex, envelope, status.name)
            ledgerDao.insertOperation(entity)

            // Actualizar estado de comunidad y miembros si procede
            val latestState = ledger.getState()
            communityDao.updateHead(
                communityIdHex = communityIdHex,
                headSeq = latestState.currentHeadSeq,
                headHashHex = bytesToHex(latestState.currentHeadHash)
            )

            // Actualizar miembros derivados
            for (member in latestState.members.values) {
                val memberEntity = CommunityMemberEntity(
                    communityIdHex = communityIdHex,
                    authorIkHex = bytesToHex(member.authorIk),
                    role = member.role.name,
                    status = member.status.name,
                    joinedAtOwnerSeq = member.joinedAtOwnerSeq,
                    designatedByIkHex = member.designatedByIk?.let { bytesToHex(it) }
                )
                communityDao.upsertMember(memberEntity)
            }
        }

        status
    }

    // ─── Métodos auxiliares de conversión ───

    private fun envelopeToEntity(
        communityIdHex: String,
        envelope: OperationEnvelope,
        status: String
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
            validationStatus = status
        )
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

    private fun reconstructPublicKey(raw: ByteArray): PublicKey? {
        return try {
            if (raw.size != 65 || raw[0] != 0x04.toByte()) return null
            val x = BigInteger(1, raw.copyOfRange(1, 33))
            val y = BigInteger(1, raw.copyOfRange(33, 65))
            val ecSpec = java.security.AlgorithmParameters.getInstance("EC").also {
                it.init(ECGenParameterSpec("secp256r1"))
            }.getParameterSpec(java.security.spec.ECParameterSpec::class.java)
            val kf = KeyFactory.getInstance("EC")
            kf.generatePublic(ECPublicKeySpec(ECPoint(x, y), ecSpec))
        } catch (_: Exception) {
            null
        }
    }

    private fun bytesToHex(bytes: ByteArray): String = bytes.toHex()

    private fun hexToBytes(hex: String): ByteArray = hex.hexToByteArray()
}
