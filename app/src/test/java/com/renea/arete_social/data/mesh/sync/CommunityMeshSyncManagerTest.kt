package com.renea.arete_social.data.mesh.sync

import android.util.Base64
import com.renea.arete_social.data.local.room.AppLocalDatabase
import com.renea.arete_social.data.local.room.dao.CommunityDao
import com.renea.arete_social.data.local.room.dao.LedgerDao
import com.renea.arete_social.data.local.room.entity.LedgerOperationEntity
import com.renea.arete_social.data.protocol.OperationEnvelope
import com.renea.arete_social.domain.mesh.MessageRouter
import com.renea.arete_social.domain.mesh.PaqueteMesh
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import com.renea.arete_social.data.protocol.OperationValidator
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityMeshSyncManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val database: AppLocalDatabase = mockk()
    private val ledgerDao: LedgerDao = mockk()
    private val communityDao: CommunityDao = mockk(relaxed = true)
    private val messageRouter: MessageRouter = mockk(relaxed = true)

    private val communityIdHex = "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899"
    private val communityId = communityIdHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private lateinit var syncManager: CommunityMeshSyncManager

    @Before
    fun setUp() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg<ByteArray>())
        }
        every { Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }

        mockkObject(OperationValidator)
        every { OperationValidator.reconstructPublicKey(any()) } returns mockk()
        every { OperationValidator.verifySignature(any(), any()) } returns true

        every { database.ledgerDao() } returns ledgerDao
        every { database.communityDao() } returns communityDao

        syncManager = CommunityMeshSyncManager(
            database = database,
            messageRouter = messageRouter,
            miUsuarioIdProveedor = { "local-user-123" },
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        unmockkObject(OperationValidator)
    }

    @Test
    fun testEmitirResumenInventarioEnviaPaqueteMesh() = runTest(testDispatcher) {
        val mockHead = LedgerOperationEntity(
            opIdHex = "op123",
            communityIdHex = communityIdHex,
            operationType = 1,
            authorIkHex = "04".padEnd(130, '0'),
            deviceId = UUID.randomUUID().toString(),
            deviceEpoch = 1,
            deviceSeq = 5L,
            prevOpHashHex = "00".repeat(32),
            ownerHeadSeq = 5L,
            ownerHeadHashHex = "11".repeat(32),
            epochIdHex = "22".repeat(32),
            timestampMs = 1_000_000L,
            bodyHex = "deadbeef",
            signatureHex = "cafe"
        )
        coEvery { ledgerDao.getOwnerHead(communityIdHex) } returns mockHead

        syncManager.emitirResumenInventario(communityId)
        advanceUntilIdle()

        coVerify {
            messageRouter.reenviar(match { paquete ->
                paquete.chatId == "${CommunityMeshSyncManager.CHAT_PREFIX}$communityIdHex" &&
                paquete.remitenteId == "local-user-123" &&
                paquete.contenido.startsWith(CommunityMeshSyncManager.PREFIX_SUMMARY)
            })
        }
    }

    @Test
    fun testProcesarResumenRemotoMasAvanzadoSolicitaOperaciones() = runTest(testDispatcher) {
        // Cabeza local en seq = 2
        val mockHead = LedgerOperationEntity(
            opIdHex = "oplocal",
            communityIdHex = communityIdHex,
            operationType = 1,
            authorIkHex = "04".padEnd(130, '0'),
            deviceId = UUID.randomUUID().toString(),
            deviceEpoch = 1,
            deviceSeq = 2L,
            prevOpHashHex = "00".repeat(32),
            ownerHeadSeq = 2L,
            ownerHeadHashHex = "11".repeat(32),
            epochIdHex = "22".repeat(32),
            timestampMs = 1_000_000L,
            bodyHex = "deadbeef",
            signatureHex = "cafe"
        )
        coEvery { ledgerDao.getOwnerHead(communityIdHex) } returns mockHead

        // Par remoto anuncia cabeza en seq = 10
        val remoteSummary = CommunitySyncProtocol.InventorySummary(
            communityIdHex = communityIdHex,
            ownerHeadSeq = 10L,
            ownerHeadHashHex = "33".repeat(32)
        )
        val summaryBytes = CommunitySyncProtocol.serializeSummary(remoteSummary)
        val base64 = Base64.encodeToString(summaryBytes, Base64.NO_WRAP)

        val paqueteEntrante = PaqueteMesh(
            id = UUID.randomUUID().toString(),
            chatId = "${CommunityMeshSyncManager.CHAT_PREFIX}$communityIdHex",
            remitenteId = "remote-peer-456",
            contenido = "${CommunityMeshSyncManager.PREFIX_SUMMARY}$base64"
        )

        val procesado = syncManager.procesarPaqueteMesh(paqueteEntrante)
        advanceUntilIdle()

        assertTrue(procesado)
        coVerify {
            messageRouter.reenviar(match { reqPaquete ->
                reqPaquete.chatId == paqueteEntrante.chatId &&
                reqPaquete.destinatarioId == "remote-peer-456" &&
                reqPaquete.contenido.startsWith(CommunityMeshSyncManager.PREFIX_REQ)
            })
        }
    }

    @Test
    fun testProcesarRespuestaEmiteEventoOperacionesRecibidas() = runTest(testDispatcher) {
        val opsHexList = (1..3).map { seq ->
            val env = OperationEnvelope(
                version = 1,
                operationType = OperationEnvelope.TYPE_CONTENT_POST,
                communityId = communityId,
                authorIk = ByteArray(65) { 0x04 },
                deviceId = UUID.randomUUID(),
                deviceEpoch = 1,
                deviceSeq = seq.toLong(),
                prevOpHash = ByteArray(32),
                ownerHeadSeq = seq.toLong(),
                ownerHeadHash = ByteArray(32),
                epochId = ByteArray(32),
                timestamp = System.currentTimeMillis(),
                bodyBytes = "test post $seq".toByteArray(Charsets.UTF_8),
                signatureBytes = ByteArray(64) { 0x01 }
            )
            env.encodeFullOperation().joinToString("") { "%02x".format(it) }
        }

        coEvery { ledgerDao.getOperation(any()) } returns null
        coEvery { ledgerDao.insertOperation(any()) } returns 1L

        val resp = CommunitySyncProtocol.InventoryResponse(
            communityIdHex = communityIdHex,
            operationsHex = opsHexList
        )
        val respBytes = CommunitySyncProtocol.serializeResponse(resp)
        val base64 = Base64.encodeToString(respBytes, Base64.NO_WRAP)

        val paqueteResp = PaqueteMesh(
            id = UUID.randomUUID().toString(),
            chatId = "${CommunityMeshSyncManager.CHAT_PREFIX}$communityIdHex",
            remitenteId = "remote-peer-456",
            contenido = "${CommunityMeshSyncManager.PREFIX_RESP}$base64"
        )

        // Suscribir ANTES de procesar para no perder la emisión
        val eventoDeferred = async { syncManager.eventosSincronizacion.first() }
        val procesado = syncManager.procesarPaqueteMesh(paqueteResp)
        advanceUntilIdle()

        assertTrue(procesado)
        val evento = eventoDeferred.await()
        assertTrue(evento is CommunityMeshSyncManager.SyncEvent.OperacionesRecibidas)
        val opsRecibidas = evento as CommunityMeshSyncManager.SyncEvent.OperacionesRecibidas
        assertEquals(3, opsRecibidas.cantidad)
        assertEquals(communityIdHex, opsRecibidas.communityIdHex)
    }
}
