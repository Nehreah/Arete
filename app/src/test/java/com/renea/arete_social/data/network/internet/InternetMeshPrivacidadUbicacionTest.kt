package com.renea.arete_social.data.network.internet

import android.content.Context
import android.content.ContextWrapper
import com.renea.arete_social.domain.mesh.MessageRouter
import com.renea.arete_social.domain.mesh.PaqueteMesh
import com.renea.arete_social.domain.mesh.PeerConnection
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Test

class InternetMeshPrivacidadUbicacionTest {

    private val dummyContext: Context = ContextWrapper(null)

    @Test
    fun paqueteConUbicacion_esBloqueadoHaciaInternet() = runBlocking {
        val routerMock = mockk<MessageRouter>(relaxed = true)

        val bridge = InternetMeshBridge(
            context = dummyContext,
            peerIdProvider = { "mi-nodo" },
            router = routerMock,
            esAmigo = { true }
        )

        // Usamos reflection para acceder a la conexión virtual interna
        val relayField = InternetMeshBridge::class.java.getDeclaredField("relayPeerConnection").apply {
            isAccessible = true
        }
        val relayPeer = relayField.get(bridge) as PeerConnection

        // Forzar estado conectado para evaluar el filtro
        val conectadoField = InternetMeshBridge::class.java.getDeclaredField("_conectadoAInternet").apply {
            isAccessible = true
        }
        val conectadoFlow = conectadoField.get(bridge) as MutableStateFlow<Boolean>
        conectadoFlow.value = true

        val paqueteGps = PaqueteMesh(
            id = "pkg-gps",
            chatId = "dm-amigo",
            remitenteId = "mi-nodo",
            destinatarioId = "amigo-lejano",
            contenido = "GPS:latitud:3.4516,longitud:-76.5320"
        )
        val enviadoGps = relayPeer.enviar(paqueteGps)
        assertFalse("Los paquetes con coordenadas GPS deben ser bloqueados tajantemente hacia Internet", enviadoGps)

        val paqueteRadar = PaqueteMesh(
            id = "pkg-radar",
            chatId = "radar_general",
            remitenteId = "mi-nodo",
            contenido = "Presencia en radar"
        )
        val enviadoRadar = relayPeer.enviar(paqueteRadar)
        assertFalse("El canal de radar hiperlocal jamás debe salir a Internet", enviadoRadar)

        bridge.close()
    }
}
