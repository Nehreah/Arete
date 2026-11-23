package com.renea.arete_social.data.mesh

import com.renea.arete_social.domain.descubrimiento.PoliticaDescubrimiento
import com.renea.arete_social.domain.mesh.MessageRouter
import com.renea.arete_social.domain.mesh.PaqueteMesh
import com.renea.arete_social.domain.mesh.PeerConnection
import com.renea.arete_social.domain.model.Evento
import com.renea.arete_social.domain.model.Lugar
import com.renea.arete_social.domain.model.ProtocoloPaseBle
import com.renea.arete_social.domain.model.Ubicacion
import com.renea.arete_social.domain.model.VisibilidadEvento
import com.renea.arete_social.feature.descubrimiento.AlmacenEnMemoria
import com.renea.arete_social.feature.descubrimiento.ServicioDescubrimiento
import com.renea.arete_social.feature.descubrimiento.ServicioPases
import com.renea.arete_social.testutils.RelojFalso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Router de mentira: guarda lo que se envía y permite «recibir» paquetes. */
internal class RouterFalso : MessageRouter {
    val enviados = mutableListOf<PaqueteMesh>()
    val entrantes = MutableSharedFlow<PaqueteMesh>(extraBufferCapacity = 16)

    override suspend fun enrutar(paquete: PaqueteMesh, origenMac: String?) = Unit
    override suspend fun reenviar(paquete: PaqueteMesh) { enviados += paquete }
    override fun paquetesEntrantes(chatId: String): Flow<PaqueteMesh> = entrantes.asSharedFlow().filter { it.chatId == chatId }
    override fun todosLosPaquetesEntrantes(): Flow<PaqueteMesh> = entrantes.asSharedFlow()
    override fun paquetesParaMi(miId: String): Flow<PaqueteMesh> = entrantes.asSharedFlow()
    override val peersConectados: StateFlow<Int> = MutableStateFlow(1)
    override fun registrarPeer(peer: PeerConnection) = Unit
    override fun removerPeer(dispositivoId: String) = Unit
}

class GestorPasesBleTest {

    private val reloj = RelojFalso()
    private val aqui = Ubicacion(3.4516, -76.5320, 0L)
    private val router = RouterFalso()
    private var compartir = true

    private val desc = ServicioDescubrimiento(
        AlmacenEnMemoria(), { "ana" }, PoliticaDescubrimiento(reloj), reloj, Dispatchers.Unconfined
    )
    private val pases = ServicioPases(
        descubrimiento = desc, politica = PoliticaDescubrimiento(reloj), reloj = reloj,
        firmar = { "firma:${it.hashCode()}" }, miClavePublica = { "pub-ana" },
        verificarFirma = { d, f, k -> k == "pub-ana" && f == "firma:${d.hashCode()}" }
    )
    private val trabajo = Job()
    private val gestor = GestorPasesBle(
        router = router, servicioPases = pases, miIdProveedor = { "ana" },
        compartirActivado = { compartir }, reloj = reloj,
        scope = CoroutineScope(trabajo + Dispatchers.Unconfined)
    )

    private fun lugar(id: String) = Lugar(id, "Lugar $id", "d", Ubicacion(3.4520, -76.5320, 0L), "chat-$id")
    private fun evento(id: String, vis: VisibilidadEvento = VisibilidadEvento.PUBLICO) = Evento(
        id, "Evento $id", "", reloj.ahora + 3_600_000, Ubicacion(3.4518, -76.5320, 0L), null, "x", null, emptyList(), "chat-$id", vis
    )

    private fun anuncio(de: String, saltos: Int = 0, id: String = "ident-$de-$saltos") = PaqueteMesh(
        id = id, chatId = "general-500m-antired-chat", remitenteId = de, destinatarioId = null,
        contenido = "[IDENT:v2:clave:e2ee]", saltosRealizados = saltos, timestamp = reloj.ahora
    )

    @Test
    fun `ofrece a un vecino un pase dirigido a el con TTL 1 y por el canal de pases`() = runBlocking {
        desc.registrarCercanos(aqui, listOf(lugar("l1")), emptyList())
        gestor.ofrecer("ana", "beto")
        val p = router.enviados.single()
        assertEquals(ProtocoloPaseBle.CHAT_ID, p.chatId)
        assertEquals("ana", p.remitenteId)
        assertEquals("beto", p.destinatarioId)
        assertEquals(1, p.ttl)
        assertTrue(p.contenido.startsWith("[PASE:v1:"))
    }

    @Test
    fun `sin nada que compartir no envia nada`() = runBlocking {
        gestor.ofrecer("ana", "beto")
        assertTrue(router.enviados.isEmpty())
    }

    @Test
    fun `con el ajuste desactivado no ofrece`() = runBlocking {
        desc.registrarCercanos(aqui, listOf(lugar("l1")), emptyList())
        compartir = false
        gestor.ofrecer("ana", "beto")
        assertTrue(router.enviados.isEmpty())
    }

