package com.renea.arete_social.feature.miausnake.integration.mesh

import com.renea.arete_social.domain.mesh.MessageRouter
import com.renea.arete_social.domain.mesh.PaqueteMesh
import com.renea.arete_social.domain.model.ProtocoloCanalesMesh
import com.renea.arete_social.domain.proximity.DetectorProximidad
import com.renea.arete_social.feature.miausnake.match.EnvelopeDecode
import com.renea.arete_social.feature.miausnake.match.InboundMatch
import com.renea.arete_social.feature.miausnake.match.MatchCodec
import com.renea.arete_social.feature.miausnake.match.MatchEnvelope
import com.renea.arete_social.feature.miausnake.match.MatchTransport
import com.renea.arete_social.feature.miausnake.match.NearbyPeer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

/**
 * Adaptador de [MatchTransport] sobre el [MessageRouter] de Aréte (BLE GATT y Wi‑Fi LAN). Miau Snake NO
 * abre su propia pila de red ni su propia identidad: usa los paquetes firmados del router.
 *
 *  - Canal propio ([ProtocoloCanalesMesh.CANAL_MIAUSNAKE_MATCH]) que la mensajería ignora, como los pases.
 *  - Paquete dirigido (`destinatarioId`) con TTL 1: nunca se retransmite; solo llega a quien tiene enlace directo.
 *  - Se firma UNA vez con `firmarPaquete` y se usa el mismo orden de envío que `GestorPasesBle`
 *    (`reenviar` difunde, `enrutar` lo marca como visto). El router rechaza paquetes ajenos sin firma válida,
 *    así que `remitenteId` llega autenticado.
 *  - Cada envío usa un `id` de paquete NUEVO: el router descarta ids repetidos, y las balizas del protocolo
 *    necesitan que la repetición llegue.
 */
class AreteMatchTransport(
    private val router: MessageRouter,
    private val miIdProveedor: () -> String?
) : MatchTransport {

    override val incoming: Flow<InboundMatch> = router.todosLosPaquetesEntrantes()
        .filter { it.chatId == ProtocoloCanalesMesh.CANAL_MIAUSNAKE_MATCH }
        .mapNotNull { toInbound(it) }

    override val linkedPeers: StateFlow<Int> get() = router.peersConectados

    override suspend fun send(peerId: String, envelope: MatchEnvelope): Boolean {
        val miId = miIdProveedor() ?: return false
        if (router.peersConectados.value <= 0) return false // sin enlace no se encola: una invitación vieja no sirve
        val text = runCatching { MatchCodec.encode(envelope) }.getOrNull() ?: return false
        val crudo = PaqueteMesh(
            id = envelope.messageId,
            chatId = ProtocoloCanalesMesh.CANAL_MIAUSNAKE_MATCH,
            remitenteId = miId,
            destinatarioId = peerId,
            contenido = text,
            ttl = 1,
            timestamp = System.currentTimeMillis()
        )
        return runCatching {
            val firmado = router.firmarPaquete(crudo)
            // Primero el envío dirigido (solo al par); si el router no lo ubica, difusión con TTL 1.
            if (!router.enviarADestino(peerId, firmado)) router.reenviar(firmado)
            router.enrutar(firmado)
            true
        }.getOrDefault(false)
    }

    private fun toInbound(p: PaqueteMesh): InboundMatch? {
        val miId = miIdProveedor() ?: return null
        if (mismoId(p.remitenteId, miId) || !mismoId(p.destinatarioId, miId)) return null
        val env = (MatchCodec.decode(p.contenido) as? EnvelopeDecode.Ok)?.envelope ?: return null
        return InboundMatch(env, p.remitenteId, signed = p.firma != null && p.clavePublicaRemitente != null)
    }

    private fun mismoId(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        fun n(s: String) = s.trim().lowercase().replace("-", "")
        return n(a) == n(b)
    }
}

/**
 * Teléfonos cercanos para invitar. Solo los hallados por radio (BLE o Wi‑Fi local): el detector GPS rellena
 * `ubicacion` y esos NO tienen enlace directo, así que no sirven para un duelo. Se suscribe solo mientras
 * la pantalla de cercanía está abierta (nada de escaneo continuo en segundo plano).
 */
class AreteNearby(
    private val detector: DetectorProximidad,
    private val miIdProveedor: () -> String?
) {
    fun peers(): Flow<List<NearbyPeer>> = detector.descubrir().map { lista ->
        val yo = miIdProveedor()
        lista.asSequence()
            .filter { it.ubicacion == null }
            .mapNotNull { d -> d.usuarioId?.takeIf { it.isNotBlank() && it != yo }?.let { NearbyPeer(it, d.nombre) } }
            .distinctBy { it.userId }
            .toList()
    }
}
