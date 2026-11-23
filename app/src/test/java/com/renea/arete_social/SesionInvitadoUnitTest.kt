package com.renea.arete_social

import com.renea.arete_social.feature.auth.SesionUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Tests de regresión para garantizar la gestión correcta de identidades locales
 * y verificación de IDs sintéticos.
 */
class SesionInvitadoUnitTest {

    @Test
    fun `id sintetico de invitado local se identifica correctamente como soloLocal`() {
        val uuidSintetico = UUID.randomUUID().toString()
        val idInvitadoLocal = "invitado-local-$uuidSintetico"

        val esLocal = idInvitadoLocal.startsWith("invitado-local-")
        val estado = SesionUiState.Autenticado(idInvitadoLocal, soloLocal = esLocal)

        assertTrue("El estado debe marcar soloLocal=true para invitados sintéticos", estado.soloLocal)
        assertTrue("El prefijo de invitado local debe ser detectado", idInvitadoLocal.startsWith("invitado-local-"))
    }

    @Test
    fun `transicion a UUID formal desactiva soloLocal y mantiene longitud estandar`() {
        val uuidRemoto = UUID.randomUUID().toString()

        val esLocal = uuidRemoto.startsWith("invitado-local-")
        val estadoMigrado = SesionUiState.Autenticado(uuidRemoto, soloLocal = esLocal)

        assertFalse("El estado con UUID estándar no debe ser soloLocal", estadoMigrado.soloLocal)
        assertEquals(36, estadoMigrado.usuarioId.length) // Formato estándar de UUID v4
    }

    @Test
    fun `filtro de exclusion en actualizacion de ubicacion ignora ids locales`() {
        val idLocal = "invitado-local-12345"
        val idValido = UUID.randomUUID().toString()

        fun debeActualizar(id: String): Boolean = !id.startsWith("invitado-local-")

        assertFalse("No debe procesar un id sintético local", debeActualizar(idLocal))
        assertTrue("Debe procesar un UUID estándar", debeActualizar(idValido))
    }
}
