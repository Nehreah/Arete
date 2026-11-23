package com.renea.arete_social.data.mesh.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class BleCommunityDiscoveryTest {

    private val random = SecureRandom()

    @Test
    fun testTagGenerationAndMatchingCurrentWindow() {
        val ke = ByteArray(32).also { random.nextBytes(it) }
        val now = 1_700_000_000_000L

        val tag = BleCommunityDiscovery.generateEphemeralTag(ke, now)
        assertEquals(16, tag.size)

        // El tag debe coincidir en el mismo timestamp
        assertTrue(BleCommunityDiscovery.matchesCommunity(tag, ke, now))
    }

    @Test
    fun testTagToleranceAdjacentWindow() {
        val ke = ByteArray(32).also { random.nextBytes(it) }
        val now = 1_700_000_000_000L

        val tag = BleCommunityDiscovery.generateEphemeralTag(ke, now)

        // 10 minutos después (dentro de la misma o siguiente ventana con tolerancia)
        val later10m = now + 600_000L
        assertTrue("Debe coincidir con 10 min de diferencia", BleCommunityDiscovery.matchesCommunity(tag, ke, later10m))

        // 14 minutos antes (dentro de ventana anterior con tolerancia)
        val earlier14m = now - 840_000L
        assertTrue("Debe coincidir con 14 min hacia atrás", BleCommunityDiscovery.matchesCommunity(tag, ke, earlier14m))

        // 45 minutos después (fuera de la ventana de tolerancia de +/- 15 min)
        val later45m = now + (45 * 60_000L)
        assertFalse("No debe coincidir tras 45 minutos de desvío", BleCommunityDiscovery.matchesCommunity(tag, ke, later45m))
    }

    @Test
    fun testDifferentKeDoesNotMatch() {
        val ke1 = ByteArray(32).also { random.nextBytes(it) }
        val ke2 = ByteArray(32).also { random.nextBytes(it) }
        val now = 1_700_000_000_000L

        val tag1 = BleCommunityDiscovery.generateEphemeralTag(ke1, now)
        assertFalse("Un tag de ke1 no debe coincidir con ke2", BleCommunityDiscovery.matchesCommunity(tag1, ke2, now))
    }
}
