package com.renea.arete_social.data.mesh

import com.renea.arete_social.data.crypto.EstadoVerificacionIdentidad
import com.renea.arete_social.data.crypto.GestorIdentidadEcdsa
import com.renea.arete_social.data.crypto.PinesIdentidad
import com.renea.arete_social.testutils.FakeSharedPreferences
import com.renea.arete_social.domain.mesh.PaqueteMesh
import com.renea.arete_social.domain.mesh.PeerConnection
import com.renea.arete_social.domain.model.Dispositivo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Pruebas de caracterización del motor de enrutamiento Mesh ([MessageRouterMesh]).
 *
 * Valida:
 * 1. Deduplicación por ID único (evitar tormentas de retransmisión).
 * 2. Filtrado de eco propio recibido desde la red.
 * 3. Descarte de paquetes con estructura inválida (ID vacío, longitud desmedida, reloj desfasado).
 * 4. Descarte de paquetes con más de 24h de antigüedad (anti-replay persistente).
 * 5. Entrega local selectiva (mensajes dirigidos a mí o broadcast vs dirigidos a otros nodos).
 * 6. Decremento y corte de TTL (paquetes con TTL <= 1 no se retransmiten).
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MessageRouterMeshTest {

    private val miUsuarioId = "mi-usuario-id-123"

    private fun crearRouter(miId: String? = miUsuarioId): MessageRouterMesh {
        // pinesIdentidad es ahora un parámetro requerido (no-nullable). Inyectamos
        // una instancia con FakeSharedPreferences para mantener los tests en JVM pura.
        return MessageRouterMesh(
            usuarioIdProveedor = { miId },
            gestorIdentidad = null, // Sin criptografía estricta: validamos solo ruteo
            pinesIdentidad = PinesIdentidad(prefsOverride = FakeSharedPreferences()),
            resolverClaveRemota = null
        )
    }

    private fun crearPaquete(
        id: String = UUID.randomUUID().toString(),
        chatId: String = "chat-general",
        remitenteId: String = "amigo-456",
        destinatarioId: String? = null,
        contenido: String = "Hola mesh",
        ttl: Int = 3,
        timestamp: Long = System.currentTimeMillis(),
        firma: String? = null,
        clavePublicaRemitente: String? = null
    ): PaqueteMesh {
        return PaqueteMesh(
            id = id,
            chatId = chatId,
            remitenteId = remitenteId,
            destinatarioId = destinatarioId,
            contenido = contenido,
            ttl = ttl,
            saltosRealizados = 0,
            timestamp = timestamp,
            firma = firma,
            clavePublicaRemitente = clavePublicaRemitente
        )
    }

    @Test
    fun `deduplicacion - mismo paquete recibido dos veces solo se entrega una vez`() = runTest {
        val router = crearRouter()
        val paquete = crearPaquete()
        val entregados = mutableListOf<PaqueteMesh>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            router.todosLosPaquetesEntrantes().collect { entregados.add(it) }
        }

        router.enrutar(paquete, origenMac = "AA:BB:CC:DD:EE:01")
        router.enrutar(paquete, origenMac = "AA:BB:CC:DD:EE:02") // Mismo ID por otra ruta

        assertEquals("Solo debe emitirse una copia del paquete", 1, entregados.size)
        assertEquals(paquete.id, entregados[0].id)
        job.cancel()
    }

    @Test
    fun `eco propio - paquete con mi propio remitenteId proveniente de la red es ignorado`() = runTest {
        val router = crearRouter()
        val paquetePropioEco = crearPaquete(remitenteId = miUsuarioId)
        val entregados = mutableListOf<PaqueteMesh>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            router.todosLosPaquetesEntrantes().collect { entregados.add(it) }
        }

        router.enrutar(paquetePropioEco, origenMac = "AA:BB:CC:DD:EE:01")

        assertTrue("El eco de un paquete propio debe ser ignorado", entregados.isEmpty())
        job.cancel()
    }

    @Test
    fun `estructura valida - descarta paquetes con id vacio o demasiado largo`() = runTest {
        val router = crearRouter()
        val entregados = mutableListOf<PaqueteMesh>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            router.todosLosPaquetesEntrantes().collect { entregados.add(it) }
        }

        val paqueteIdVacio = crearPaquete(id = "")
        val paqueteIdLargo = crearPaquete(id = "X".repeat(65))

        router.enrutar(paqueteIdVacio, origenMac = "AA:BB:CC:01")
        router.enrutar(paqueteIdLargo, origenMac = "AA:BB:CC:01")

        assertTrue("Paquetes con ID inválido deben ser descartados", entregados.isEmpty())
        job.cancel()
    }

    @Test
    fun `estructura valida - descarta paquetes con contenido que supera el maximo permitido`() = runTest {
        val router = crearRouter()
        val entregados = mutableListOf<PaqueteMesh>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            router.todosLosPaquetesEntrantes().collect { entregados.add(it) }
        }

        val paqueteGigante = crearPaquete(contenido = "A".repeat(PaqueteMesh.MAX_CONTENIDO_MESH_CHARS + 1))
        router.enrutar(paqueteGigante, origenMac = "AA:BB:CC:01")

        assertTrue("Paquetes desproporcionados deben ser descartados", entregados.isEmpty())
        job.cancel()
    }

    @Test
    fun `estructura valida - descarta paquetes con timestamp muy adelantado en el futuro`() = runTest {
        val router = crearRouter()
        val entregados = mutableListOf<PaqueteMesh>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            router.todosLosPaquetesEntrantes().collect { entregados.add(it) }
        }

        val timestampFuturo = System.currentTimeMillis() + PaqueteMesh.TOLERANCIA_FUTURO_MS + 60_000L
        val paqueteRelojFuturo = crearPaquete(timestamp = timestampFuturo)

        router.enrutar(paqueteRelojFuturo, origenMac = "AA:BB:CC:01")

        assertTrue("Paquetes del futuro fuera de tolerancia deben ser descartados", entregados.isEmpty())
        job.cancel()
    }

    @Test
    fun `anti-replay - descarta paquetes con timestamp mayor a 24 horas en el pasado`() = runTest {
        val router = crearRouter()
        val entregados = mutableListOf<PaqueteMesh>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            router.todosLosPaquetesEntrantes().collect { entregados.add(it) }
        }

        // Simula un replay attack: paquete de hace 25 horas reinyectado en la red
        val timestampAntiguo = System.currentTimeMillis() - (25 * 3600 * 1000L)
        val paqueteAntiguo = crearPaquete(timestamp = timestampAntiguo)

        router.enrutar(paqueteAntiguo, origenMac = "AA:BB:CC:01")

        assertTrue(
            "Paquetes de más de 24h de antigüedad deben ser descartados (ventana anti-replay persistente)",
            entregados.isEmpty()
        )
        job.cancel()
    }

    @Test
    fun `entrega local - paquete broadcast o dirigido a mi se entrega en paquetesEntrantes`() = runTest {
        val router = crearRouter()
        val entregados = mutableListOf<PaqueteMesh>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            router.todosLosPaquetesEntrantes().collect { entregados.add(it) }
        }

        val paqueteBroadcast = crearPaquete(destinatarioId = null)
        val paqueteParaMi = crearPaquete(destinatarioId = miUsuarioId)
        val paqueteParaOtro = crearPaquete(destinatarioId = "tercero-789")

        router.enrutar(paqueteBroadcast, origenMac = "AA:BB:CC:01")
        router.enrutar(paqueteParaMi, origenMac = "AA:BB:CC:02")
        router.enrutar(paqueteParaOtro, origenMac = "AA:BB:CC:03")

        assertEquals(2, entregados.size)
        assertTrue(entregados.any { it.id == paqueteBroadcast.id })
        assertTrue(entregados.any { it.id == paqueteParaMi.id })
        assertFalse(entregados.any { it.id == paqueteParaOtro.id })

        job.cancel()
    }

    private fun crearPeerFake(
        id: String = "peer-vecino-1",
        onEnviar: (PaqueteMesh) -> Unit
    ): PeerConnection {
        return object : PeerConnection {
            override val dispositivo: Dispositivo = Dispositivo(
                id = id,
                nombre = id,
                señal = -50,
                ultimaConexion = System.currentTimeMillis(),
                usuarioId = "usuario-$id",
                distanciaMetros = 2.0,
                macAddress = id
            )
            override suspend fun conectar(): Boolean = true
            override suspend fun desconectar() {}
            override suspend fun enviar(paquete: PaqueteMesh): Boolean {
                onEnviar(paquete)
                return true
            }
            override fun observarPaquetes(): Flow<PaqueteMesh> = emptyFlow()
            override fun estaConectado(): Boolean = true
        }
    }

    @Test
    fun `retransmision - paquete con ttl mayor a 1 se retransmite decrementando ttl`() = runTest {
        val router = crearRouter()
        val paquetesEnviadosAPeer = mutableListOf<PaqueteMesh>()

        val peerFake = crearPeerFake { paquetesEnviadosAPeer.add(it) }

        router.registrarPeer(peerFake)
        val paquete = crearPaquete(ttl = 3, destinatarioId = "tercero-789")

        router.enrutar(paquete, origenMac = "origen-mac-0")

        assertEquals("Debe retransmitirse al peer vecino", 1, paquetesEnviadosAPeer.size)
        val retransmitido = paquetesEnviadosAPeer[0]
        assertEquals("TTL debe decrementar en 1", 2, retransmitido.ttl)
        assertEquals("Saltos debe incrementar en 1", 1, retransmitido.saltosRealizados)
    }

    @Test
    fun `retransmision - paquete con ttl igual a 1 no se retransmite`() = runTest {
        val router = crearRouter()
        val paquetesEnviadosAPeer = mutableListOf<PaqueteMesh>()

        val peerFake = crearPeerFake { paquetesEnviadosAPeer.add(it) }

        router.registrarPeer(peerFake)
        val paqueteAgotado = crearPaquete(ttl = 1, destinatarioId = "tercero-789")

        router.enrutar(paqueteAgotado, origenMac = "origen-mac-0")

        assertTrue("Paquete con TTL agotado no debe retransmitirse", paquetesEnviadosAPeer.isEmpty())
    }

    private fun crearRouterConCripto(
        miId: String? = miUsuarioId,
        gestorIdentidad: GestorIdentidadEcdsa,
        pinesIdentidad: PinesIdentidad,
        resolverClaveRemota: ((String) -> String?)? = null
    ): MessageRouterMesh {
        return MessageRouterMesh(
            usuarioIdProveedor = { miId },
            gestorIdentidad = gestorIdentidad,
            pinesIdentidad = pinesIdentidad,
            resolverClaveRemota = resolverClaveRemota
        )
    }

    @Test
    fun `seguridad - paquete ajeno sin firma valida es descartado`() = runTest {
        val gestorPropio = GestorIdentidadEcdsa(prefsOverride = FakeSharedPreferences())
        val pines = PinesIdentidad(prefsOverride = FakeSharedPreferences())
        val router = crearRouterConCripto(
            gestorIdentidad = gestorPropio,
            pinesIdentidad = pines
        )

        val entregados = mutableListOf<PaqueteMesh>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            router.todosLosPaquetesEntrantes().collect { entregados.add(it) }
        }

        // Paquete ajeno con firma basura
        val paqueteFalso = crearPaquete(
            remitenteId = "atacante-1",
            firma = "firma_invalida_totalmente",
            clavePublicaRemitente = "clave_invalida"
        )

        router.enrutar(paqueteFalso, origenMac = "AA:BB:CC:01")

        assertTrue("Paquete ajeno con firma inválida debe ser descartado", entregados.isEmpty())
        job.cancel()
    }

    @Test
    fun `H-06 TOFU - paquete con clave discordante es descartado sin degradar ni envenenar pin existente`() = runTest {
        val gestorPropio = GestorIdentidadEcdsa(prefsOverride = FakeSharedPreferences())
        val gestorAmigo = GestorIdentidadEcdsa(prefsOverride = FakeSharedPreferences())
        val gestorAtacante = GestorIdentidadEcdsa(prefsOverride = FakeSharedPreferences())

        val claveAmigo = gestorAmigo.obtenerClavePublicaBase64()!!
        val claveAtacante = gestorAtacante.obtenerClavePublicaBase64()!!

        val pines = PinesIdentidad(prefsOverride = FakeSharedPreferences())
        val amigoId = "amigo-456"

        // El amigo ya tiene un pin VERIFICADO
        pines.fijar(amigoId, claveAmigo, EstadoVerificacionIdentidad.VERIFICADO)

        val router = crearRouterConCripto(
            gestorIdentidad = gestorPropio,
            pinesIdentidad = pines,
            resolverClaveRemota = { id -> if (id == amigoId) claveAmigo else null }
        )

        val entregados = mutableListOf<PaqueteMesh>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            router.todosLosPaquetesEntrantes().collect { entregados.add(it) }
        }

        // El atacante crea un paquete suplantando a amigo-456 con su propia clave privada y pública
        val paqueteBase = crearPaquete(remitenteId = amigoId)
        val firmaAtacante = gestorAtacante.firmar(paqueteBase.datosFirmados())!!
        val paqueteAtacante = paqueteBase.copy(
            firma = firmaAtacante,
            clavePublicaRemitente = claveAtacante
        )

        router.enrutar(paqueteAtacante, origenMac = "AA:BB:CC:01")

        assertTrue("El paquete con clave discordante debe ser descartado", entregados.isEmpty())
        assertEquals("El pin del amigo no debe ser envenenado por el atacante", claveAmigo, pines.obtener(amigoId))
        assertEquals(
            "El estado de verificación debe mantenerse VERIFICADO sin degradación",
            EstadoVerificacionIdentidad.VERIFICADO,
            pines.obtenerEstado(amigoId)
        )

        // Ahora el amigo legítimo envía un paquete
        val paqueteLegitimoBase = crearPaquete(remitenteId = amigoId)
        val firmaLegitima = gestorAmigo.firmar(paqueteLegitimoBase.datosFirmados())!!
        val paqueteLegitimo = paqueteLegitimoBase.copy(
            firma = firmaLegitima,
            clavePublicaRemitente = claveAmigo
        )

        router.enrutar(paqueteLegitimo, origenMac = "AA:BB:CC:02")

        assertEquals("El paquete legítimo debe ser entregado", 1, entregados.size)
        assertEquals(paqueteLegitimo.id, entregados[0].id)

        job.cancel()
    }

    @Test
    fun `S-02 TOFU - paquete con clave discordante en identidad NO_VERIFICADO es rechazado y no sobreescribe el pin`() = runTest {
        val gestorPropio = GestorIdentidadEcdsa(prefsOverride = FakeSharedPreferences())
        val gestorAmigo = GestorIdentidadEcdsa(prefsOverride = FakeSharedPreferences())
        val gestorAtacante = GestorIdentidadEcdsa(prefsOverride = FakeSharedPreferences())

        val claveAmigo = gestorAmigo.obtenerClavePublicaBase64()!!
        val claveAtacante = gestorAtacante.obtenerClavePublicaBase64()!!

        val pines = PinesIdentidad(prefsOverride = FakeSharedPreferences())
        val amigoId = "amigo-local-999"

        // Amigo local en modo descentralizado (NO_VERIFICADO)
        pines.fijar(amigoId, claveAmigo, EstadoVerificacionIdentidad.NO_VERIFICADO)

        val router = crearRouterConCripto(
            gestorIdentidad = gestorPropio,
            pinesIdentidad = pines,
            resolverClaveRemota = null
        )

        val entregados = mutableListOf<PaqueteMesh>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            router.todosLosPaquetesEntrantes().collect { entregados.add(it) }
        }

        // Atacante intenta suplantar al amigo local usando una clave diferente
        val paqueteBase = crearPaquete(remitenteId = amigoId)
        val firmaAtacante = gestorAtacante.firmar(paqueteBase.datosFirmados())!!
        val paqueteAtacante = paqueteBase.copy(
            firma = firmaAtacante,
            clavePublicaRemitente = claveAtacante
        )

        router.enrutar(paqueteAtacante, origenMac = "AA:BB:CC:99")

        assertTrue("El paquete atacante con clave cambiada debe ser descartado", entregados.isEmpty())
        assertEquals("El pin original del amigo NO debe ser sobreescrito", claveAmigo, pines.obtener(amigoId))
        assertEquals(
            "El estado de la identidad debe marcarse como ALERTA_CONFLICTO",
            EstadoVerificacionIdentidad.ALERTA_CONFLICTO,
            pines.obtenerEstado(amigoId)
        )
        assertTrue("El router debe registrar la alerta TOFU activa", router.tieneAlertaTofu(amigoId))

        job.cancel()
    }

    @Test
    fun `H-06 TOFU - primer contacto en conflicto directo con resolutor remoto fija clave oficial remota y rechaza atacante`() = runTest {
        val gestorPropio = GestorIdentidadEcdsa(prefsOverride = FakeSharedPreferences())
        val gestorAmigo = GestorIdentidadEcdsa(prefsOverride = FakeSharedPreferences())
        val gestorAtacante = GestorIdentidadEcdsa(prefsOverride = FakeSharedPreferences())

        val claveAmigo = gestorAmigo.obtenerClavePublicaBase64()!!
        val claveAtacante = gestorAtacante.obtenerClavePublicaBase64()!!

        val pines = PinesIdentidad(prefsOverride = FakeSharedPreferences())
        val nuevoContactoId = "nuevo-contacto-777"

        // El resolutor conoce la clave oficial del nuevo contacto
        val router = crearRouterConCripto(
            gestorIdentidad = gestorPropio,
            pinesIdentidad = pines,
            resolverClaveRemota = { id -> if (id == nuevoContactoId) claveAmigo else null }
        )

        val entregados = mutableListOf<PaqueteMesh>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            router.todosLosPaquetesEntrantes().collect { entregados.add(it) }
        }

        // Atacante intenta primer contacto malicioso
        val paqueteBase = crearPaquete(remitenteId = nuevoContactoId)
        val firmaAtacante = gestorAtacante.firmar(paqueteBase.datosFirmados())!!
        val paqueteAtacante = paqueteBase.copy(
            firma = firmaAtacante,
            clavePublicaRemitente = claveAtacante
        )

        router.enrutar(paqueteAtacante, origenMac = "AA:BB:CC:01")

        assertTrue("Paquete de primer contacto ilegítimo debe ser descartado", entregados.isEmpty())
        assertEquals(
            "Se debe haber fijado la clave remota oficial, NO la del atacante",
            claveAmigo,
            pines.obtener(nuevoContactoId)
        )

        job.cancel()
    }

    @Test
    fun `enviarADestino respeta feature flag y entrega unicamente al peer registrado`() = runTest {
        val router = crearRouter()
        val paquetesDestino = mutableListOf<PaqueteMesh>()
        val paquetesOtro = mutableListOf<PaqueteMesh>()
        val peerDestino = crearPeerFake("peer-destino-mac") { paquetesDestino.add(it) }
        val peerOtro = crearPeerFake("peer-otro-mac") { paquetesOtro.add(it) }
        router.registrarPeer(peerDestino)
        router.registrarPeer(peerOtro)

        val paquete = crearPaquete(destinatarioId = "peer-destino-mac", ttl = 1)

        // 1. Con flag inactivo: debe retornar false (no envía dirigido)
        com.renea.arete_social.domain.model.FeatureFlags.SYNC_ENVIO_DIRIGIDO = false
        val enviadoSinFlag = router.enviarADestino("peer-destino-mac", paquete)
        assertFalse(enviadoSinFlag)
        assertTrue(paquetesDestino.isEmpty())

        // 2. Con flag activo: entrega unicamente a peerDestino
        com.renea.arete_social.domain.model.FeatureFlags.SYNC_ENVIO_DIRIGIDO = true
        try {
            val enviadoConFlag = router.enviarADestino("peer-destino-mac", paquete)
            assertTrue(enviadoConFlag)
            assertEquals(1, paquetesDestino.size)
            assertEquals(paquete.id, paquetesDestino.first().id)
            assertTrue(paquetesOtro.isEmpty())
        } finally {
            com.renea.arete_social.domain.model.FeatureFlags.SYNC_ENVIO_DIRIGIDO = false
        }
    }
}
