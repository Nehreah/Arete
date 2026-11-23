package com.renea.arete_social.data.community

import com.renea.arete_social.data.identity.DeviceKeyManager
import com.renea.arete_social.data.identity.IdentityManager
import com.renea.arete_social.data.local.room.entity.CommunityEntity
import com.renea.arete_social.domain.model.Comunidad
import com.renea.arete_social.domain.repository.RepositorioComunidades
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RepositorioComunidadesLocalFirstTest {

    private lateinit var localRepo: CommunityRepositoryLocal
    private lateinit var remoteRepo: RepositorioComunidades
    private lateinit var identityManager: IdentityManager
    private lateinit var deviceKeyManager: DeviceKeyManager
    private lateinit var repository: RepositorioComunidadesLocalFirst

    private val testWords = listOf(
        "abandon", "ability", "able", "about", "above", "absent",
        "absorb", "abstract", "absurd", "abuse", "access", "accident"
    )

    @Before
    fun setUp() {
        localRepo = mockk(relaxed = true)
        remoteRepo = mockk(relaxed = true)
        identityManager = IdentityManager().apply { loadFromPhrase(testWords) }
        deviceKeyManager = DeviceKeyManager(keyStoreProvider = "CustomNonAndroidProvider")

        repository = RepositorioComunidadesLocalFirst(
            localRepo = localRepo,
            remoteRepo = remoteRepo,
            identityManager = identityManager,
            deviceKeyManager = deviceKeyManager,
            featureFlagP2p = true
        )
    }

    @Test
    fun testCrearComunidadLocalFirst() = runBlocking {
        coEvery {
            localRepo.createCommunity(any(), any(), any(), any(), any(), any())
        } returns Pair("comm_id_local_1234", mockk(relaxed = true))

        val creada = repository.crearComunidad(
            nombre = "Cripto Comuna Cali",
            descripcion = "Comunidad soberana P2P",
            tipo = "PRIVADA"
        )

        assertNotNull(creada)
        assertEquals("comm_id_local_1234", creada.id)
        assertEquals("Cripto Comuna Cali", creada.nombre)

        // Verificamos que se llamó al repositorio local soberano y NUNCA al remoto
        coVerify(exactly = 1) { localRepo.createCommunity("Cripto Comuna Cali", "Comunidad soberana P2P", any(), any(), any(), any()) }
        coVerify(exactly = 0) { remoteRepo.crearComunidad(any(), any(), any(), any(), any()) }
    }

    @Test
    fun testListarComunidadesCombinesLocalesAndRemotas() = runBlocking {
        val remota = Comunidad(
            id = "remota-1",
            nombre = "Pública Remota",
            descripcion = "En servidor",
            tipo = "PUBLICA",
            chatId = "remota-1"
        )
        val local = CommunityEntity(
            communityIdHex = "local-1",
            name = "Privada Local",
            description = "En Room",
            ownerIkHex = "aabb",
            leaderIkHex = null
        )

        coEvery { remoteRepo.listarComunidades() } returns listOf(remota)
        coEvery { localRepo.getAllCommunities() } returns listOf(local)

        val lista = repository.listarComunidades()
        assertEquals(2, lista.size)
        assertTrue(lista.any { it.id == "remota-1" })
        assertTrue(lista.any { it.id == "local-1" })
    }

    @Test
    fun testListarComunidadesOfflineGracefulDegradation() = runBlocking {
        val local = CommunityEntity(
            communityIdHex = "local-1",
            name = "Privada Local Offline",
            description = "En Room",
            ownerIkHex = "aabb",
            leaderIkHex = null
        )

        // Simular fallo de red hacia remoto
        coEvery { remoteRepo.listarComunidades() } throws RuntimeException("Sin conexión")
        coEvery { localRepo.getAllCommunities() } returns listOf(local)

        val lista = repository.listarComunidades()
        assertEquals(1, lista.size)
        assertEquals("local-1", lista[0].id)
        assertEquals("Privada Local Offline", lista[0].nombre)
    }

    @Test
    fun testObtenerComunidadLocalPrecedence() = runBlocking {
        val local = CommunityEntity(
            communityIdHex = "local-target",
            name = "Comunidad Encontrada Local",
            description = "Room",
            ownerIkHex = "aabb",
            leaderIkHex = null
        )

        coEvery { localRepo.getCommunity("local-target") } returns local

        val encontrada = repository.obtenerComunidad("local-target")
        assertNotNull(encontrada)
        assertEquals("local-target", encontrada?.id)
        assertEquals("Comunidad Encontrada Local", encontrada?.nombre)

        // No debe consultar al repositorio remoto si ya existe en el almacenamiento local
        coVerify(exactly = 0) { remoteRepo.obtenerComunidad("local-target") }
    }
}
