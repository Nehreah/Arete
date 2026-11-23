package com.renea.arete_social.data.local.perfil

import com.renea.arete_social.data.local.room.dao.PeerProfileDao
import com.renea.arete_social.data.local.room.entity.PeerProfileEntity
import com.renea.arete_social.domain.model.SnapshotPerfil
import com.renea.arete_social.domain.repository.ResolverPerfiles
import com.renea.arete_social.testutils.FakeSharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ResolverPerfilesTest {

    private class FakePeerProfileDao : PeerProfileDao {
        val peers = mutableMapOf<String, PeerProfileEntity>()
        val flow = MutableStateFlow<Map<String, PeerProfileEntity>>(emptyMap())

        override suspend fun getPeer(usuarioId: String): PeerProfileEntity? = peers[usuarioId]

        override fun observePeer(usuarioId: String): Flow<PeerProfileEntity?> =
            flow.map { it[usuarioId] }

        override fun observeAllPeers(): Flow<List<PeerProfileEntity>> =
            flow.map { it.values.toList() }

        override suspend fun insertOrUpdate(peer: PeerProfileEntity) {
            peers[peer.usuarioId] = peer
            flow.value = peers.toMap()
        }

        override suspend fun updateLastSeen(usuarioId: String, timestamp: Long) {
            peers[usuarioId]?.let {
                val updated = it.copy(ultimaVezVisto = timestamp)
                peers[usuarioId] = updated
                flow.value = peers.toMap()
            }
        }

        override suspend fun deletePeer(usuarioId: String) {
            peers.remove(usuarioId)
            flow.value = peers.toMap()
        }

        override suspend fun podarDesconocidosAntiguos(limiteTimestamp: Long) {
            peers.entries.removeIf { !it.value.esAmigo && it.value.ultimaVezVisto < limiteTimestamp }
            flow.value = peers.toMap()
        }

        override suspend fun getAllPeers(): List<PeerProfileEntity> = peers.values.toList()

        override suspend fun getRecentPeers(limiteTimestamp: Long): List<PeerProfileEntity> =
            peers.values.filter { it.ultimaVezVisto >= limiteTimestamp }
    }

    private lateinit var fakeDao: FakePeerProfileDao
    private lateinit var cacheUsuarios: CacheUsuariosLocal
    private lateinit var cachePerfil: CachePerfilLocal
    private lateinit var resolver: ResolverPerfiles

    @Before
    fun setUp() {
        fakeDao = FakePeerProfileDao()
        val prefsUsuarios = FakeSharedPreferences()
        val prefsPerfil = FakeSharedPreferences()
        cacheUsuarios = CacheUsuariosLocal(prefsUsuarios)
        cachePerfil = CachePerfilLocal(prefsPerfil)
        resolver = ResolverPerfilesLocal(fakeDao, cacheUsuarios, cachePerfil)
    }

    @Test
    fun `resuelve Alguien si el usuario no existe`() = runBlocking {
        assertEquals("Alguien", resolver.resolverNombre("desconocido"))
        assertNull(resolver.resolverFoto("desconocido"))
    }

    @Test
    fun `registra par descubierto y resuelve su nombre`() = runBlocking {
        resolver.registrarParDescubierto("u1", "Carlos")
        assertEquals("Carlos", resolver.resolverNombre("u1"))
        val peer = resolver.obtenerPerfil("u1")
        assertNotNull(peer)
        assertEquals("Carlos", peer?.nombre)
    }

    @Test
    fun `no pisa un nombre completo con un nombre provisional mas corto o cargando`() = runBlocking {
        resolver.registrarParDescubierto("u2", "Sebastián Pérez")
        resolver.registrarParDescubierto("u2", "Sebastián P")
        assertEquals("Sebastián Pérez", resolver.resolverNombre("u2"))

        resolver.registrarParDescubierto("u2", "Cargando…")
        assertEquals("Sebastián Pérez", resolver.resolverNombre("u2"))
    }

    @Test
    fun `registra perfil recibido por BLE y actualiza foto y descripcion`() = runBlocking {
        val snapshot = SnapshotPerfil(
            usuarioId = "u3",
            nombre = "María Gómez",
            descripcion = "Investigadora Mesh",
            foto = "/local/path/avatar_u3.jpg",
            portada = "/local/path/portada_u3.jpg",
            fotoHash = "hash123",
            privado = false
        )
        resolver.registrarPerfilRecibido(snapshot)

        assertEquals("María Gómez", resolver.resolverNombre("u3"))
        assertEquals("/local/path/avatar_u3.jpg", resolver.resolverFoto("u3"))
        val perfil = resolver.obtenerPerfil("u3")
        assertEquals("Investigadora Mesh", perfil?.descripcion)
    }

    @Test
    fun `interaccion de mensaje actualiza par en Room`() = runBlocking {
        resolver.registrarInteraccionMensaje("u4", "Lucas", "/avatar_lucas.jpg")
        assertEquals("Lucas", resolver.resolverNombre("u4"))
        assertEquals("/avatar_lucas.jpg", resolver.resolverFoto("u4"))
    }
}
