package com.renea.arete_social.data.mesh

import com.renea.arete_social.data.local.perfil.CachePerfilLocal
import com.renea.arete_social.domain.mesh.MessageRouter
import com.renea.arete_social.domain.mesh.PaqueteMesh
import com.renea.arete_social.domain.mesh.PeerConnection
import com.renea.arete_social.domain.model.EstadoPerfilSnapshot
import com.renea.arete_social.domain.model.ProtocoloPerfilBle
import com.renea.arete_social.domain.model.SnapshotPerfil
import com.renea.arete_social.testutils.FakeSharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GestorPerfilBleTest {

    private class RouterFalso : MessageRouter {
        val entrantes = MutableSharedFlow<PaqueteMesh>(extraBufferCapacity = 16)
        val enviados = mutableListOf<PaqueteMesh>()
        val peers = MutableStateFlow(1)
        override suspend fun enrutar(paquete: PaqueteMesh, origenMac: String?) {}
        override suspend fun reenviar(paquete: PaqueteMesh) { enviados += paquete }
        override fun paquetesEntrantes(chatId: String): Flow<PaqueteMesh> = emptyFlow()
        override fun todosLosPaquetesEntrantes(): Flow<PaqueteMesh> = entrantes
        override fun paquetesParaMi(miId: String): Flow<PaqueteMesh> = emptyFlow()
        override val peersConectados: StateFlow<Int> get() = peers.asStateFlow()
        override fun registrarPeer(peer: PeerConnection) {}
        override fun removerPeer(dispositivoId: String) {}
    }

    private var ahora = 1_000_000_000_000L
    private lateinit var router: RouterFalso
    private lateinit var cache: CachePerfilLocal
    private lateinit var scopeGestor: CoroutineScope
    private lateinit var job: Job

    @Before fun preparar() {
        router = RouterFalso()
        cache = CachePerfilLocal(FakeSharedPreferences(), reloj = { ahora })
        job = kotlinx.coroutines.SupervisorJob()
        scopeGestor = CoroutineScope(job + UnconfinedTestDispatcher())
    }

    @After fun limpiar() { job.cancel() }

    private var amigos: Set<String> = setOf("vecino")

    private fun gestor(miId: String = "yo") =
        GestorPerfilBle(router, cache, { miId }, { it in amigos }, { ahora }, scopeGestor).also { it.iniciar() }

    private fun paquete(remitente: String, dest: String?, contenido: String, chatId: String = ProtocoloPerfilBle.CHAT_ID) = PaqueteMesh(
        id = "p-${System.nanoTime()}", chatId = chatId,
        remitenteId = remitente, destinatarioId = dest, contenido = contenido, ttl = 1, timestamp = ahora
    )

    private fun miPerfil(privado: Boolean) {
        cache.actualizarMiPerfil("yo", "Ana", "hola", privado)
        cache.actualizarMisEstados("yo", emptyList())
    }

    @Test fun `mi perfil privado responde a una amistad con TTL 1 y dirigido`() = runTest {
        miPerfil(privado = true)
        gestor()
        router.entrantes.emit(paquete("vecino", null, ProtocoloPerfilBle.pedir("yo")))
        val r = router.enviados.single()
        assertEquals("vecino", r.destinatarioId)
        assertEquals(1, r.ttl)
        assertEquals(ProtocoloPerfilBle.CHAT_ID, r.chatId)
        assertNotNull(ProtocoloPerfilBle.decodificarDatos(r.contenido))
    }

    @Test fun `mi perfil publico responde a cualquiera cercano aunque no sea amistad`() = runTest {
        miPerfil(privado = false)
        gestor()
        router.entrantes.emit(paquete("desconocido", null, ProtocoloPerfilBle.pedir("yo")))
        val r = router.enviados.single()
        assertEquals("desconocido", r.destinatarioId)
        assertEquals(1, r.ttl)
        assertEquals(false, ProtocoloPerfilBle.decodificarDatos(r.contenido)!!.privado)
    }

    @Test fun `mi perfil privado responde a quien no es mi amistad unicamente con perfil privado y nombre`() = runTest {
        miPerfil(privado = true)
        gestor()
        router.entrantes.emit(paquete("desconocido", null, ProtocoloPerfilBle.pedir("yo")))
        val r = router.enviados.single()
        assertEquals("desconocido", r.destinatarioId)
        val decodificado = ProtocoloPerfilBle.decodificarDatos(r.contenido)
        assertNotNull(decodificado)
        assertEquals("yo", decodificado?.usuarioId)
        assertEquals("Ana", decodificado?.nombre)
        assertTrue(decodificado?.privado == true)
        assertTrue(decodificado?.descripcion.isNullOrBlank())
        assertTrue(decodificado?.estados.isNullOrEmpty())
        assertTrue(decodificado?.obras.isNullOrEmpty())
    }

    @Test fun `protocolo v2 entrega manifiesto completo a amigos y privado a desconocidos`() = runTest {
        com.renea.arete_social.domain.model.FeatureFlags.SYNC_V2 = true
        try {
            miPerfil(privado = true)
            gestor()

            // 1. Desconocido pide manifiesto
            router.entrantes.emit(paquete("desconocido", null, com.renea.arete_social.domain.model.ProtocoloPerfilV2.pedirManifiesto("yo")))
            val rDesconocido = router.enviados.removeAt(0)
            val manifDesc = com.renea.arete_social.domain.model.ProtocoloPerfilV2.decodificarManifiesto(rDesconocido.contenido)
            assertNotNull(manifDesc)
            assertEquals("Ana", manifDesc?.nombre)
            assertTrue(manifDesc?.esPrivado == true)
            assertTrue(manifDesc?.secciones.isNullOrEmpty())

            // 2. Amigo pide manifiesto
            ahora += 30_000L
            router.entrantes.emit(paquete("vecino", null, com.renea.arete_social.domain.model.ProtocoloPerfilV2.pedirManifiesto("yo")))
            val rAmigo = router.enviados.removeAt(0)
            val manifAmigo = com.renea.arete_social.domain.model.ProtocoloPerfilV2.decodificarManifiesto(rAmigo.contenido)
            assertNotNull(manifAmigo)
            assertEquals("Ana", manifAmigo?.nombre)
            assertEquals(false, manifAmigo?.esPrivado)
        } finally {
            com.renea.arete_social.domain.model.FeatureFlags.SYNC_V2 = false
        }
    }

    @Test fun `procesa manifiesto entrante recibido por CHAT_ID_MANIFEST`() = runTest {
        com.renea.arete_social.domain.model.FeatureFlags.SYNC_V2 = true
        try {
            gestor()
            val manifiesto = com.renea.arete_social.domain.model.ManifiestoPerfil(
                v = 2,
                usuarioId = "vecino",
                nombre = "Vecino Amigo",
                versionGlobal = 1L,
                esPrivado = false,
                fotoHash = "hash123",
                portadaHash = "portada123",
                secciones = mapOf(
                    com.renea.arete_social.domain.model.TipoSeccionPerfil.GUSTOS.name to com.renea.arete_social.domain.model.SeccionHeader(1L, "gustosHash", 50)
                ),
                actualizadoEn = ahora
            )
            val contenido = com.renea.arete_social.domain.model.ProtocoloPerfilV2.codificarManifiesto(manifiesto)
            val paquete = PaqueteMesh(
                id = "p-manif",
                chatId = com.renea.arete_social.domain.model.ProtocoloPerfilV2.CHAT_ID_MANIFEST,
                remitenteId = "vecino",
                destinatarioId = null,
                contenido = contenido,
                ttl = 1,
                timestamp = ahora
            )
            router.entrantes.emit(paquete)
            // Al procesar el manifiesto en CHAT_ID_MANIFEST, debe solicitar las secciones disponibles
            assertTrue(router.enviados.any { it.chatId == com.renea.arete_social.domain.model.ProtocoloPerfilV2.CHAT_ID_SECCIONES })
        } finally {
            com.renea.arete_social.domain.model.FeatureFlags.SYNC_V2 = false
        }
    }

    @Test fun `no respondo por el perfil de otra persona`() = runTest {
        miPerfil(privado = true)
        gestor()
        router.entrantes.emit(paquete("vecino", null, ProtocoloPerfilBle.pedir("otra-persona")))
        assertTrue(router.enviados.isEmpty())
    }

    @Test fun `no respondo dos veces seguidas a la misma persona`() = runTest {
        miPerfil(privado = true)
        gestor()
        router.entrantes.emit(paquete("vecino", null, ProtocoloPerfilBle.pedir("yo")))
        router.entrantes.emit(paquete("vecino", null, ProtocoloPerfilBle.pedir("yo")))
        assertEquals(1, router.enviados.size)
    }

    @Test fun `ignoro mi propia peticion que vuelve por la red`() = runTest {
        miPerfil(privado = true)
        gestor()
        router.entrantes.emit(paquete("yo", null, ProtocoloPerfilBle.pedir("yo")))
        assertTrue(router.enviados.isEmpty())
    }

    private fun respuesta(duenoId: String) = ProtocoloPerfilBle.codificarDatos(
        SnapshotPerfil(
            usuarioId = duenoId, nombre = "Dueña", privado = true,
            estados = listOf(EstadoPerfilSnapshot("e1", "hola", ahora))
        )
    )

    @Test fun `acepto el perfil enviado por su propio dueno`() = runTest {
        gestor()
        router.entrantes.emit(paquete("dueno", "yo", respuesta("dueno")))
        assertNotNull(cache.recibido("dueno"))
    }

    @Test fun `rechazo un perfil de otra persona enviado por un tercero`() = runTest {
        gestor()
        // "intruso" intenta hacer pasar por suyo el perfil de "dueno"
        router.entrantes.emit(paquete("intruso", "yo", respuesta("dueno")))
        assertNull(cache.recibido("dueno"))
        assertNull(cache.recibido("intruso"))
    }

    @Test fun `ignoro respuestas dirigidas a otra persona`() = runTest {
        gestor()
        router.entrantes.emit(paquete("dueno", "alguien-mas", respuesta("dueno")))
        assertNull(cache.recibido("dueno"))
    }

    @Test fun `sin dispositivos conectados no se busca`() = runTest {
        router.peers.value = 0
        val resultado = gestor().solicitar("dueno")
        assertEquals(ResultadoPerfilBle.SinDispositivos, resultado)
        assertTrue(router.enviados.isEmpty())
    }

    @Test fun `v2 responde seccion modular a amigo y la rechaza a desconocido`() = runTest {
        com.renea.arete_social.domain.model.FeatureFlags.SYNC_V2 = true
        try {
            miPerfil(privado = true)
            amigos = setOf("amigo")
            gestor()

            // Petición desde desconocido -> no debe responder
            router.entrantes.emit(
                paquete(
                    "desconocido",
                    "yo",
                    com.renea.arete_social.domain.model.ProtocoloPerfilV2.pedirSeccion("yo", com.renea.arete_social.domain.model.TipoSeccionPerfil.BASE, 1L),
                    chatId = com.renea.arete_social.domain.model.ProtocoloPerfilV2.CHAT_ID_SECCIONES
                )
            )
            assertTrue(router.enviados.isEmpty())

            // Petición desde amigo -> responde sección
            router.entrantes.emit(
                paquete(
                    "amigo",
                    "yo",
                    com.renea.arete_social.domain.model.ProtocoloPerfilV2.pedirSeccion("yo", com.renea.arete_social.domain.model.TipoSeccionPerfil.BASE, 1L),
                    chatId = com.renea.arete_social.domain.model.ProtocoloPerfilV2.CHAT_ID_SECCIONES
                )
            )
            assertEquals(1, router.enviados.size)
            val enviado = router.enviados.removeAt(0)
            assertEquals("amigo", enviado.destinatarioId)
            val sec = com.renea.arete_social.domain.model.ProtocoloPerfilV2.parsearSeccion(enviado.contenido)
            assertNotNull(sec)
            assertEquals(com.renea.arete_social.domain.model.TipoSeccionPerfil.BASE, sec?.tipoSeccion)
        } finally {
            com.renea.arete_social.domain.model.FeatureFlags.SYNC_V2 = false
        }
    }

    @Test fun `v2 reensambla seccion fragmentada recibida en multiples chunks`() = runTest {
        com.renea.arete_social.domain.model.FeatureFlags.SYNC_V2 = true
        try {
            gestor()
            // Inicializar previo
            cache.guardarRecibido(SnapshotPerfil(usuarioId = "amigo", nombre = "Amigo", privado = false))

            val chunk1 = "{\"descripcion\":\"Parte 1 "
            val chunk2 = "y Parte 2\"}"
            val p1 = com.renea.arete_social.domain.model.ProtocoloPerfilV2.codificarSeccion(
                duenoId = "amigo",
                seccion = com.renea.arete_social.domain.model.TipoSeccionPerfil.BASE,
                indice = 0,
                total = 2,
                payloadJson = chunk1
            )
            val p2 = com.renea.arete_social.domain.model.ProtocoloPerfilV2.codificarSeccion(
                duenoId = "amigo",
                seccion = com.renea.arete_social.domain.model.TipoSeccionPerfil.BASE,
                indice = 1,
                total = 2,
                payloadJson = chunk2
            )

            // Emitir chunk 0: todavía no se aplica
            router.entrantes.emit(paquete("amigo", "yo", p1, chatId = com.renea.arete_social.domain.model.ProtocoloPerfilV2.CHAT_ID_SECCIONES))
            assertEquals("", cache.recibido("amigo")?.descripcion ?: "")

            // Emitir chunk 1: se reensambla y se guarda
            router.entrantes.emit(paquete("amigo", "yo", p2, chatId = com.renea.arete_social.domain.model.ProtocoloPerfilV2.CHAT_ID_SECCIONES))
            assertEquals("Parte 1 y Parte 2", cache.recibido("amigo")?.descripcion)
        } finally {
            com.renea.arete_social.domain.model.FeatureFlags.SYNC_V2 = false
        }
    }
}
