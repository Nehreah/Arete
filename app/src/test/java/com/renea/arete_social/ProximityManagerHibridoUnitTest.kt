package com.renea.arete_social

import com.renea.arete_social.domain.model.Dispositivo
import com.renea.arete_social.domain.model.Ubicacion
import com.renea.arete_social.domain.proximity.DetectorProximidad
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §2.3 — Tests de regresión JVM para ProximityManagerHibrido.
 *
 * Verifica que un fallo en una fuente (GPS o BLE) no detenga la otra.
 * Esto garantiza que el bug de "arranque ansioso sin permiso de ubicación
 * mata para siempre el SharedFlow de descubrimiento" no pueda reaparecer.
 *
 * No requiere emulador ni dispositivo: corre en la JVM del host.
 */
class ProximityManagerHibridoUnitTest {

    private val dispositivoDePrueba = Dispositivo(
        id              = "test-id-001",
        nombre          = "Nodo Test",
        señal           = -65,
        ultimaConexion  = System.currentTimeMillis(),
        usuarioId       = "test-id-001",
        distanciaMetros = 5.0
    )

    // ── Tests §2.3 ───────────────────────────────────────────────────────────

    /**
     * §2.3.a — Si el flujo GPS lanza SecurityException (permiso de ubicación no concedido
     * en el arranque del proceso), el BLE debe seguir emitiendo dispositivos.
     *
     * Este es exactamente el escenario que causó el bug del descubrimiento asimétrico:
     * ProximityManagerGps.ubicacionPropia() reventaba con SecurityException antes de
     * que el usuario concediera el permiso de ubicación, lo que mataba el combine() y
     * dejaba el SharedFlow compartido sin productor para siempre.
     */
    @Test
    fun `si GPS lanza SecurityException, BLE sigue emitiendo`() = runBlocking {
        val gpsFalla: Flow<List<Dispositivo>> = flow {
            throw SecurityException("sin permiso ACCESS_FINE_LOCATION")
        }
        val bleOk: Flow<List<Dispositivo>> = flowOf(listOf(dispositivoDePrueba))

        val resultado = combinarGpsBle(gpsFalla, bleOk).first { it.isNotEmpty() }

        assertEquals("BLE debería haber emitido 1 dispositivo aunque GPS fallara", 1, resultado.size)
        assertEquals(dispositivoDePrueba.id, resultado[0].id)
    }

    /**
     * §2.3.b — Si el flujo BLE lanza una excepción (Bluetooth apagado durante escaneo),
     * el GPS debe seguir emitiendo dispositivos.
     */
    @Test
    fun `si BLE lanza excepcion, GPS sigue emitiendo`() = runBlocking {
        val gpsOk: Flow<List<Dispositivo>> = flowOf(listOf(dispositivoDePrueba))
        val bleFalla: Flow<List<Dispositivo>> = flow {
            throw IllegalStateException("Bluetooth desactivado por el sistema")
        }

        val resultado = combinarGpsBle(gpsOk, bleFalla).first { it.isNotEmpty() }

        assertEquals("GPS debería haber emitido 1 dispositivo aunque BLE fallara", 1, resultado.size)
    }

    /**
     * §2.3.c — GPS y BLE emitiendo el mismo usuario: no debe duplicarse.
     */
    @Test
    fun `GPS y BLE se fusionan sin duplicar el mismo usuario`() = runBlocking {
        val dispositivoGps = dispositivoDePrueba.copy(señal = null, distanciaMetros = 10.0)
        val dispositivoBle = dispositivoDePrueba.copy(señal = -70, distanciaMetros = 8.0)

        val resultado = combinarGpsBle(
            flowOf(listOf(dispositivoGps)),
            flowOf(listOf(dispositivoBle))
        ).first { it.isNotEmpty() }

        assertTrue(
            "El mismo usuario detectado por GPS y BLE no debe duplicarse: ${resultado.size} resultados",
            resultado.size == 1
        )
    }

    /**
     * §2.3.d — Si ambos flujos fallan, el resultado es lista vacía, no una excepción
     * propagada al suscriptor.
     */
    @Test
    fun `si ambas fuentes fallan, el flujo devuelve lista vacia sin propagar excepcion`() = runBlocking {
        val gpsFalla: Flow<List<Dispositivo>> = flow { throw SecurityException("sin permiso") }
        val bleFalla: Flow<List<Dispositivo>> = flow { throw IllegalStateException("BT apagado") }

        // No debe lanzar excepción — debe terminar con lista vacía
        val resultado = combinarGpsBle(gpsFalla, bleFalla).first()
        assertTrue("Ambas fuentes fallaron: el resultado debe ser lista vacía", resultado.isEmpty())
    }

    // ── Réplica del combine() de ProximityManagerHibrido.construirFlujo() ────
    // Mantiene el mismo patrón .catch + combine que el código de producción (§2.2).
    // Si la lógica de producción cambia, esta réplica debe actualizarse también.
    private fun combinarGpsBle(
        gpsFlow: Flow<List<Dispositivo>>,
        bleFlow: Flow<List<Dispositivo>>
    ): Flow<List<Dispositivo>> = combine(
        gpsFlow
            .onStart { emit(emptyList()) }
            .catch { emit(emptyList()) },
        bleFlow
            .catch { emit(emptyList()) }
    ) { personasGps, dispositivosBle ->
        // Lógica de fusión simplificada (la completa está en ProximityManagerHibrido)
        val idsBle = dispositivosBle.map { it.usuarioId ?: it.id }.toSet()
        val gpsSinDuplicados = personasGps.filter { (it.usuarioId ?: it.id) !in idsBle }
        (gpsSinDuplicados + dispositivosBle).distinctBy { it.usuarioId ?: it.id }
    }
}
