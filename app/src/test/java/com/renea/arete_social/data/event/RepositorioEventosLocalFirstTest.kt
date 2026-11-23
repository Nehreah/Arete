package com.renea.arete_social.data.event

import com.renea.arete_social.data.identity.IdentityManager
import com.renea.arete_social.data.local.room.AppLocalDatabase
import com.renea.arete_social.data.local.room.dao.EventDao
import com.renea.arete_social.data.local.room.entity.EventEntity
import com.renea.arete_social.domain.model.Ubicacion
import com.renea.arete_social.domain.model.VisibilidadEvento
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class RepositorioEventosLocalFirstTest {

    private lateinit var database: AppLocalDatabase
    private lateinit var eventDao: EventDao
    private lateinit var identityManager: IdentityManager
    private lateinit var localRepo: EventRepositoryLocal
    private lateinit var repository: RepositorioEventosLocalFirst

    private val testWords = listOf(
        "abandon", "ability", "able", "about", "above", "absent",
        "absorb", "abstract", "absurd", "abuse", "access", "accident"
    )

    @Before
    fun setUp() {
        database = mockk(relaxed = true)
        eventDao = mockk(relaxed = true)
        val participantDao = mockk<com.renea.arete_social.data.local.room.dao.EventParticipantDao>(relaxed = true)
        every { database.eventDao() } returns eventDao
        every { database.eventParticipantDao() } returns participantDao

        identityManager = IdentityManager()
        identityManager.loadFromPhrase(testWords)

        localRepo = EventRepositoryLocal(database, identityManager)
        repository = RepositorioEventosLocalFirst(localRepo, database, remoteRepo = null)
    }

    @Test
    fun testCrearYObtenerEventoLocalFirst() = runBlocking {
        val ubicacion = Ubicacion(latitud = 3.4516, longitud = -76.5320)
        coEvery { eventDao.upsertEvent(any()) } returns Unit

        val evento = repository.crear(
            nombre = "Feria del Libro y Poesía Mesh",
            descripcion = "Encuentro de literatura local",
            fecha = 1_750_000_000_000L,
            lugarId = null,
            creadorId = "creador_local",
            comunidadId = null,
            ubicacion = ubicacion,
            visibilidad = VisibilidadEvento.PUBLICO
        )

        assertNotNull(evento.id)
        assertEquals("Feria del Libro y Poesía Mesh", evento.nombre)
        assertEquals(3.4516, evento.ubicacion.latitud, 0.0001)

        // Mock para obtenerPorId
        val entity = EventEntity(
            id = evento.id,
            organizerIkHex = "organizador_hex",
            title = evento.nombre,
            description = evento.descripcion,
            dateTimeMs = evento.fecha,
            latitude = ubicacion.latitud,
            longitude = ubicacion.longitud,
            locationName = "Cali",
            privacy = VisibilidadEvento.PUBLICO.name
        )
        coEvery { eventDao.getEvent(evento.id) } returns entity

        val obtenido = repository.obtenerPorId(evento.id)
        assertNotNull(obtenido)
        assertEquals(evento.nombre, obtenido?.nombre)
        assertEquals(VisibilidadEvento.PUBLICO, obtenido?.visibilidad)
    }
}
