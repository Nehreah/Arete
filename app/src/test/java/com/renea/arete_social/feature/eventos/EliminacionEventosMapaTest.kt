package com.renea.arete_social.feature.eventos

import com.renea.arete_social.domain.descubrimiento.PoliticaDescubrimiento
import com.renea.arete_social.domain.model.Evento
import com.renea.arete_social.domain.model.Lugar
import com.renea.arete_social.domain.model.Region
import com.renea.arete_social.domain.model.Ubicacion
import com.renea.arete_social.domain.model.VisibilidadEvento
import com.renea.arete_social.domain.model.ZonaRegional
import com.renea.arete_social.domain.repository.RepositorioEventos
import com.renea.arete_social.domain.repository.RepositorioLugares
import com.renea.arete_social.feature.descubrimiento.AlmacenEnMemoria
import com.renea.arete_social.feature.descubrimiento.ServicioDescubrimiento
import com.renea.arete_social.feature.descubrimiento.ServicioRegionActual
import com.renea.arete_social.feature.descubrimiento.ServicioVisionRegional
import com.renea.arete_social.feature.tamagotchi.superpoderes.ServicioModoSuper
import com.renea.arete_social.testutils.RelojFalso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EliminacionEventosMapaTest {

    private val reloj = RelojFalso()
    private val aqui = Ubicacion(3.4516, -76.5320, 0L)

    private fun evento(id: String) = Evento(
        id = id,
        nombre = "Evento $id",
        descripcion = "desc",
        fecha = reloj.ahora + 3_600_000L,
        ubicacion = Ubicacion(3.4518, -76.5320, 0L), // dentro de los 500 m
        creadorId = "user1",
        comunidadId = null,
        participantesIds = emptyList(),
        chatId = "chat-$id",
        visibilidad = VisibilidadEvento.PUBLICO
    )

    private class RepoEventosFake : RepositorioEventos {
        val flujo = MutableSharedFlow<String>(extraBufferCapacity = 64)
        override val eventosEliminados: Flow<String> = flujo.asSharedFlow()

        override fun notificarEliminado(eventoId: String) {
            flujo.tryEmit(eventoId)
        }

        override suspend fun eliminar(eventoId: String) {
            flujo.emit(eventoId)
        }

        override suspend fun crear(
            nombre: String, descripcion: String, fecha: Long, lugarId: String?,
            creadorId: String, comunidadId: String?, ubicacion: Ubicacion?,
            visibilidad: VisibilidadEvento, fotoUrl: String?
        ): Evento = error("no")
        override suspend fun obtenerPorId(id: String): Evento? = null
        override fun observarEvento(id: String): Flow<Evento?> = flowOf(null)
        override suspend fun unirse(eventoId: String, usuarioId: String) = Unit
        override suspend fun salir(eventoId: String, usuarioId: String) = Unit
        override suspend fun actualizar(id: String, nombre: String, descripcion: String, fecha: Long, ubicacion: Ubicacion?, visibilidad: VisibilidadEvento, fotoUrl: String?) = Unit
        override suspend fun asignarRol(eventoId: String, usuarioId: String, rol: String) = Unit
        override suspend fun listarParticipantesConRoles(eventoId: String) = emptyList<com.renea.arete_social.domain.model.ParticipanteEvento>()
        override suspend fun subirPortada(eventoId: String, bytes: ByteArray, extension: String) = ""
        override suspend fun eventosCercanos(origen: Ubicacion, radioMetros: Double) = emptyList<Evento>()
        override suspend fun listarPorComunidad(comunidadId: String) = emptyList<Evento>()
    }

    @Test
    fun `ServicioDescubrimiento purga evento si al consultar el servidor en el radio ya no existe`() = runBlocking {
        val almacen = AlmacenEnMemoria()
        val servicio = ServicioDescubrimiento(
            almacen = almacen,
            usuarioIdProveedor = { "yo" },
            politica = PoliticaDescubrimiento(reloj),
            reloj = reloj,
            dispatcher = Dispatchers.Unconfined
        )

        val ev = evento("ev-borrado")
        // 1. Descubrir el evento inicialmente cerca
        servicio.registrarCercanos(aqui, emptyList(), listOf(ev))
        assertEquals(1, servicio.visibles.value.size)
        assertEquals("ev-borrado", servicio.visibles.value.single().snapshot.id)

        // 2. El evento se borra en el servidor; la próxima consulta a 500 m devuelve lista vacía
        servicio.registrarCercanos(aqui, emptyList(), emptyList())

        // 3. Ya NO debe aparecer como visible ni en memoria ni en almacén
        assertTrue("El evento borrado debe haber sido purgado", servicio.visibles.value.isEmpty())
        assertFalse("El almacén no debe tener el evento borrado", almacen.datos["yo"]?.containsKey("EVENTO:ev-borrado") == true)
    }

    @Test
    fun `ServicioDescubrimiento olvidarEvento elimina el evento de descubiertos y persistencia`() = runBlocking {
        val almacen = AlmacenEnMemoria()
        val servicio = ServicioDescubrimiento(
            almacen = almacen,
            usuarioIdProveedor = { "yo" },
            politica = PoliticaDescubrimiento(reloj),
            reloj = reloj,
            dispatcher = Dispatchers.Unconfined
        )

        val ev = evento("ev-1")
        servicio.registrarCercanos(aqui, emptyList(), listOf(ev))
        assertEquals(1, servicio.visibles.value.size)

        servicio.olvidarEvento("ev-1")
        assertTrue(servicio.visibles.value.isEmpty())
        assertFalse(almacen.datos["yo"]?.containsKey("EVENTO:ev-1") == true)
    }

    @Test
    fun `ServicioVisionRegional remueve de inmediato el evento reportado en eventosEliminados`() = runBlocking {
        val repoEventos = RepoEventosFake()
        val region = Region("r1", "Region 1", aqui, "chat-r1")
        val ev1 = evento("ev-super-1")
        val ev2 = evento("ev-super-2")

        val repoLugares = object : RepositorioLugares {
            override suspend fun crear(nombre: String, descripcion: String, ubicacion: Ubicacion): Lugar = error("no")
            override suspend fun obtenerPorId(id: String): Lugar? = null
            override fun observarLugar(id: String): Flow<Lugar?> = flowOf(null)
            override suspend fun lugaresCercanos(origen: Ubicacion, radioMetros: Double) = emptyList<Lugar>()
            override suspend fun lugaresEnZona(zona: ZonaRegional, limite: Int) = emptyList<Lugar>()
        }

        val regionActual = ServicioRegionActual(listOf(region))
        val modoSuper = MutableStateFlow(ServicioModoSuper.Estado(activo = true, venceEn = reloj.ahora + 3_600_000L))
        val job = Job()
        val scope = CoroutineScope(job + Dispatchers.Default)

        val servicio = ServicioVisionRegional(
            modoSuper = modoSuper,
            region = regionActual,
            repositorioLugares = repoLugares,
            repositorioEventos = object : RepositorioEventos by repoEventos {
                override suspend fun eventosPublicosEnZona(zona: ZonaRegional, desdeMillis: Long, limite: Int): List<Evento> {
                    return listOf(ev1, ev2)
                }
            },
            scope = scope,
            reloj = reloj
        )

        servicio.iniciar()
        regionActual.actualizar(aqui)

        val vInicial = withTimeout(5_000) { servicio.vision.first { it != null && it.eventos.size == 2 } }!!
        assertEquals(2, vInicial.eventos.size)

        // Se emite eliminación de ev-super-1
        repoEventos.eliminar("ev-super-1")

        val vDespues = withTimeout(5_000) { servicio.vision.first { it?.eventos?.size == 1 } }!!
        assertEquals(1, vDespues.eventos.size)
        assertEquals("ev-super-2", vDespues.eventos.single().id)

        servicio.detener()
        job.cancel()
    }

    @Test
    fun `RepositorioEventos Fake notificarEliminado emite en el flujo de eliminados`() = runBlocking {
        val repo = RepoEventosFake()
        val recibidos = mutableListOf<String>()
        val job = Job()
        val scope = CoroutineScope(job + Dispatchers.Unconfined)
        scope.launch(Dispatchers.Unconfined) {
            repo.eventosEliminados.collect { recibidos.add(it) }
        }

        repo.notificarEliminado("ev-123")
        assertEquals(listOf("ev-123"), recibidos)

        repo.eliminar("ev-456")
        assertEquals(listOf("ev-123", "ev-456"), recibidos)
        job.cancel()
    }
}
