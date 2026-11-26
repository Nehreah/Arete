package com.renea.arete_social.data.mesh

import com.renea.arete_social.data.crypto.PinesIdentidad
import com.renea.arete_social.domain.mesh.PaqueteMesh
import com.renea.arete_social.domain.mesh.PeerConnection
import com.renea.arete_social.domain.model.Dispositivo
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class MessageRouterEcoTest {

    private class FakePeer(
        override val dispositivo: Dispositivo
    ) : PeerConnection {
        val paquetesEnviados = CopyOnWriteArrayList<PaqueteMesh>()

        override suspend fun conectar(): Boolean = true
        override suspend fun desconectar() {}
        override suspend fun enviar(paquete: PaqueteMesh): Boolean {
            paquetesEnviados.add(paquete)
            return true
        }
        override fun observarPaquetes() = emptyFlow<PaqueteMesh>()
        override fun estaConectado(): Boolean = true
    }

    @Test
    fun reenvioConExclusion_noEnviaAlPeerEmisorPorMacOClave() = runBlocking {
        val pinesMock = mockk<PinesIdentidad>(relaxed = true)
        val router = MessageRouterMesh(
            usuarioIdProveedor = { "mi-usuario-local" },
            pinesIdentidad = pinesMock
        )

        val peerEmisor = FakePeer(
            Dispositivo(
                id = "srv-AA:BB:CC:DD:EE:01",
                nombre = "Peer Emisor",
                señal = -50,
                ultimaConexion = System.currentTimeMillis(),
                usuarioId = "usuario-emisor",
                distanciaMetros = 10.0,
                macAddress = "AA:BB:CC:DD:EE:01"
            )
        )

        val peerVecino = FakePeer(
            Dispositivo(
                id = "srv-AA:BB:CC:DD:EE:02",
                nombre = "Peer Vecino",
                señal = -60,
                ultimaConexion = System.currentTimeMillis(),
                usuarioId = "usuario-vecino",
                distanciaMetros = 20.0,
                macAddress = "AA:BB:CC:DD:EE:02"
            )
        )

        router.registrarPeer(peerEmisor)
        router.registrarPeer(peerVecino)

        val paquete = PaqueteMesh(
            id = "pkg-001",
            chatId = "radar_general",
            remitenteId = "otro-usuario",
            contenido = "Hola mesh",
            ttl = 3
        )

        // Reenviar excluyendo la dirección física del peer emisor
        router.reenviarConExclusion(paquete, origenPeerId = "AA:BB:CC:DD:EE:01")

        // El emisor no debe haber recibido eco
        assertTrue(peerEmisor.paquetesEnviados.isEmpty())
        // El vecino sí debe recibir la retransmisión
        assertEquals(1, peerVecino.paquetesEnviados.size)
        assertEquals("pkg-001", peerVecino.paquetesEnviados.first().id)
    }

    @Test
    fun supresionAntiMula_ignoraPaquetesRepetidosEnVentanaDe10Segundos() = runBlocking {
        val pinesMock = mockk<PinesIdentidad>(relaxed = true)
        val router = MessageRouterMesh(
            usuarioIdProveedor = { "mi-usuario-local" },
            pinesIdentidad = pinesMock
        )

        val peer = FakePeer(
            Dispositivo(
                id = "peer-dest",
                nombre = "Peer Destino",
                señal = -40,
                ultimaConexion = System.currentTimeMillis(),
                usuarioId = "usuario-dest",
                distanciaMetros = 5.0
            )
        )
        router.registrarPeer(peer)

        val paquete = PaqueteMesh(
            id = "pkg-mula-01",
            chatId = "radar_general",
            remitenteId = "autor-remoto",
            contenido = "Mensaje Mula",
            ttl = 3
        )

        // Primer enrutamiento: se procesa y reenvía
        router.enrutar(paquete, origenMac = "AA:BB:CC:DD:EE:FF")
        val enviosIniciales = peer.paquetesEnviados.size

        // Segundo enrutamiento del mismo paquete en la misma ventana de 10s
        router.enrutar(paquete, origenMac = "AA:BB:CC:DD:EE:FF")

        // No debe haber aumentado los envíos duplicados
        assertEquals(enviosIniciales, peer.paquetesEnviados.size)
    }
}
