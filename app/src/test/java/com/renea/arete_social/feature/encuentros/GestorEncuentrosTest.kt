package com.renea.arete_social.feature.encuentros

import com.renea.arete_social.domain.model.EncuentroRemoto
import com.renea.arete_social.domain.model.EstadoEncuentro
import com.renea.arete_social.domain.model.ModoRuta
import com.renea.arete_social.domain.model.RutaEncuentro
import com.renea.arete_social.domain.model.Ubicacion
import com.renea.arete_social.domain.repository.RepositorioEncuentros
import com.renea.arete_social.domain.ruta.CalculadorRuta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// ── Fakes mínimos: no llaman a red ni Android ──────────────────────────

private object FakeRepo : RepositorioEncuentros {
    override suspend fun solicitar(amigoId: String): String = "fake-id"
    override suspend fun responder(encuentroId: String, acepta: Boolean) = EstadoEncuentro.ACTIVO
    override suspend fun terminar(encuentroId: String) = Unit
    override suspend fun publicarUbicacion(encuentroId: String, ubicacion: Ubicacion) = Unit
    override suspend fun obtenerAbiertos(): List<EncuentroRemoto> = emptyList()
    override suspend fun obtener(encuentroId: String): EncuentroRemoto? = null
    override suspend fun ubicacionDe(encuentroId: String, usuarioId: String): Ubicacion? = null
}

private object FakeCalculador : CalculadorRuta {
    override suspend fun calcular(origen: Ubicacion, destino: Ubicacion, modo: ModoRuta) =
        RutaEncuentro(puntos = listOf(origen, destino), distanciaMetros = 0.0, porCalles = false)
}

class GestorEncuentrosTest {

    @Before
    fun setUp() {
        GestorEncuentros.detener()
        GestorEncuentros.terminarEncuentro()
        // Iniciar con dependencias mínimas (sin router ni red real)
        GestorEncuentros.iniciar(
            repositorio = FakeRepo,
            calculador = FakeCalculador,
            miId = "yo-123",
            nombreDe = { null }
        )
    }

    @Test
    fun `solicitarEncuentro establece sesion pendiente con datos del amigo`() {
        assertNull(GestorEncuentros.sesionActiva.value)

        GestorEncuentros.solicitarEncuentro(
            amigoId = "amigo-123",
            amigoNombre = "Carlos"
        )

        val sesion = GestorEncuentros.sesionActiva.value
        assertNotNull(sesion)
        assertEquals("amigo-123", sesion?.amigoId)
        assertEquals("Carlos", sesion?.amigoNombre)
        // La nueva API crea la sesión como PENDIENTE (el amigo aún no aceptó)
        assertEquals(EstadoEncuentro.PENDIENTE, sesion?.estado)
        assertEquals(ModoRuta.WALKING, sesion?.modo)
        assertTrue("soySolicitante debe ser true", sesion?.soySolicitante == true)
    }

    @Test
    fun `alternarModoRuta cicla correctamente entre modos de transporte`() {
        GestorEncuentros.solicitarEncuentro("amigo-1", "Ana")

        assertEquals(ModoRuta.WALKING, GestorEncuentros.sesionActiva.value?.modo)

        GestorEncuentros.alternarModoRuta()
        assertEquals(ModoRuta.BICYCLING, GestorEncuentros.sesionActiva.value?.modo)

        GestorEncuentros.alternarModoRuta()
        assertEquals(ModoRuta.DRIVING, GestorEncuentros.sesionActiva.value?.modo)

        GestorEncuentros.alternarModoRuta()
        assertEquals(ModoRuta.WALKING, GestorEncuentros.sesionActiva.value?.modo)
    }

    @Test
    fun `terminarEncuentro limpia la sesion activa`() {
        GestorEncuentros.solicitarEncuentro("amigo-2", "Beatriz")
        assertNotNull(GestorEncuentros.sesionActiva.value)

        GestorEncuentros.terminarEncuentro()
        assertNull(GestorEncuentros.sesionActiva.value)
    }

    @Test
    fun `calcularDistanciaMetros devuelve distancia haversine correcta`() {
        // Coordenadas conocidas: Bogotá Plaza de Bolívar (4.5981, -74.0760) a Museo Nacional (4.6158, -74.0688)
        // Distancia aproximada en línea recta: ~2.1 km (2100 m)
        val plazaBolivar = Ubicacion(4.5981, -74.0760, 0L)
        val museoNacional = Ubicacion(4.6158, -74.0688, 0L)

        val distancia = GestorEncuentros.calcularDistanciaMetros(plazaBolivar, museoNacional)

        // Verificamos que esté entre 2000m y 2200m
        assertTrue("La distancia debe ser cercana a 2.1 km pero fue $distancia", distancia in 2000.0..2200.0)
    }

    @Test
    fun `calcularTiempoEstimadoMinutos calcula tiempos coherentes por modo`() {
        val distancia = 2100.0 // ~2.1 km

        val tiempoPie = GestorEncuentros.calcularTiempoEstimadoMinutos(distancia, ModoRuta.WALKING)
        val tiempoBici = GestorEncuentros.calcularTiempoEstimadoMinutos(distancia, ModoRuta.BICYCLING)
        val tiempoAuto = GestorEncuentros.calcularTiempoEstimadoMinutos(distancia, ModoRuta.DRIVING)

        // A pie: ~2100 / 1.4 = 1500 seg = 25 min
        assertEquals(25, tiempoPie)
        // En bici: ~2100 / 4.5 = 466 seg = ~7 min
        assertEquals(7, tiempoBici)
        // En auto: ~2100 / 8.5 = 247 seg = ~4 min
        assertEquals(4, tiempoAuto)
    }

    @Test
    fun `interpolarPuntos devuelve lista de puntos incluyendo extremos`() {
        val origen = Ubicacion(4.0, -74.0, 0L)
        val destino = Ubicacion(5.0, -73.0, 0L)

        val puntos = GestorEncuentros.interpolarPuntos(origen, destino, pasos = 4)

        assertEquals(5, puntos.size)
        assertEquals(4.0, puntos.first().latitud, 0.0001)
        assertEquals(5.0, puntos.last().latitud, 0.0001)
    }
}
