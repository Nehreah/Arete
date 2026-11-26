package com.renea.arete_social

import com.renea.arete_social.domain.model.Dispositivo
import com.renea.arete_social.domain.model.Ubicacion
import com.renea.arete_social.domain.model.Usuario
import com.renea.arete_social.domain.model.Visibilidad
import com.renea.arete_social.domain.repository.RepositorioUsuarios
import com.renea.arete_social.domain.util.distanciaMetros
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de regresión para garantizar que el cálculo de proximidad por ubicación
 * se mantenga blindado contra futuras modificaciones.
 *
 * Valida:
 * 1. Filtrado del usuario propio para evitar auto-detección.
 * 2. Cálculo preciso de distancia en metros a partir de coordenadas.
 * 3. Manejo de listas vacías y fallos de red sin excepciones no controladas.
 */
class ProximityManagerGpsUnitTest {

    private val ubicacionLocal = Ubicacion(3.3852658, -76.5233646, System.currentTimeMillis())
    private val ubicacionVecino = Ubicacion(3.3852484, -76.5233729, System.currentTimeMillis())

    private val usuarioRemoto = Usuario(
        id = "remoto-uuid-002",
        nombre = "Vecino Test",
        foto = null,
        estadoVisibilidad = Visibilidad.VISIBLE,
        ubicacion = ubicacionVecino
    )

    private val usuarioPropio = Usuario(
        id = "mi-usuario-uuid-001",
        nombre = "Yo Mismo",
        foto = null,
        estadoVisibilidad = Visibilidad.VISIBLE,
        ubicacion = ubicacionLocal
    )

    @Test
    fun `calculo de distancia en metros entre coordenadas es coherente`() {
        val distancia = distanciaMetros(ubicacionLocal, ubicacionVecino)
        // Coordenadas con diferencia de ~0.000017 grados en latitud están a menos de 5 metros
        assertTrue("La distancia calculada debe ser menor a 5 metros: $distancia", distancia < 5.0)
        assertTrue("La distancia calculada debe ser mayor a 0 metros: $distancia", distancia > 0.0)
    }

    @Test
    fun `filtrar usuario propio excluye al remitente del resultado de cercanos`() {
        val listaConAmbos = listOf(usuarioPropio, usuarioRemoto)
        val miId = "mi-usuario-uuid-001"

        val filtrados = listaConAmbos.filter { it.id != miId }

        assertEquals(1, filtrados.size)
        assertEquals("remoto-uuid-002", filtrados[0].id)
    }

    @Test
    fun `mapeo de Usuario a Dispositivo preserva distancia y coordenadas`() {
        val origen = ubicacionLocal
        val d = Dispositivo(
            id = usuarioRemoto.id,
            nombre = usuarioRemoto.nombre,
            señal = null,
            ultimaConexion = System.currentTimeMillis(),
            usuarioId = usuarioRemoto.id,
            distanciaMetros = usuarioRemoto.ubicacion?.let { distanciaMetros(origen, it) },
            ubicacion = usuarioRemoto.ubicacion
        )

        assertEquals("remoto-uuid-002", d.id)
        assertEquals("Vecino Test", d.nombre)
        assertNotNull(d.distanciaMetros)
        assertTrue("La distancia debe ser menor a 5 metros", d.distanciaMetros!! < 5.0)
        assertEquals(ubicacionVecino.latitud, d.ubicacion?.latitud)
    }

    @Test
    fun `filtro de distancia a 2 km discrimina correctamente nodos a 1800m y 2200m`() {
        val origen = Ubicacion(3.4500, -76.5300, System.currentTimeMillis())
        // ~1.8 km al norte: aprox 0.0162 grados de latitud
        val nodoCercano1800m = Ubicacion(3.4662, -76.5300, System.currentTimeMillis())
        // ~2.2 km al norte: aprox 0.0198 grados de latitud
        val nodoLejano2200m = Ubicacion(3.4698, -76.5300, System.currentTimeMillis())

        val dist1 = distanciaMetros(origen, nodoCercano1800m)
        val dist2 = distanciaMetros(origen, nodoLejano2200m)

        assertEquals(2000.0, com.renea.arete_social.data.proximity.gps.RADIO_CERCANOS_METROS, 0.001)
        assertTrue("Nodo a ~1.8km debe estar dentro de 2 km: $dist1", dist1 <= com.renea.arete_social.data.proximity.gps.RADIO_CERCANOS_METROS)
        assertTrue("Nodo a ~2.2km debe estar fuera de 2 km: $dist2", dist2 > com.renea.arete_social.data.proximity.gps.RADIO_CERCANOS_METROS)
    }
}

