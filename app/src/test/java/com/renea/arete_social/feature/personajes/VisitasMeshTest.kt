package com.renea.arete_social.feature.personajes

import com.renea.arete_social.data.crypto.GestorIdentidadEcdsa
import com.renea.arete_social.data.crypto.PinesIdentidad
import com.renea.arete_social.data.mesh.MessageRouterMesh
import com.renea.arete_social.domain.mesh.PaqueteMesh
import com.renea.arete_social.feature.personajes.data.RepositorioCasaLocal
import com.renea.arete_social.feature.personajes.data.RepositorioPersonajesLocal
import com.renea.arete_social.feature.personajes.logic.GestorVisitasMesh
import com.renea.arete_social.feature.personajes.logic.ResultadoVisitaSolicitud
import com.renea.arete_social.feature.personajes.model.Temperamento
import com.renea.arete_social.feature.personajes.model.TipoRecuerdo
import com.renea.arete_social.feature.personajes.model.escena.AccionObjeto
import com.renea.arete_social.feature.personajes.model.escena.CasaSnapshot
import com.renea.arete_social.feature.personajes.model.escena.ObjetoColocado
import com.renea.arete_social.feature.personajes.model.visita.ProtocoloVisita
import com.renea.arete_social.feature.personajes.model.visita.ReciboVisita
import com.renea.arete_social.feature.personajes.model.visita.VisitaResultado
import com.renea.arete_social.feature.personajes.model.visita.VisitaSolicitud
import com.renea.arete_social.feature.tamagotchi.model.PetEntity
import com.renea.arete_social.testutils.FakeSharedPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Pruebas unitarias para la Fase 3: Visitas asíncronas entre amigos sobre la red mesh.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VisitasMeshTest {

    // ── 1. Protocolo y Framing de Red ────────────────────────────────────────

    @Test
    fun `protocolo de visita - serializa y deserializa solicitudes fielmente`() {
        val solicitud = VisitaSolicitud(
            visitaId = "visita-123",
            remitenteOwnerId = "user-a",
            remitentePersonajeId = "pet-a",
            remitenteNombre = "MichiA",
            timestamp = 1700000000000L,
            nonce = "nonce-abc",
            nota = "¡Hola vecino!"
        )

        val enmarcado = ProtocoloVisita.codificarSolicitud(solicitud)
        assertTrue(enmarcado.startsWith("[VISITA_REQ:v1:"))
        assertTrue(enmarcado.endsWith("]"))

        val decodificado = ProtocoloVisita.parsearSolicitud(enmarcado)
        assertNotNull(decodificado)
        assertEquals(solicitud, decodificado)
    }

    @Test
    fun `protocolo de visita - serializa y deserializa resultados y recibos`() {
        val casa = CasaSnapshot(
            propietarioId = "user-b",
            personajeId = "pet-b",
            version = 1,
            objetos = listOf(ObjetoColocado("inst-1", "cama_futurista", 1, 1))
        )

        val resultado = VisitaResultado(
            visitaId = "visita-456",
            anfitrionOwnerId = "user-b",
            anfitrionPersonajeId = "pet-b",
            anfitrionNombre = "MichiB",
            casaSnapshot = casa,
            timestamp = 1700000000000L,
            firmaAnfitrion = "firma_b"
        )

        val encRes = ProtocoloVisita.codificarResultado(resultado)
        val decRes = ProtocoloVisita.parsearResultado(encRes)
        assertEquals(resultado, decRes)

        val recibo = ReciboVisita(
            visitaId = "visita-456",
            visitanteOwnerId = "user-a",
            anfitrionOwnerId = "user-b",
            accionRealizada = AccionObjeto.JUGAR,
            objetoCatalogoId = "cama_futurista",
            notaVisitante = "Gran cama",
            timestamp = 1700000005000L,
            firmaVisitante = "firma_visitante_a"
        )

        val encRecibo = ProtocoloVisita.codificarRecibo(recibo)
        val decRecibo = ProtocoloVisita.parsearRecibo(encRecibo)
        assertEquals(recibo, decRecibo)
    }

    // ── 2. Seguridad y Privacidad ────────────────────────────────────────────

    @Test
    fun `anfitrion en modo invisible no responde a solicitudes de visita`() = runTest {
        val paquetesEnviadosPorAnfitrion = mutableListOf<PaqueteMesh>()
        val routerAnfitrion = MessageRouterMesh(
            usuarioIdProveedor = { "user-anfitrion" },
            pinesIdentidad = PinesIdentidad(prefsOverride = FakeSharedPreferences())
        )

        val repoCasa = RepositorioCasaLocal(prefsOverride = FakeSharedPreferences())
        val repoPersonajes = RepositorioPersonajesLocal(prefsOverride = FakeSharedPreferences())

        val gestorAnfitrion = GestorVisitasMesh(
            router = routerAnfitrion,
            repositorioCasa = repoCasa,
            repositorioPersonajes = repoPersonajes,
            gestorIdentidad = null,
            miIdProveedor = { "user-anfitrion" },
            miPetProveedor = { PetEntity("user-anfitrion", "MichiB") },
            esAmigo = { true }, // Aunque sea amigo...
            esInvisible = { true } // ¡Está en modo Invisible!
        )
        gestorAnfitrion.iniciar()

        // Llega solicitud de un amigo
        val paqueteSolicitud = PaqueteMesh(
            id = "req-1",
            chatId = ProtocoloVisita.CHAT_ID,
            remitenteId = "user-amigo",
            destinatarioId = "user-anfitrion",
            contenido = ProtocoloVisita.codificarSolicitud(
                VisitaSolicitud("v-1", "user-amigo", "pet-a", "MichiA", 1000L, "nonce-1")
            )
        )

        routerAnfitrion.enrutar(paqueteSolicitud)

        // El anfitrión no debió emitir ningún paquete de respuesta
        assertEquals(
            "En modo Invisible no debe emitir ninguna respuesta por la red",
            0,
            paquetesEnviadosPorAnfitrion.size
        )
    }

    @Test
    fun `anfitrion descarta solicitud si el remitente no es amigo aceptado`() = runTest {
        val paquetesEnviadosPorAnfitrion = mutableListOf<PaqueteMesh>()
        val routerAnfitrion = MessageRouterMesh(
            usuarioIdProveedor = { "user-anfitrion" },
            pinesIdentidad = PinesIdentidad(prefsOverride = FakeSharedPreferences())
        )

        val repoCasa = RepositorioCasaLocal(prefsOverride = FakeSharedPreferences())
        val repoPersonajes = RepositorioPersonajesLocal(prefsOverride = FakeSharedPreferences())

        val gestorAnfitrion = GestorVisitasMesh(
            router = routerAnfitrion,
            repositorioCasa = repoCasa,
            repositorioPersonajes = repoPersonajes,
            gestorIdentidad = null,
            miIdProveedor = { "user-anfitrion" },
            miPetProveedor = { PetEntity("user-anfitrion", "MichiB") },
            esAmigo = { false }, // Desconocido o no amigo
            esInvisible = { false }
        )
        gestorAnfitrion.iniciar()

        val paqueteSolicitud = PaqueteMesh(
            id = "req-2",
            chatId = ProtocoloVisita.CHAT_ID,
            remitenteId = "desconocido-99",
            destinatarioId = "user-anfitrion",
            contenido = ProtocoloVisita.codificarSolicitud(
                VisitaSolicitud("v-2", "desconocido-99", "pet-x", "MichiX", 1000L, "nonce-2")
            )
        )

        routerAnfitrion.enrutar(paqueteSolicitud)

        assertEquals("No se responde a solicitudes de usuarios que no son amigos", 0, paquetesEnviadosPorAnfitrion.size)
    }

    // ── 3. Flujo Completo con Recibo Mutuo ───────────────────────────────────

    @Test
    fun `flujo completo de visita genera recibo firmado, recuerdo y actualizacion de relacion`() = runTest {
        val gestorCryptoA = GestorIdentidadEcdsa(prefsOverride = FakeSharedPreferences())
        val gestorCryptoB = GestorIdentidadEcdsa(prefsOverride = FakeSharedPreferences())

        val repoCasaB = RepositorioCasaLocal(prefsOverride = FakeSharedPreferences(), gestorIdentidad = gestorCryptoB)
        val repoPersonajesA = RepositorioPersonajesLocal(prefsOverride = FakeSharedPreferences())
        val repoPersonajesB = RepositorioPersonajesLocal(prefsOverride = FakeSharedPreferences())

        val petA = PetEntity("user-a", "MichiA", temperamento = Temperamento.SOCIABLE)
        val petB = PetEntity("user-b", "MichiB", temperamento = Temperamento.TRANQUILO)

        val routerA = MessageRouterMesh(
            usuarioIdProveedor = { "user-a" },
            pinesIdentidad = PinesIdentidad(prefsOverride = FakeSharedPreferences())
        )
        val routerB = MessageRouterMesh(
            usuarioIdProveedor = { "user-b" },
            pinesIdentidad = PinesIdentidad(prefsOverride = FakeSharedPreferences())
        )

        val gestorVisitasA = GestorVisitasMesh(
            router = routerA,
            repositorioCasa = RepositorioCasaLocal(prefsOverride = FakeSharedPreferences()),
            repositorioPersonajes = repoPersonajesA,
            gestorIdentidad = gestorCryptoA,
            miIdProveedor = { "user-a" },
            miPetProveedor = { petA },
            esAmigo = { true },
            scope = this
        )

        val gestorVisitasB = GestorVisitasMesh(
            router = routerB,
            repositorioCasa = repoCasaB,
            repositorioPersonajes = repoPersonajesB,
            gestorIdentidad = gestorCryptoB,
            miIdProveedor = { "user-b" },
            miPetProveedor = { petB },
            esAmigo = { true },
            scope = this
        )

        gestorVisitasA.iniciar()
        gestorVisitasB.iniciar()
        testScheduler.runCurrent()

        // 1. Simulación de respuesta de casa del anfitrión
        val casaB = repoCasaB.obtenerCasa("user-b", petB.personajeId)
        val visitaResultado = VisitaResultado(
            visitaId = "visita-100",
            anfitrionOwnerId = "user-b",
            anfitrionPersonajeId = petB.personajeId,
            anfitrionNombre = petB.name,
            casaSnapshot = casaB,
            timestamp = System.currentTimeMillis()
        )

        // 2. Visitante completa la visita interactuando con un mueble
        val reciboEmitido = gestorVisitasA.completarVisita(
            resultado = visitaResultado,
            accion = AccionObjeto.JUGAR,
            objetoCatalogoId = "rascador_poste",
            nota = "¡Me encantó tu casa MichiB!"
        )

        assertNotNull(reciboEmitido)
        assertNotNull("El recibo debe estar firmado por el visitante", reciboEmitido?.firmaVisitante)

        // Verificar que el visitante guardó su recuerdo y relación
        val recuerdosA = repoPersonajesA.obtenerRecuerdos()
        assertEquals(1, recuerdosA.size)
        assertEquals(TipoRecuerdo.VISITA_CASA, recuerdosA[0].tipo)
        assertEquals("user-b", recuerdosA[0].conQuienId)

        val relacionA = repoPersonajesA.obtenerRelacion("user-b")
        assertNotNull(relacionA)
        assertTrue("La confianza debe haber aumentado", (relacionA?.confianza ?: 0) > 0)
        assertEquals(1, relacionA?.interaccionesTotales)

        // 3. El anfitrión recibe el recibo del visitante
        val paqueteRecibo = PaqueteMesh(
            id = "recibo-pack-1",
            chatId = ProtocoloVisita.CHAT_ID,
            remitenteId = "user-a",
            destinatarioId = "user-b",
            contenido = ProtocoloVisita.codificarRecibo(reciboEmitido!!)
        )

        routerB.enrutar(paqueteRecibo)
        testScheduler.advanceUntilIdle()

        // Verificar que el anfitrión también registró el recuerdo y la relación
        val recuerdosB = repoPersonajesB.obtenerRecuerdos()
        assertEquals(1, recuerdosB.size)
        assertEquals("user-a", recuerdosB[0].conQuienId)

        val relacionB = repoPersonajesB.obtenerRelacion("user-a")
        assertNotNull(relacionB)
        assertTrue((relacionB?.confianza ?: 0) > 0)

        gestorVisitasA.detener()
        gestorVisitasB.detener()
    }
}
