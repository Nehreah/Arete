package com.renea.arete_social.data.event

import com.renea.arete_social.data.identity.IdentityManager
import com.renea.arete_social.data.local.room.AppLocalDatabase
import com.renea.arete_social.data.local.room.dao.EventDao
import com.renea.arete_social.data.local.room.entity.EventEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EventRepositoryLocalTest {

    private lateinit var database: AppLocalDatabase
    private lateinit var eventDao: EventDao
    private lateinit var identityManager: IdentityManager
    private lateinit var repository: EventRepositoryLocal

    private val testWords = listOf(
        "abandon", "ability", "able", "about", "above", "absent",
        "absorb", "abstract", "absurd", "abuse", "access", "accident"
    )

    @Before
    fun setUp() {
        database = mockk()
        eventDao = mockk(relaxed = true)
        every { database.eventDao() } returns eventDao

        identityManager = IdentityManager()
        identityManager.loadFromPhrase(testWords)

        repository = EventRepositoryLocal(database, identityManager)
    }

    @Test
    fun testCreateEvent() = runBlocking {
        val eventSlot = slot<EventEntity>()
        coEvery { eventDao.upsertEvent(capture(eventSlot)) } returns Unit

        val localEvent = repository.createEvent(
            title = "Encuentro Hacker P2P",
            description = "Taller de redes mesh y descentralización",
            dateTimeMs = 1_750_000_000_000L,
            latitude = 3.4516,
            longitude = -76.5320,
            locationName = "Cali, Colombia",
            privacy = "COMMUNITY"
        )

        assertNotNull(localEvent.id)
        assertEquals("Encuentro Hacker P2P", localEvent.title)
        assertEquals("Cali, Colombia", localEvent.locationName)
        assertEquals(identityManager.ikPublicBytes.joinToString("") { "%02x".format(it) }, localEvent.organizerIkHex)

        coVerify(exactly = 1) { eventDao.upsertEvent(any()) }
        assertTrue(eventSlot.isCaptured)
        assertEquals(localEvent.id, eventSlot.captured.id)
    }

    @Test
    fun testCancelEventAuthorized() = runBlocking {
        val organizerIkHex = identityManager.ikPublicBytes.joinToString("") { "%02x".format(it) }
        val testEntity = EventEntity(
            id = "event-1234",
            organizerIkHex = organizerIkHex,
            title = "Evento de Prueba",
            description = "Desc",
            dateTimeMs = 1_750_000_000_000L
        )

        coEvery { eventDao.getEvent("event-1234") } returns testEntity
        coEvery { eventDao.cancelEvent("event-1234", any()) } returns Unit

        val success = repository.cancelEvent("event-1234", organizerIkHex)
        assertTrue("El organizador debe poder cancelar el evento", success)
        coVerify(exactly = 1) { eventDao.cancelEvent("event-1234", any()) }
    }

    @Test
    fun testCancelEventUnauthorizedRejected() = runBlocking {
        val organizerIkHex = identityManager.ikPublicBytes.joinToString("") { "%02x".format(it) }
        val testEntity = EventEntity(
            id = "event-1234",
            organizerIkHex = organizerIkHex,
            title = "Evento de Prueba",
            description = "Desc",
            dateTimeMs = 1_750_000_000_000L
        )

        coEvery { eventDao.getEvent("event-1234") } returns testEntity

        val success = repository.cancelEvent("event-1234", "otro_ik_atacante_0000")
        assertFalse("Un tercero no organizador no debe poder cancelar el evento", success)
        coVerify(exactly = 0) { eventDao.cancelEvent(any(), any()) }
    }
}
