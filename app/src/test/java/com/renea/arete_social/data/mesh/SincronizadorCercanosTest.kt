package com.renea.arete_social.data.mesh

import com.renea.arete_social.data.local.perfil.CachePerfilLocal
import com.renea.arete_social.data.local.room.dao.PeerProfileDao
import com.renea.arete_social.data.local.room.entity.PeerProfileEntity
import com.renea.arete_social.domain.mesh.MessageRouter
import com.renea.arete_social.domain.mesh.PaqueteMesh
import com.renea.arete_social.domain.mesh.PeerConnection
import com.renea.arete_social.domain.model.FeatureFlags
import com.renea.arete_social.domain.repository.ResolverPerfiles
import com.renea.arete_social.data.local.perfil.ResolverPerfilesLocal
import com.renea.arete_social.testutils.FakeSharedPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SincronizadorCercanosTest {

    private class RouterFalso : MessageRouter {
        val entrantes = MutableSharedFlow<PaqueteMesh>(extraBufferCapacity = 16)
        val enviados = mutableListOf<PaqueteMesh>()
        val peers = MutableStateFlow(1)
        override suspend fun enrutar(paquete: PaqueteMesh, origenMac: String?) {}
        override suspend fun reenviar(paquete: PaqueteMesh) { enviados += paquete }
        override fun paquetesEntrantes(chatId: String): Flow<PaqueteMesh> = emptyFlow()
        override fun todosLosPaquetesEntrantes(): Flow<PaqueteMesh> = entrantes
        override fun paquetesParaMi(miId: String): Flow<PaqueteMesh> = emptyFlow()
        override val peersConectados: StateFlow<Int> get() = peers.asStateFlow()
        override fun registrarPeer(peer: PeerConnection) {}
        override fun removerPeer(dispositivoId: String) {}
    }

    private class FakePeerDao : PeerProfileDao {
        val list = mutableListOf<PeerProfileEntity>()
        override suspend fun getPeer(usuarioId: String): PeerProfileEntity? = list.firstOrNull { it.usuarioId == usuarioId }
        override fun observePeer(usuarioId: String): Flow<PeerProfileEntity?> = emptyFlow()
        override fun observeAllPeers(): Flow<List<PeerProfileEntity>> = emptyFlow()
        override suspend fun insertOrUpdate(peer: PeerProfileEntity) { list.add(peer) }
        override suspend fun updateLastSeen(usuarioId: String, timestamp: Long) {}
        override suspend fun deletePeer(usuarioId: String) {}
        override suspend fun podarDesconocidosAntiguos(limiteTimestamp: Long) {
            list.removeIf { !it.esAmigo && it.ultimaVezVisto < limiteTimestamp }
        }
        override suspend fun getAllPeers(): List<PeerProfileEntity> = list.toList()
        override suspend fun getRecentPeers(limiteTimestamp: Long): List<PeerProfileEntity> =
            list.filter { it.ultimaVezVisto >= limiteTimestamp }
    }

    @Test
    fun `no realiza solicitudes si flag SYNC_AUTO esta inactivo`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val router = RouterFalso()
        val cache = CachePerfilLocal(FakeSharedPreferences())
        val gestorBle = GestorPerfilBle(router, cache, { "yo" }, { false }, scope = testScope)
        val resolver = ResolverPerfilesLocal(FakePeerDao(), null, cache)

        val sincronizador = SincronizadorCercanos(
            gestorPerfilBle = gestorBle,
            resolverPerfiles = resolver,
            miIdProveedor = { "yo" },
            esAmigo = { false },
            scope = testScope
        )

        FeatureFlags.SYNC_AUTO = false
        sincronizador.onParDetectado("vecino-1")
        testDispatcher.scheduler.advanceTimeBy(3000)

        assertTrue(router.enviados.isEmpty())
    }

    @Test
    fun `solicita perfil si flag SYNC_AUTO esta activo y respeta cooldown`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val router = RouterFalso()
        val cache = CachePerfilLocal(FakeSharedPreferences())
        val gestorBle = GestorPerfilBle(router, cache, { "yo" }, { true }, scope = testScope)
        val resolver = ResolverPerfilesLocal(FakePeerDao(), null, cache)

        val sincronizador = SincronizadorCercanos(
            gestorPerfilBle = gestorBle,
            resolverPerfiles = resolver,
            miIdProveedor = { "yo" },
            esAmigo = { true },
            scope = testScope
        )

        FeatureFlags.SYNC_AUTO = true
        try {
            sincronizador.onParDetectado("amigo-1")
            testDispatcher.scheduler.advanceTimeBy(1000)
            assertEquals(1, router.enviados.size)

            // Petición repetida antes de 10 min no vuelve a enviar
            sincronizador.onParDetectado("amigo-1")
            testDispatcher.scheduler.advanceTimeBy(1000)
            assertEquals(1, router.enviados.size)
        } finally {
            FeatureFlags.SYNC_AUTO = false
        }
    }
}
