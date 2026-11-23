package com.renea.arete_social.data.mesh

import com.renea.arete_social.domain.descubrimiento.PoliticaDescubrimiento
import com.renea.arete_social.domain.mesh.PaqueteMesh
import com.renea.arete_social.domain.model.EntidadSnapshot
import com.renea.arete_social.domain.model.OrigenVisibilidad
import com.renea.arete_social.domain.model.PaseDescubrimiento
import com.renea.arete_social.domain.model.ProtocoloPaseBle
import com.renea.arete_social.domain.model.TipoEntidadMapa
import com.renea.arete_social.feature.descubrimiento.AlmacenEnMemoria
import com.renea.arete_social.feature.descubrimiento.ServicioDescubrimiento
import com.renea.arete_social.feature.descubrimiento.ServicioPases
import com.renea.arete_social.testutils.RelojFalso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Recepción de pases por la malla, con el formato real `[PASE:v1:<json>]`. */
class GestorPasesBleRecepcionTest {

    private val reloj = RelojFalso()
    private val firmar: (String) -> String? = { "firma:${it.hashCode()}" }
    private val verificar: (String, String, String) -> Boolean = { d, f, k -> k == "pub-ana" && f == "firma:${d.hashCode()}" }

    private val router = RouterFalso()
    private val descBeto = ServicioDescubrimiento(AlmacenEnMemoria(), { "beto" }, PoliticaDescubrimiento(reloj), reloj, Dispatchers.Unconfined)
    private val pasesBeto = ServicioPases(
        descubrimiento = descBeto, politica = PoliticaDescubrimiento(reloj), reloj = reloj,
        firmar = firmar, miClavePublica = { "pub-beto" }, verificarFirma = verificar
    )
    private val trabajo = Job()
    private val gestor = GestorPasesBle(
        router = router, servicioPases = pasesBeto, miIdProveedor = { "beto" },
        compartirActivado = { true }, reloj = reloj, scope = CoroutineScope(trabajo + Dispatchers.Unconfined)
    )

    private fun pase(receptor: String = "beto", emisor: String = "ana"): PaseDescubrimiento {
        val base = PaseDescubrimiento(
            id = "pase-1", emisorId = emisor, receptorId = receptor, emitidoEn = reloj.ahora,
            entidades = listOf(EntidadSnapshot(TipoEntidadMapa.LUGAR, "l1", "Biblioteca", "d", 3.45, -76.53, "chat-l1"))
        )
        return base.copy(firma = firmar(base.datosFirmados())!!, clavePublicaEmisor = "pub-ana")
    }

    private fun paquete(p: PaseDescubrimiento, remitente: String = "ana", destinatario: String? = "beto") = PaqueteMesh(
        id = "paq-${p.id}-$remitente", chatId = ProtocoloPaseBle.CHAT_ID, remitenteId = remitente,
        destinatarioId = destinatario, contenido = ProtocoloPaseBle.codificar(p), ttl = 1, timestamp = reloj.ahora
    )

    @Test
    fun `un pase recibido por la malla se acepta y se ve por 24 horas`() = runBlocking {
        gestor.iniciar()
        router.entrantes.emit(paquete(pase()))
        val v = descBeto.visibles.value.single()
        assertEquals("l1", v.snapshot.id)
        assertEquals(OrigenVisibilidad.PASE, v.origen)
        gestor.detener(); trabajo.cancel()
    }

    @Test
    fun `un pase dirigido a otra persona se ignora`() = runBlocking {
        gestor.iniciar()
        router.entrantes.emit(paquete(pase(receptor = "carlos"), destinatario = "carlos"))
        assertTrue(descBeto.visibles.value.isEmpty())
        gestor.detener(); trabajo.cancel()
    }

    @Test
    fun `un pase reenviado por otra persona se descarta aunque la firma sea buena`() = runBlocking {
        gestor.iniciar()
        router.entrantes.emit(paquete(pase(emisor = "ana"), remitente = "carlos"))
        assertTrue(descBeto.visibles.value.isEmpty())
        gestor.detener(); trabajo.cancel()
    }

    @Test
    fun `un contenido que no es un pase no rompe nada`() = runBlocking {
        gestor.iniciar()
        router.entrantes.emit(paquete(pase()).copy(id = "basura", contenido = "[PASE:v1:{roto}]"))
        assertTrue(descBeto.visibles.value.isEmpty())
        gestor.detener(); trabajo.cancel()
    }
}
