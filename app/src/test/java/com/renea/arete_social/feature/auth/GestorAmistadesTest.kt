package com.renea.arete_social.feature.auth

import com.renea.arete_social.domain.model.SolicitudAmistad
import com.renea.arete_social.domain.repository.AmistadesServidor
import com.renea.arete_social.testutils.FakeSharedPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pruebas unitarias de seguridad para [GestorAmistades] (H-01).
 *
 * Criterio de salida del Plan de Seguridad:
 * - A inserta RESPUESTA_AMISTAD hacia B sin solicitud o aviso directo: B no queda como amigo.
 * - Con cuenta, el estado de amistad lo decide única y autoritativamente el servidor.
 * - reemplazarDesdeServidor con cuenta reemplaza estrictamente sin unión para no retener amigos locales fraudulentos.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GestorAmistadesTest {

    private val fakePrefs = FakeSharedPreferences()

    @Before
    fun setUp() {
        GestorAmistades.limpiar()
    }

    @After
    fun tearDown() {
        GestorAmistades.limpiar()
    }

    @Test
    fun `H-01 con cuenta - recibirAvisoRespuesta no agrega amigo localmente y solicita sincronizacion`() = runTest {
        GestorAmistades.inicializar(usuarioId = "usuario-autenticado-123", prefsOverride = fakePrefs)

        var sincronizacionPedida = false
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            GestorAmistades.sincronizacionPedida.collect {
                sincronizacionPedida = true
            }
        }

        // Simular que llega un aviso directo de que el atacante "aceptó" la amistad
        GestorAmistades.recibirAvisoRespuesta(otroId = "atacante-999", aceptada = true)

        assertFalse(
            "Con cuenta, un aviso directo jamás debe añadir al amigo localmente",
            GestorAmistades.esAmigo("atacante-999")
        )
        assertTrue(
            "Con cuenta, debe solicitarse sincronización autoritativa con el servidor",
            sincronizacionPedida
        )

        job.cancel()
    }

    @Test
    fun `H-01 con cuenta - reemplazarDesdeServidor reemplaza estrictamente sin union`() = runTest {
        GestorAmistades.inicializar(usuarioId = "usuario-autenticado-123", prefsOverride = fakePrefs)

        // Supongamos que en el servidor solo existen amigos legítimos
        val datosServidor = AmistadesServidor(
            amigosIds = setOf("amigo-real-1", "amigo-real-2"),
            solicitudesRecibidas = emptyList(),
            solicitudesEnviadasIds = emptySet()
        )

        GestorAmistades.reemplazarDesdeServidor(datosServidor)

        assertTrue(GestorAmistades.esAmigo("amigo-real-1"))
        assertTrue(GestorAmistades.esAmigo("amigo-real-2"))
        assertFalse(GestorAmistades.esAmigo("atacante-999"))

        // Si el servidor luego devuelve una lista donde amigo-real-2 fue eliminado
        val datosActualizados = AmistadesServidor(
            amigosIds = setOf("amigo-real-1"),
            solicitudesRecibidas = emptyList(),
            solicitudesEnviadasIds = emptySet()
        )

        GestorAmistades.reemplazarDesdeServidor(datosActualizados)

        assertTrue(GestorAmistades.esAmigo("amigo-real-1"))
        assertFalse(
            "Amigo revocado por el servidor no debe permanecer en el cliente",
            GestorAmistades.esAmigo("amigo-real-2")
        )
    }

    @Test
    fun `modo invitado offline - solo acepta si existia solicitud previa enviada`() {
        GestorAmistades.inicializar(usuarioId = "invitado", prefsOverride = fakePrefs)

        // Aviso directo sin solicitud enviada previa
        GestorAmistades.recibirAvisoRespuesta(otroId = "desconocido-1", aceptada = true)
        assertFalse(
            "En modo invitado, no se acepta amistad si no había solicitud previa enviada",
            GestorAmistades.esAmigo("desconocido-1")
        )
    }
}
