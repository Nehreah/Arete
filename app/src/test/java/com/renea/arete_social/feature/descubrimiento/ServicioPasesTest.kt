package com.renea.arete_social.feature.descubrimiento

import com.renea.arete_social.domain.descubrimiento.PoliticaDescubrimiento
import com.renea.arete_social.domain.model.Descubrimiento
import com.renea.arete_social.domain.model.EntidadSnapshot
import com.renea.arete_social.domain.model.EstadoDescubrimiento
import com.renea.arete_social.domain.model.Evento
import com.renea.arete_social.domain.model.Lugar
import com.renea.arete_social.domain.model.OrigenVisibilidad
import com.renea.arete_social.domain.model.PaseDescubrimiento
import com.renea.arete_social.domain.model.TipoEntidadMapa
import com.renea.arete_social.domain.model.Ubicacion
import com.renea.arete_social.domain.model.VisibilidadEvento
import com.renea.arete_social.testutils.RelojFalso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServicioPasesTest {

    private val reloj = RelojFalso()
    private val aqui = Ubicacion(3.4516, -76.5320, 0L)
    private val lejos = Ubicacion(3.60, -76.53, 0L)

    // Firma de mentira pero determinista: suficiente para probar las reglas (la real es ECDSA en GestorIdentidadEcdsa).
    private val firmar: (String) -> String? = { "firma:${it.hashCode()}" }
    private val verificar: (String, String, String) -> Boolean = { datos, firma, clave ->
        clave == "pub-ana" && firma == "firma:${datos.hashCode()}"
    }

    private fun servicioDesc(usuario: String) = ServicioDescubrimiento(
        almacen = AlmacenEnMemoria(), usuarioIdProveedor = { usuario },
        politica = PoliticaDescubrimiento(reloj), reloj = reloj, dispatcher = Dispatchers.Unconfined
    )

    private fun servicioPases(desc: ServicioDescubrimiento, verificador: VerificadorEntidades? = null) = ServicioPases(
        descubrimiento = desc, politica = PoliticaDescubrimiento(reloj), reloj = reloj,
        firmar = firmar, miClavePublica = { "pub-ana" }, verificarFirma = verificar, verificador = verificador
    )

    private val descAna = servicioDesc("ana")
    private val pasesAna = servicioPases(descAna)
    private val descBeto = servicioDesc("beto")
    private val verificador = object : VerificadorEntidades {
        var respuesta: Boolean? = true
        override suspend fun existe(snapshot: EntidadSnapshot): Boolean? = respuesta
    }
    private val pasesBeto = servicioPases(descBeto, verificador)

    private fun lugar(id: String, lat: Double = 3.4520) = Lugar(id, "Lugar $id", "desc $id", Ubicacion(lat, -76.5320, 0L), "chat-$id")
    private fun evento(id: String, vis: VisibilidadEvento = VisibilidadEvento.PUBLICO, fecha: Long = reloj.ahora + 3_600_000) = Evento(
        id, "Evento $id", "", fecha, Ubicacion(3.4518, -76.5320, 0L), null, "x", null, emptyList(), "chat-$id", vis
    )

    private fun snap(id: String, tipo: TipoEntidadMapa = TipoEntidadMapa.LUGAR) =
        EntidadSnapshot(tipo, id, "Nombre $id", "d", 3.45, -76.53, "chat-$id")

    /** Ana descubre cosas y le arma un pase a Beto. */
    private fun paseDeAna(vararg ids: String): PaseDescubrimiento = runBlocking {
        descAna.registrarCercanos(aqui, ids.map { lugar(it) }, emptyList())
        pasesAna.crear("ana", "beto")!!
    }

    // ── Emitir ───────────────────────────────────────────────────────────────

    @Test
    fun `sin nada que compartir no hay pase`() {
        assertNull(pasesAna.crear("ana", "beto"))
    }

    @Test
    fun `el pase incluye lo cercano y lo reciente publico y va firmado`() = runBlocking {
        descAna.registrarCercanos(aqui, listOf(lugar("l1")), listOf(evento("e1")))
        descAna.registrarCercanos(lejos, emptyList(), emptyList()) // ahora son «recientes»
        val pase = pasesAna.crear("ana", "beto")!!
        assertEquals(setOf("l1", "e1"), pase.entidades.map { it.id }.toSet())
        assertEquals("ana", pase.emisorId)
        assertEquals("beto", pase.receptorId)
        assertEquals("pub-ana", pase.clavePublicaEmisor)
        assertTrue(verificar(pase.datosFirmados(), pase.firma, pase.clavePublicaEmisor))
    }

    @Test
    fun `nunca se incluyen eventos no publicos`() = runBlocking {
        descAna.registrarCercanos(aqui, listOf(lugar("l1")), listOf(evento("e1"), evento("e2", VisibilidadEvento.AMIGOS), evento("e3", VisibilidadEvento.COMUNIDAD)))
        val pase = pasesAna.crear("ana", "beto")!!
        assertEquals(setOf("l1", "e1"), pase.entidades.map { it.id }.toSet())
    }

    @Test
    fun `lo recibido por pase no se vuelve a compartir`() = runBlocking {
        descAna.registrar(Descubrimiento(snap("recibido"), paseRecibidoEn = reloj.ahora, paseEmisorId = "carlos"))
        assertNull(pasesAna.crear("ana", "beto"))
    }

    @Test
    fun `se ofrecen como mucho 20 y primero lo mas fresco`() = runBlocking {
        val ids = (1..25).map { "l$it" }
        descAna.registrarCercanos(aqui, ids.map { lugar(it) }, emptyList())
        val pase = pasesAna.crear("ana", "beto")!!
        assertEquals(PaseDescubrimiento.MAX_ENTIDADES, pase.entidades.size)
    }

    @Test
    fun `cerca va antes que reciente al priorizar`() = runBlocking {
        descAna.registrarCercanos(aqui, listOf(lugar("viejo")), emptyList())
        reloj.avanzarMinutos(5)
        descAna.registrarCercanos(lejos, emptyList(), emptyList())          // viejo → reciente
        descAna.registrarCercanos(aqui, listOf(lugar("nuevo", lat = 3.4519)), emptyList()) // nuevo → cerca (y viejo vuelve a estar cerca)
        val pase = pasesAna.crear("ana", "beto")!!
        assertEquals(2, pase.entidades.size)
    }

    @Test
    fun `no se vuelve a ofrecer lo que ya se ofrecio`() = runBlocking {
        descAna.registrarCercanos(aqui, listOf(lugar("l1"), lugar("l2")), emptyList())
        val primero = pasesAna.crear("ana", "beto")!!
        val ya = primero.entidades.map { it.clave }.toSet()
        assertNull(pasesAna.crear("ana", "beto", yaOfrecidos = ya))
        descAna.registrarCercanos(aqui, listOf(lugar("l1"), lugar("l2"), lugar("l3")), emptyList())
        val segundo = pasesAna.crear("ana", "beto", yaOfrecidos = ya)!!
        assertEquals(listOf("l3"), segundo.entidades.map { it.id })
    }

    @Test
    fun `nombre y descripcion largos se recortan para caber`() = runBlocking {
        val largo = Lugar("l1", "N".repeat(500), "D".repeat(900), Ubicacion(3.4520, -76.5320, 0L), "chat-l1")
        descAna.registrarCercanos(aqui, listOf(largo), emptyList())
        val e = pasesAna.crear("ana", "beto")!!.entidades.single()
        assertEquals(120, e.nombre.length)
        assertEquals(300, e.descripcion.length)
    }

    // ── Recibir ──────────────────────────────────────────────────────────────

    @Test
    fun `un pase valido hace visibles los elementos por 24 horas pero no compartibles`() = runBlocking {
        val pase = paseDeAna("l1", "l2")
        val r = pasesBeto.aceptar(pase, remitenteTransporte = "ana", miId = "beto")
        assertTrue(r is ResultadoPase.Aceptado)
        assertEquals(2, (r as ResultadoPase.Aceptado).nuevos.size)
        val visibles = descBeto.visibles.value
        assertEquals(2, visibles.size)
        assertTrue(visibles.all { it.origen == OrigenVisibilidad.PASE })
        assertEquals(reloj.ahora + 24 * 3_600_000L, visibles.first().venceEn)
        assertTrue(descBeto.compartibles().isEmpty())
    }

    @Test
    fun `aceptar un pase avisa a la interfaz`() = runBlocking {
        val pase = paseDeAna("l1", "l2")
        val aviso = async(Dispatchers.Unconfined) { withTimeout(2_000) { pasesBeto.avisos.first() } }
        pasesBeto.aceptar(pase, "ana", "beto")
        assertEquals("Alguien cercano te compartió 2 lugares por 24 h", aviso.await())
    }

    @Test
    fun `rechaza un pase que no va dirigido a mi`() = runBlocking {
        val r = pasesBeto.aceptar(paseDeAna("l1"), "ana", miId = "carlos")
        assertTrue(r is ResultadoPase.Rechazado)
        assertTrue(descBeto.visibles.value.isEmpty())
    }

    @Test
    fun `rechaza un pase cuyo emisor no es quien lo envio por la malla`() = runBlocking {
        // Carlos reenvía un pase de Ana: el router autentica a Carlos, no a Ana.
        val r = pasesBeto.aceptar(paseDeAna("l1"), remitenteTransporte = "carlos", miId = "beto")
        assertTrue(r is ResultadoPase.Rechazado)
    }

    @Test
    fun `rechaza un pase con la firma alterada`() = runBlocking {
        val pase = paseDeAna("l1")
        val alterado = pase.copy(entidades = pase.entidades.map { it.copy(chatId = "chat-ajeno") })
        val r = pasesBeto.aceptar(alterado, "ana", "beto")
        assertEquals(ResultadoPase.Rechazado("firma inválida"), r)
    }

    @Test
    fun `rechaza un pase sin firma o con clave distinta`() = runBlocking {
        val pase = paseDeAna("l1")
        assertEquals(ResultadoPase.Rechazado("firma inválida"), pasesBeto.aceptar(pase.copy(firma = ""), "ana", "beto"))
        assertEquals(ResultadoPase.Rechazado("firma inválida"), pasesBeto.aceptar(pase.copy(clavePublicaEmisor = "otra"), "ana", "beto"))
    }

    @Test
    fun `rechaza un pase viejo o del futuro`() = runBlocking {
        val pase = paseDeAna("l1")
        reloj.avanzarMinutos(11)
        assertEquals(ResultadoPase.Rechazado("demasiado viejo"), pasesBeto.aceptar(pase, "ana", "beto"))
        reloj.avanzarMinutos(-11 - 30)
        assertEquals(ResultadoPase.Rechazado("fecha futura"), pasesBeto.aceptar(pase, "ana", "beto"))
    }

    @Test
    fun `rechaza un pase vacio o con mas de 20 elementos`() = runBlocking {
        val base = paseDeAna("l1")
        assertEquals(ResultadoPase.Rechazado("vacío"), pasesBeto.aceptar(base.copy(entidades = emptyList()), "ana", "beto"))
        val muchos = base.copy(entidades = (1..21).map { snap("m$it") })
        assertEquals(ResultadoPase.Rechazado("demasiados elementos"), pasesBeto.aceptar(muchos, "ana", "beto"))
    }

    @Test
    fun `un pase no se renueva lo que ya veo se ignora`() = runBlocking {
        val pase = paseDeAna("l1")
        descBeto.registrarCercanos(aqui, listOf(lugar("l1")), emptyList()) // Beto ya lo ve (cerca)
        val r = pasesBeto.aceptar(pase, "ana", "beto") as ResultadoPase.Aceptado
        assertTrue(r.nuevos.isEmpty())
        assertEquals(1, r.ignorados)
    }

    @Test
    fun `un pase tampoco renueva otro pase vigente`() = runBlocking {
        val primero = paseDeAna("l1")
        pasesBeto.aceptar(primero, "ana", "beto")
        val venceAntes = descBeto.visibles.value.single().venceEn
        reloj.avanzarMinutos(5)
        val segundo = pasesAna.crear("ana", "beto")!!
        val r = pasesBeto.aceptar(segundo, "ana", "beto") as ResultadoPase.Aceptado
        assertTrue(r.nuevos.isEmpty())
        assertEquals(venceAntes, descBeto.visibles.value.single().venceEn)
    }

    @Test
    fun `tras vencer el pase se puede aceptar otro`() = runBlocking {
        pasesBeto.aceptar(paseDeAna("l1"), "ana", "beto")
        descAna.registrarCercanos(lejos, emptyList(), emptyList()) // Ana se aleja del lugar
        reloj.avanzarHoras(24.0)
        descBeto.refrescar()
        assertTrue(descBeto.visibles.value.isEmpty())
        // A Ana también se le venció (estuvo cerca hace 24 h): ya no tiene nada que compartir.
        descAna.refrescar()
        assertNull(pasesAna.crear("ana", "beto"))
        descAna.registrarCercanos(aqui, listOf(lugar("l1")), emptyList())
        val r = pasesBeto.aceptar(pasesAna.crear("ana", "beto")!!, "ana", "beto") as ResultadoPase.Aceptado
        assertEquals(1, r.nuevos.size)
    }

    @Test
    fun `elementos invalidos se ignoran pero los demas se aceptan`() = runBlocking {
        val base = paseDeAna("ok")
        val malos = listOf(
            snap("lat").copy(latitud = 200.0),
            snap("sinnombre").copy(nombre = ""),
            snap("ev-amigos", TipoEntidadMapa.EVENTO).copy(visibilidad = VisibilidadEvento.AMIGOS, fechaEvento = reloj.ahora)
        )
        // Se re-firma como Ana para que la firma sea válida.
        val sinFirma = base.copy(entidades = base.entidades + malos)
        val firmado = sinFirma.copy(firma = firmar(sinFirma.datosFirmados())!!)
        val r = pasesBeto.aceptar(firmado, "ana", "beto") as ResultadoPase.Aceptado
        assertEquals(listOf("ok"), r.nuevos.map { it.id })
        assertEquals(3, r.ignorados)
    }

    @Test
    fun `un evento ya terminado no se acepta`() = runBlocking {
        val base = paseDeAna("ok")
        val viejo = snap("ev", TipoEntidadMapa.EVENTO).copy(fechaEvento = reloj.ahora - 3 * 24 * 3_600_000L)
        val p = base.copy(entidades = listOf(viejo))
        val firmado = p.copy(firma = firmar(p.datosFirmados())!!)
        val r = pasesBeto.aceptar(firmado, "ana", "beto") as ResultadoPase.Aceptado
        assertTrue(r.nuevos.isEmpty())
    }

    // ── Verificación diferida ────────────────────────────────────────────────

    @Test
    fun `lo que el servidor confirma queda y se quita de pendientes`() = runBlocking {
        pasesBeto.aceptar(paseDeAna("l1"), "ana", "beto")
        assertEquals(1, pasesBeto.cantidadPendientes)
        verificador.respuesta = true
        assertEquals(0, pasesBeto.verificarPendientes())
        assertEquals(0, pasesBeto.cantidadPendientes)
        assertEquals(1, descBeto.visibles.value.size)
    }

    @Test
    fun `lo que no existe en el servidor se olvida`() = runBlocking {
        pasesBeto.aceptar(paseDeAna("falso"), "ana", "beto")
        verificador.respuesta = false
        assertEquals(1, pasesBeto.verificarPendientes())
        assertTrue(descBeto.visibles.value.isEmpty())
        assertEquals(0, pasesBeto.cantidadPendientes)
    }

    @Test
    fun `sin internet se conserva y se reintenta mas tarde`() = runBlocking {
        pasesBeto.aceptar(paseDeAna("l1"), "ana", "beto")
        verificador.respuesta = null
        assertEquals(0, pasesBeto.verificarPendientes())
        assertEquals(1, pasesBeto.cantidadPendientes)
        assertEquals(1, descBeto.visibles.value.size)
        verificador.respuesta = false
        assertEquals(1, pasesBeto.verificarPendientes())
    }

    @Test
    fun `sin verificador no hay nada que comprobar`() = runBlocking {
        val sinVerificador = servicioPases(servicioDesc("beto"))
        assertEquals(0, sinVerificador.verificarPendientes())
        assertFalse(sinVerificador.cantidadPendientes < 0)
        assertNotNull(sinVerificador.avisos)
    }
}
