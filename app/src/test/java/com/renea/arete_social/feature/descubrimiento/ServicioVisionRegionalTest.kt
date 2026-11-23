package com.renea.arete_social.feature.descubrimiento

import com.renea.arete_social.domain.model.Evento
import com.renea.arete_social.domain.model.Lugar
import com.renea.arete_social.domain.model.Region
import com.renea.arete_social.domain.model.Ubicacion
import com.renea.arete_social.domain.model.VisibilidadEvento
import com.renea.arete_social.domain.model.ZonaRegional
import com.renea.arete_social.domain.repository.RepositorioEventos
import com.renea.arete_social.domain.repository.RepositorioLugares
import com.renea.arete_social.feature.tamagotchi.superpoderes.ServicioModoSuper
import com.renea.arete_social.testutils.RelojFalso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServicioVisionRegionalTest {

    private val reloj = RelojFalso()
    private val hora = 3_600_000L

    private val a = Region("a", "A", Ubicacion(0.0, 0.0, 0L), "chat-a")
    private val b = Region("b", "B", Ubicacion(0.0, 1.0, 0L), "chat-b")

    private fun lugar(id: String, lng: Double, chat: String = "chat-$id") =
        Lugar(id, "Lugar $id", "", Ubicacion(0.0, lng, 0L), chat)

    private fun evento(
        id: String, lng: Double,
        visibilidad: VisibilidadEvento = VisibilidadEvento.PUBLICO,
        fecha: Long = reloj.ahora + hora
    ) = Evento(
        id = id, nombre = "Evento $id", descripcion = "", fecha = fecha, ubicacion = Ubicacion(0.0, lng, 0L),
        creadorId = "x", comunidadId = null, participantesIds = emptyList(), chatId = "chat-$id", visibilidad = visibilidad
    )

    private class RepoLugaresFalso(var lista: List<Lugar>) : RepositorioLugares {
        var consultas = 0
        override suspend fun crear(nombre: String, descripcion: String, ubicacion: Ubicacion): Lugar = error("no")
        override suspend fun obtenerPorId(id: String): Lugar? = null
        override fun observarLugar(id: String): Flow<Lugar?> = flowOf(null)
        override suspend fun lugaresCercanos(origen: Ubicacion, radioMetros: Double): List<Lugar> = emptyList()
        override suspend fun lugaresEnZona(zona: ZonaRegional, limite: Int): List<Lugar> { consultas++; return lista }
    }

    private class RepoEventosFalso(var lista: List<Evento>) : RepositorioEventos {
        var consultas = 0
        var ultimoDesde = 0L
        override suspend fun crear(
            nombre: String,
            descripcion: String,
            fecha: Long,
            lugarId: String?,
            creadorId: String,
            comunidadId: String?,
            ubicacion: Ubicacion?,
            visibilidad: VisibilidadEvento,
            fotoUrl: String?
        ): Evento = error("no")
        override suspend fun obtenerPorId(id: String): Evento? = null
        override fun observarEvento(id: String): Flow<Evento?> = flowOf(null)
        override suspend fun unirse(eventoId: String, usuarioId: String) = Unit
        override suspend fun salir(eventoId: String, usuarioId: String) = Unit
        override suspend fun actualizar(
            id: String,
            nombre: String,
            descripcion: String,
            fecha: Long,
            ubicacion: Ubicacion?,
            visibilidad: VisibilidadEvento,
            fotoUrl: String?
        ) = Unit
        override suspend fun eliminar(eventoId: String) = Unit
        override suspend fun asignarRol(eventoId: String, usuarioId: String, rol: String) = Unit
        override suspend fun listarParticipantesConRoles(eventoId: String): List<com.renea.arete_social.domain.model.ParticipanteEvento> = emptyList()
        override suspend fun subirPortada(eventoId: String, bytes: ByteArray, extension: String): String = ""
        override suspend fun eventosCercanos(origen: Ubicacion, radioMetros: Double): List<Evento> = emptyList()
        override suspend fun listarPorComunidad(comunidadId: String): List<Evento> = emptyList()
        override suspend fun eventosPublicosEnZona(zona: ZonaRegional, desdeMillis: Long, limite: Int): List<Evento> {
            consultas++; ultimoDesde = desdeMillis; return lista
        }
    }

    private val lugares = RepoLugaresFalso(emptyList())
    private val eventos = RepoEventosFalso(emptyList())
    private val modoSuper = MutableStateFlow(ServicioModoSuper.Estado())
    private val regionActual = ServicioRegionActual(listOf(a, b))
    private val trabajo = Job()
    private val scope = CoroutineScope(trabajo + Dispatchers.Default)
    private val servicio = ServicioVisionRegional(
        modoSuper = modoSuper, region = regionActual, repositorioLugares = lugares,
        repositorioEventos = eventos, scope = scope, reloj = reloj, refrescoMillis = 60_000L
    )

    private fun fin() { servicio.detener(); trabajo.cancel() }

    private fun encender() {
        modoSuper.value = ServicioModoSuper.Estado(activo = true, venceEn = reloj.ahora + 3 * hora)
    }

    // ── Una sola carga ───────────────────────────────────────────────────────

    @Test
    fun `solo trae lo de la region correcta y descarta lo que pertenece a otra`() = runBlocking {
        lugares.lista = listOf(lugar("cerca-a", 0.2), lugar("de-b", 0.8), lugar("lejano", 3.0))
        eventos.lista = listOf(evento("ev-a", 0.1), evento("ev-b", 0.9))
        val v = servicio.cargar(a)!!
        assertEquals(listOf("cerca-a"), v.lugares.map { it.id })
        assertEquals(listOf("ev-a"), v.eventos.map { it.id })
        assertEquals(a, v.region)
    }

    @Test
    fun `excluye eventos no publicos y eventos ya terminados`() = runBlocking {
        eventos.lista = listOf(
            evento("publico", 0.1),
            evento("amigos", 0.1, VisibilidadEvento.AMIGOS),
            evento("comunidad", 0.1, VisibilidadEvento.COMUNIDAD),
            evento("viejo", 0.1, fecha = reloj.ahora - 3 * 24 * hora),
            evento("de-ayer-por-la-noche", 0.1, fecha = reloj.ahora - 2 * hora)
        )
        val v = servicio.cargar(a)!!
        assertEquals(setOf("publico", "de-ayer-por-la-noche"), v.eventos.map { it.id }.toSet())
    }

    @Test
    fun `pide al servidor eventos desde hace un dia`() = runBlocking {
        servicio.cargar(a)
        assertEquals(reloj.ahora - 24 * hora, eventos.ultimoDesde)
    }

    // ── Con el bucle de fondo ────────────────────────────────────────────────

    @Test
    fun `sin super no consulta nada`() = runBlocking {
        servicio.iniciar()
        regionActual.actualizar(Ubicacion(0.0, 0.1, 0L))
        delay(300)
        assertEquals(0, lugares.consultas)
        assertEquals(0, eventos.consultas)
        assertNull(servicio.vision.value)
        fin()
    }

    @Test
    fun `con super pero sin region todavia no consulta`() = runBlocking {
        servicio.iniciar()
        encender()
        delay(300)
        assertEquals(0, lugares.consultas)
        assertNull(servicio.vision.value)
        fin()
    }

    @Test
    fun `con super y region carga la zona`() = runBlocking {
        lugares.lista = listOf(lugar("l1", 0.2))
        eventos.lista = listOf(evento("e1", 0.1))
        servicio.iniciar()
        regionActual.actualizar(Ubicacion(0.0, 0.1, 0L))
        encender()
        val v = withTimeout(5_000) { servicio.vision.first { it != null } }!!
        assertEquals("a", v.region.id)
        assertEquals(1, v.lugares.size)
        assertEquals(1, v.eventos.size)
        fin()
    }

    @Test
    fun `al terminar el super se vacia de inmediato`() = runBlocking {
        lugares.lista = listOf(lugar("l1", 0.2))
        servicio.iniciar()
        regionActual.actualizar(Ubicacion(0.0, 0.1, 0L))
        encender()
        withTimeout(5_000) { servicio.vision.first { it != null } }
        modoSuper.value = ServicioModoSuper.Estado()
        withTimeout(5_000) { servicio.vision.first { it == null } }
        assertNull(servicio.vision.value)
        fin()
    }

    @Test
    fun `al cambiar de region se reemplaza la vision`() = runBlocking {
        lugares.lista = listOf(lugar("la", 0.2), lugar("lb", 0.8))
        servicio.iniciar()
        regionActual.actualizar(Ubicacion(0.0, 0.1, 0L))
        encender()
        val enA = withTimeout(5_000) { servicio.vision.first { it != null } }!!
        assertEquals(listOf("la"), enA.lugares.map { it.id })
        regionActual.actualizar(Ubicacion(0.0, 0.95, 0L)) // claramente más cerca de B
        val enB = withTimeout(5_000) { servicio.vision.first { it?.region?.id == "b" } }!!
        assertEquals(listOf("lb"), enB.lugares.map { it.id })
        fin()
    }

    @Test
    fun `la misma region con otra posicion no vuelve a consultar`() = runBlocking {
        servicio.iniciar()
        regionActual.actualizar(Ubicacion(0.0, 0.1, 0L))
        encender()
        withTimeout(5_000) { servicio.vision.first { it != null } }
        val antes = lugares.consultas
        regionActual.actualizar(Ubicacion(0.0, 0.12, 0L))
        regionActual.actualizar(Ubicacion(0.0, 0.14, 0L))
        delay(300)
        assertEquals(antes, lugares.consultas)
        fin()
    }

    @Test
    fun `detener limpia el resultado`() = runBlocking {
        servicio.iniciar()
        regionActual.actualizar(Ubicacion(0.0, 0.1, 0L))
        encender()
        withTimeout(5_000) { servicio.vision.first { it != null } }
        servicio.detener()
        assertNull(servicio.vision.value)
        trabajo.cancel()
        assertTrue(true)
    }
}
