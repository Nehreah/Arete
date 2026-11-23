package com.renea.arete_social.feature.descubrimiento

import com.renea.arete_social.domain.descubrimiento.PoliticaDescubrimiento
import com.renea.arete_social.domain.model.Descubrimiento
import com.renea.arete_social.domain.model.EntidadSnapshot
import com.renea.arete_social.domain.model.EstadoDescubrimiento
import com.renea.arete_social.domain.model.Evento
import com.renea.arete_social.domain.model.Lugar
import com.renea.arete_social.domain.model.OrigenVisibilidad
import com.renea.arete_social.domain.model.TipoEntidadMapa
import com.renea.arete_social.domain.model.Ubicacion
import com.renea.arete_social.domain.model.VisibilidadEvento
import com.renea.arete_social.domain.repository.AlmacenDescubrimientos
import com.renea.arete_social.testutils.RelojFalso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Almacén en memoria para probar sin Android; cuenta cuántas veces se escribió. */
class AlmacenEnMemoria : AlmacenDescubrimientos {
    val datos = HashMap<String, HashMap<String, Descubrimiento>>()
    var escrituras = 0

    override fun cargar(usuarioId: String): List<Descubrimiento> = datos[usuarioId]?.values?.toList() ?: emptyList()
    override fun guardar(usuarioId: String, descubrimiento: Descubrimiento) {
        escrituras++
        datos.getOrPut(usuarioId) { HashMap() }[descubrimiento.snapshot.clave] = descubrimiento
    }
    override fun eliminar(usuarioId: String, clave: String) { datos[usuarioId]?.remove(clave) }
    override fun limpiar(usuarioId: String?) { if (usuarioId == null) datos.clear() else datos.remove(usuarioId) }
}

class ServicioDescubrimientoTest {

    private val reloj = RelojFalso()
    private val almacen = AlmacenEnMemoria()
    private var usuario: String? = "yo"
    private val servicio = ServicioDescubrimiento(
        almacen = almacen,
        usuarioIdProveedor = { usuario },
        politica = PoliticaDescubrimiento(reloj),
        reloj = reloj,
        dispatcher = Dispatchers.Unconfined
    )

    private val aqui = Ubicacion(3.4516, -76.5320, 0L)
    private val lejos = Ubicacion(3.5516, -76.5320, 0L) // ~11 km al norte

    private fun lugar(id: String = "l1", lat: Double = 3.4520, lng: Double = -76.5320) =
        Lugar(id, "Lugar $id", "desc", Ubicacion(lat, lng, 0L), chatId = "chat-$id")

    private fun evento(
        id: String = "e1",
        visibilidad: VisibilidadEvento = VisibilidadEvento.PUBLICO,
        fecha: Long = reloj.ahora + 3_600_000
    ) = Evento(
        id = id, nombre = "Evento $id", descripcion = "", fecha = fecha,
        ubicacion = Ubicacion(3.4518, -76.5320, 0L), creadorId = "otro", comunidadId = null,
        participantesIds = emptyList(), chatId = "chat-$id", visibilidad = visibilidad
    )

    @Test
    fun `lo que hay a 500 m se ve como cerca y se guarda`() = runBlocking {
        servicio.registrarCercanos(aqui, listOf(lugar()), listOf(evento()))
        val visibles = servicio.visibles.value
        assertEquals(2, visibles.size)
        assertTrue(visibles.all { it.origen == OrigenVisibilidad.CERCA })
        assertEquals(2, almacen.datos["yo"]?.size)
    }

    @Test
    fun `al alejarse sigue visible como reciente con su vencimiento`() = runBlocking {
        servicio.registrarCercanos(aqui, listOf(lugar()), emptyList())
        val visto = reloj.ahora
        reloj.avanzarHoras(2.0)
        servicio.registrarCercanos(lejos, emptyList(), emptyList())
        val v = servicio.visibles.value.single()
        assertEquals(OrigenVisibilidad.RECIENTE, v.origen)
        assertEquals(visto + 24 * 3_600_000L, v.venceEn)
    }

    @Test
    fun `pasadas las 24 horas desaparece aunque no te muevas`() = runBlocking {
        servicio.registrarCercanos(aqui, listOf(lugar()), emptyList())
        servicio.registrarCercanos(lejos, emptyList(), emptyList())
        reloj.avanzarHoras(23.0)
        servicio.refrescar()
        assertEquals(1, servicio.visibles.value.size)
        reloj.avanzarHoras(1.0)
        servicio.refrescar()
        assertTrue(servicio.visibles.value.isEmpty())
        assertTrue(almacen.datos["yo"].isNullOrEmpty())
    }

