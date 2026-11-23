package com.renea.arete_social.feature.mensajeria

import com.renea.arete_social.domain.repository.ID_CHAT_GENERAL
import com.renea.arete_social.domain.descubrimiento.PoliticaDescubrimiento
import com.renea.arete_social.domain.model.Lugar
import com.renea.arete_social.domain.model.OrigenVisibilidad
import com.renea.arete_social.domain.model.Region
import com.renea.arete_social.domain.model.Sala
import com.renea.arete_social.domain.model.TipoSala
import com.renea.arete_social.domain.model.Ubicacion
import com.renea.arete_social.domain.repository.RepositorioSalas
import com.renea.arete_social.feature.descubrimiento.AlmacenEnMemoria
import com.renea.arete_social.feature.descubrimiento.ServicioDescubrimiento
import com.renea.arete_social.feature.descubrimiento.ServicioRegionActual
import com.renea.arete_social.testutils.RelojFalso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** El SalasViewModel con los servicios de descubrimiento: Chat Aréte, regionales y chats de lugares. */
@OptIn(ExperimentalCoroutinesApi::class)
class SalasViewModelZonasTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val repoSalas = object : RepositorioSalas {
        override suspend fun obtenerOCrearSalaGeneral(): Sala = error("no")
        override suspend fun crearSala(nombre: String, descripcion: String, creadorId: String, esPublica: Boolean, clave: String?): Sala = error("no")
        override suspend fun listarSalas(): List<Sala> = emptyList()
        override fun observarSalas() = flowOf(emptyList<Sala>())
        override suspend fun obtenerSala(id: String): Sala? = null
        override suspend fun unirseASala(salaId: String, clave: String?): Boolean = true
    }

    private val reloj = RelojFalso()
    private val almacen = AlmacenEnMemoria()
    private val descubrimiento = ServicioDescubrimiento(
        almacen, { "yo" }, PoliticaDescubrimiento(reloj), reloj, Dispatchers.Unconfined
    )
    private val a = Region("a", "Armenia", Ubicacion(0.0, 0.0, 0L), "chat-a")
    private val b = Region("b", "Bogotá", Ubicacion(0.0, 1.0, 0L), "chat-b")
    private val regiones = ServicioRegionActual(listOf(a, b))

    private fun vm(conRegiones: Boolean = true, conDescubrimiento: Boolean = true) = SalasViewModel(
        repositorioSalas = repoSalas,
        usuarioIdActual = "yo",
        servicioDescubrimiento = if (conDescubrimiento) descubrimiento else null,
        servicioRegionActual = if (conRegiones) regiones else null
    )

    private fun SalasViewModel.listo() = estado.value as SalasUiState.Listo

    @Test
    fun `sin servicios la lista es la de siempre y el general se llama Chat Aréte`() {
        val v = vm(conRegiones = false, conDescubrimiento = false).listo()
        assertEquals(1, v.salas.size)
        assertEquals("Chat Aréte", v.salaGeneral?.nombre)
        assertEquals(ID_CHAT_GENERAL, v.salaGeneral?.chatId)
        assertNull(v.regionDestacadaChatId)
    }

    @Test
    fun `todas las regiones aparecen despues del general aun sin ubicacion`() {
        val v = vm().listo()
        assertEquals(ID_CHAT_GENERAL, v.salas.first().chatId)
        assertEquals(setOf("chat-a", "chat-b"), v.salas.filter { it.tipo == TipoSala.REGIONAL }.map { it.chatId }.toSet())
        assertNull(v.regionDestacadaChatId)
    }

    @Test
    fun `la region mas cercana se destaca y va primero`() {
        val modelo = vm()
        regiones.actualizar(Ubicacion(0.0, 0.9, 0L))
        val v = modelo.listo()
        assertEquals("chat-b", v.regionDestacadaChatId)
        val regionales = v.salas.filter { it.tipo == TipoSala.REGIONAL }
        assertEquals(listOf("chat-b", "chat-a"), regionales.map { it.chatId })
    }

    @Test
    fun `un lugar descubierto agrega su chat y al vencer desaparece`() = runBlocking {
        val modelo = vm()
        val aqui = Ubicacion(3.4516, -76.5320, 0L)
        val lugar = Lugar("l1", "Biblioteca", "", Ubicacion(3.4520, -76.5320, 0L), chatId = "chat-l1")
        descubrimiento.registrarCercanos(aqui, listOf(lugar), emptyList())

        var v = modelo.listo()
        assertTrue(v.salas.any { it.chatId == "chat-l1" && it.tipo == TipoSala.LUGAR })
        assertEquals(OrigenVisibilidad.CERCA, v.visibilidadPorChat["chat-l1"]?.origen)

        // Se aleja: sigue visible como reciente.
        descubrimiento.registrarCercanos(Ubicacion(3.60, -76.53, 0L), emptyList(), emptyList())
        v = modelo.listo()
        assertEquals(OrigenVisibilidad.RECIENTE, v.visibilidadPorChat["chat-l1"]?.origen)

        // A las 24 h se oculta y su chat sale de la lista.
        reloj.avanzarHoras(24.0)
        descubrimiento.refrescar()
        v = modelo.listo()
        assertTrue(v.salas.none { it.chatId == "chat-l1" })
        assertTrue(v.visibilidadPorChat.isEmpty())
    }

    @Test
    fun `unificar mantiene el orden general regionales comunidades zona remotas y no duplica chats`() {
        val modelo = vm(conRegiones = false, conDescubrimiento = false)
        val reg = SalasZonales.salasRegionales(listOf(a, b), a)
        val com = listOf(Sala(id = "c1", nombre = "# Comunidad", chatId = "chat-com", creadorId = "x", tipo = TipoSala.COMUNIDAD))
        val zona = listOf(Sala(id = "z1", nombre = "Lugar", chatId = "chat-z", creadorId = "mapa", tipo = TipoSala.LUGAR))
        val remotas = listOf(
            Sala(id = "r1", nombre = "Remota", chatId = "chat-r", creadorId = "x"),
            Sala(id = "dup", nombre = "Duplicada de zona", chatId = "chat-z", creadorId = "x")
        )
        val todas = modelo.unificarYFiltrarSalas(remotas, com, reg, zona)
        assertEquals(
            listOf(ID_CHAT_GENERAL, "chat-a", "chat-b", "chat-com", "chat-z", "chat-r"),
            todas.map { it.chatId }
        )
    }
}