    @Test
    fun `no repite la oferta dentro de un minuto ni lo ya ofrecido`() = runBlocking {
        desc.registrarCercanos(aqui, listOf(lugar("l1")), emptyList())
        gestor.ofrecer("ana", "beto")
        gestor.ofrecer("ana", "beto")                     // dentro del minuto: nada
        assertEquals(1, router.enviados.size)
        reloj.avanzarMinutos(2)
        gestor.ofrecer("ana", "beto")                     // pasó el minuto pero nada nuevo: nada
        assertEquals(1, router.enviados.size)
        desc.registrarCercanos(aqui, listOf(lugar("l1"), lugar("l2")), emptyList())
        gestor.ofrecer("ana", "beto")                     // algo nuevo: solo lo nuevo
        assertEquals(2, router.enviados.size)
    }

    @Test
    fun `cada vecino recibe su propia oferta`() = runBlocking {
        desc.registrarCercanos(aqui, listOf(lugar("l1")), emptyList())
        gestor.ofrecer("ana", "beto")
        gestor.ofrecer("ana", "carlos")
        assertEquals(listOf("beto", "carlos"), router.enviados.map { it.destinatarioId })
    }

    @Test
    fun `lo ofrecido vuelve a ofrecerse pasadas 24 horas si sigue vigente`() = runBlocking {
        desc.registrarCercanos(aqui, listOf(lugar("l1")), emptyList())
        gestor.ofrecer("ana", "beto")
        reloj.avanzarHoras(25.0)
        desc.registrarCercanos(aqui, listOf(lugar("l1")), emptyList()) // Ana sigue cerca: vigente
        gestor.ofrecer("ana", "beto")
        assertEquals(2, router.enviados.size)
    }

    @Test
    fun `una solicitud explicita de sincronizacion dispara la oferta`() = runBlocking {
        desc.registrarCercanos(aqui, listOf(lugar("l1")), emptyList())
        gestor.iniciar()
        router.entrantes.emit(PaqueteMesh(
            id = "solicitud", chatId = ProtocoloPaseBle.CHAT_ID,
            remitenteId = "beto", destinatarioId = "ana",
            contenido = "[PARCHE_SYNC_REQUEST:v1]", ttl = 1, timestamp = reloj.ahora
        ))
        assertEquals(1, router.enviados.size)
        assertEquals("beto", router.enviados.single().destinatarioId)
        gestor.detener()
        trabajo.cancel()
    }

    @Test
    fun `con Parche activo cada solicitud se responde sola y con todo lo compartible`() = runBlocking {
        desc.registrarCercanos(aqui, listOf(lugar("l1")), emptyList())
        gestor.ofrecer("ana", "beto", solicitado = true)
        assertEquals(1, router.enviados.size)
        // Una segunda solicitud (p. ej. el primer pase se perdió) se atiende pasados unos segundos y repite lo vigente.
        reloj.avanzarMinutos(1)
        gestor.ofrecer("ana", "beto", solicitado = true)
        assertEquals(2, router.enviados.size)
        // Otra persona que también quiere sincronizar recibe lo suyo sin que el dueño confirme nada.
        gestor.ofrecer("ana", "carlos", solicitado = true)
        assertEquals(listOf("beto", "beto", "carlos"), router.enviados.map { it.destinatarioId })
    }

    @Test
    fun `solicitudes en rafaga se limitan para evitar spam`() = runBlocking {
        desc.registrarCercanos(aqui, listOf(lugar("l1")), emptyList())
        gestor.ofrecer("ana", "beto", solicitado = true)
        gestor.ofrecer("ana", "beto", solicitado = true)
        assertEquals(1, router.enviados.size)
    }

    @Test
    fun `un anuncio de identidad de un vecino no comparte automaticamente`() = runBlocking {
        desc.registrarCercanos(aqui, listOf(lugar("l1")), emptyList())
        gestor.iniciar()
        router.entrantes.emit(anuncio("beto", saltos = 0))
        assertTrue(router.enviados.isEmpty())
        gestor.detener()
        trabajo.cancel()
    }

    @Test
    fun `un anuncio retransmitido no es un vecino directo y no se le ofrece`() = runBlocking {
        desc.registrarCercanos(aqui, listOf(lugar("l1")), emptyList())
        gestor.iniciar()
        router.entrantes.emit(anuncio("lejano", saltos = 2))
        assertTrue(router.enviados.isEmpty())
        gestor.detener()
        trabajo.cancel()
    }

    @Test
    fun `mis propios anuncios y otros paquetes no disparan nada`() = runBlocking {
        desc.registrarCercanos(aqui, listOf(lugar("l1")), emptyList())
        gestor.iniciar()
        router.entrantes.emit(anuncio("ana"))                                   // eco propio
        router.entrantes.emit(anuncio("beto").copy(id = "msg", contenido = "hola")) // mensaje normal
        assertTrue(router.enviados.isEmpty())
        gestor.detener()
        trabajo.cancel()
    }

    @Test
    fun `reiniciar olvida lo ofrecido`() = runBlocking {
        desc.registrarCercanos(aqui, listOf(lugar("l1")), emptyList())
        gestor.ofrecer("ana", "beto")
        gestor.reiniciar()
        gestor.ofrecer("ana", "beto")
        assertEquals(2, router.enviados.size)
    }

    @Test
    fun `no ofrece eventos privados`() = runBlocking {
        desc.registrarCercanos(aqui, emptyList(), listOf(evento("e1", VisibilidadEvento.AMIGOS)))
        gestor.ofrecer("ana", "beto")
        assertTrue(router.enviados.isEmpty())
    }
}
