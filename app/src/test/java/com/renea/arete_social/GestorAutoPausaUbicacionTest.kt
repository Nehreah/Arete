package com.renea.arete_social

import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.renea.arete_social.feature.ubicacion.GestorAutoPausaUbicacion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica el comportamiento del GestorAutoPausaUbicacion:
 *  - Al pasar a background y expirar el tiempo, activa la pausa automáticamente.
 *  - Si el usuario vuelve a foreground antes de expirar el tiempo, se cancela el temporizador.
 *  - Tras la pausa, volver a foreground restablece el estado y notifica la reanudación.
 *  - Si la función está desactivada (0 min), no se arma el temporizador.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GestorAutoPausaUbicacionTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val dummyContext: Context = ContextWrapper(null)
    private val dummyOwner = object : LifecycleOwner {
        override val lifecycle: Lifecycle get() = throw UnsupportedOperationException()
    }

    @Test
    fun `al expirar el tiempo en background se activa la pausa y se notifica`() = scope.runTest {
        var notificadoMinutos: Int? = null
        var pausaLlamada = false

        val gestor = GestorAutoPausaUbicacion(
            context = dummyContext,
            scope = this,
            proveedorMinutos = { 15 },
            notificador = { notificadoMinutos = it },
            canceladorNotificacion = {},
            onPausarUbicacion = { pausaLlamada = true },
            onReanudarUbicacion = null
        )

        gestor.onStop(dummyOwner)
        assertFalse("Aún no debe estar pausado justo después de onStop", gestor.estaPausado())

        // Avanzar el tiempo virtual a los 15 minutos (15 * 60_000 ms)
        advanceTimeBy(15 * 60_000L + 10)

        assertTrue("La ubicación debe estar pausada tras expirar los 15 min", gestor.estaPausado())
        assertEquals(15, notificadoMinutos)
        assertTrue(pausaLlamada)
    }

    @Test
    fun `si el usuario vuelve antes de expirar, no se pausa la ubicacion`() = scope.runTest {
        var notificadoMinutos: Int? = null

        val gestor = GestorAutoPausaUbicacion(
            context = dummyContext,
            scope = this,
            proveedorMinutos = { 30 },
            notificador = { notificadoMinutos = it },
            canceladorNotificacion = {},
            onPausarUbicacion = null,
            onReanudarUbicacion = null
        )

        gestor.onStop(dummyOwner)
        // Pasan solo 10 minutos
        advanceTimeBy(10 * 60_000L)

        // El usuario regresa a la app
        gestor.onStart(dummyOwner)

        // Pasan otros 30 minutos
        advanceTimeBy(30 * 60_000L)

        assertFalse("No debería haberse pausado porque el usuario regresó a tiempo", gestor.estaPausado())
        assertEquals(null, notificadoMinutos)
    }

    @Test
    fun `al volver a foreground despues de pausa se restablece el estado y reanuda`() = scope.runTest {
        var reanudacionLlamada = false
        var notificacionCancelada = false

        val gestor = GestorAutoPausaUbicacion(
            context = dummyContext,
            scope = this,
            proveedorMinutos = { 10 },
            notificador = {},
            canceladorNotificacion = { notificacionCancelada = true },
            onPausarUbicacion = null,
            onReanudarUbicacion = { reanudacionLlamada = true }
        )

        // Simular que expira la pausa
        gestor.onStop(dummyOwner)
        advanceTimeBy(10 * 60_000L + 10)
        assertTrue("Debe estar pausado tras expirar", gestor.estaPausado())

        // Usuario vuelve a abrir la app
        gestor.onStart(dummyOwner)
        testScheduler.runCurrent()

        assertFalse("Debe haberse reanudado tras volver a foreground", gestor.estaPausado())
        assertTrue("Se debe haber llamado al callback de reanudación", reanudacionLlamada)
        assertTrue("Se debe haber cancelado la notificación de pausa", notificacionCancelada)
    }

    @Test
    fun `si los minutos configurados son 0, la auto-pausa esta desactivada`() = scope.runTest {
        val gestor = GestorAutoPausaUbicacion(
            context = dummyContext,
            scope = this,
            proveedorMinutos = { 0 }, // 0 = Nunca
            notificador = {},
            canceladorNotificacion = {},
            onPausarUbicacion = null,
            onReanudarUbicacion = null
        )

        gestor.onStop(dummyOwner)
        advanceTimeBy(120 * 60_000L) // 2 horas después

        assertFalse("No debe pausarse nunca si la opción es 0 (desactivado)", gestor.estaPausado())
    }
}