    @Test
    fun `volver cerca antes de las 24 horas reinicia el plazo`() = runBlocking {
        servicio.registrarCercanos(aqui, listOf(lugar()), emptyList())
        servicio.registrarCercanos(lejos, emptyList(), emptyList())
        reloj.avanzarHoras(20.0)
        servicio.registrarCercanos(aqui, listOf(lugar()), emptyList())
        servicio.registrarCercanos(lejos, emptyList(), emptyList())
        reloj.avanzarHoras(20.0)
        servicio.refrescar()
        assertEquals(1, servicio.visibles.value.size)
    }

    @Test
    fun `no se escribe en disco mas de una vez cada 30 segundos por elemento`() = runBlocking {
        servicio.registrarCercanos(aqui, listOf(lugar()), emptyList())
        servicio.registrarCercanos(aqui, listOf(lugar()), emptyList())
        servicio.registrarCercanos(aqui, listOf(lugar()), emptyList())
        assertEquals(1, almacen.escrituras)
        reloj.avanzarMinutos(1)
        servicio.registrarCercanos(aqui, listOf(lugar()), emptyList())
        assertEquals(2, almacen.escrituras)
    }

    @Test
    fun `un lugar que el servidor devolvio pero esta fuera del radio no se descubre`() = runBlocking {
        servicio.registrarCercanos(lejos, listOf(lugar()), emptyList())
        assertTrue(servicio.visibles.value.isEmpty())
    }

    @Test
    fun `lo guardado se recupera al iniciar un servicio nuevo`() = runBlocking {
        servicio.registrarCercanos(aqui, listOf(lugar()), emptyList())
        val otro = ServicioDescubrimiento(almacen, { "yo" }, PoliticaDescubrimiento(reloj), reloj, Dispatchers.Unconfined)
        otro.iniciar()
        assertEquals(OrigenVisibilidad.RECIENTE, otro.visibles.value.single().origen)
    }

    @Test
    fun `cada persona ve solo sus descubrimientos`() = runBlocking {
        servicio.registrarCercanos(aqui, listOf(lugar()), emptyList())
        usuario = "otra"
        servicio.refrescar()
        assertTrue(servicio.visibles.value.isEmpty())
        usuario = "yo"
        servicio.refrescar()
        assertEquals(1, servicio.visibles.value.size)
    }

    @Test
    fun `sin sesion no se ve ni se guarda nada`() = runBlocking {
        usuario = null
        servicio.registrarCercanos(aqui, listOf(lugar()), emptyList())
        assertTrue(servicio.visibles.value.isEmpty())
        assertEquals(0, almacen.escrituras)
    }

    @Test
    fun `solo se pueden compartir lugares y eventos publicos descubiertos fisicamente`() = runBlocking {
        servicio.registrarCercanos(
            aqui,
            listOf(lugar("l1")),
            listOf(evento("e1"), evento("e2", VisibilidadEvento.AMIGOS), evento("e3", VisibilidadEvento.COMUNIDAD))
        )
        val ids = servicio.compartibles().map { it.id }.toSet()
        assertEquals(setOf("l1", "e1"), ids)
    }

    @Test
    fun `lo recibido por pase se ve pero no se comparte`() = runBlocking {
        val snapshot = EntidadSnapshot(TipoEntidadMapa.LUGAR, "lp", "Plaza", latitud = 3.9, longitud = -76.0, chatId = "chat-lp")
        servicio.registrar(Descubrimiento(snapshot, paseRecibidoEn = reloj.ahora, paseEmisorId = "ana"))
        val v = servicio.visibles.value.single()
        assertEquals(OrigenVisibilidad.PASE, v.origen)
        assertTrue(servicio.compartibles().isEmpty())
        assertTrue(servicio.estadoDe(snapshot.clave) is EstadoDescubrimiento.PorPase)
    }

    @Test
    fun `un pase vencido deja de verse`() = runBlocking {
        val snapshot = EntidadSnapshot(TipoEntidadMapa.LUGAR, "lp", "Plaza", latitud = 3.9, longitud = -76.0, chatId = "chat-lp")
        servicio.registrar(Descubrimiento(snapshot, paseRecibidoEn = reloj.ahora, paseEmisorId = "ana"))
        reloj.avanzarHoras(24.0)
        servicio.refrescar()
        assertTrue(servicio.visibles.value.isEmpty())
        assertTrue(servicio.estadoDe(snapshot.clave) is EstadoDescubrimiento.NoDescubierto)
    }

    @Test
    fun `limpiar olvida todo lo de la persona`() = runBlocking {
        servicio.registrarCercanos(aqui, listOf(lugar()), emptyList())
        servicio.limpiar()
        assertTrue(servicio.visibles.value.isEmpty())
        assertTrue(almacen.datos["yo"].isNullOrEmpty())
        assertFalse(servicio.compartibles().isNotEmpty())
    }
}
