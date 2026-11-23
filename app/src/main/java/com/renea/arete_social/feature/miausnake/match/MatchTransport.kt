package com.renea.arete_social.feature.miausnake.match

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/** Un teléfono cercano al que se puede invitar. [userId] es el id de usuario de Aréte. */
data class NearbyPeer(val userId: String, val displayName: String)

/**
 * Un sobre recibido. [authenticatedPeerId] lo pone la malla (paquete firmado y verificado con la
 * identidad de Aréte), NO el contenido del sobre. [signed] es false si el paquete no traía firma.
 */
data class InboundMatch(val envelope: MatchEnvelope, val authenticatedPeerId: String, val signed: Boolean)

/**
 * Canal del juego sobre la malla de Aréte (plan V9, 00.5). El dominio del juego solo conoce esta
 * interfaz: la implementación real es un adaptador de MessageRouterMesh (BLE GATT / Wi‑Fi LAN) y se
 * puede cambiar sin tocar el motor.
 */
interface MatchTransport {
    /** Sobres ya decodificados y dirigidos a este teléfono. Lo ilegible se descarta antes de llegar aquí. */
    val incoming: Flow<InboundMatch>

    /** Teléfonos con enlace directo ahora mismo (BLE o Wi‑Fi). 0 = no hay a quién enviar. */
    val linkedPeers: StateFlow<Int>

    /** true si se entregó a un enlace directo. No es acuse de aplicación: eso lo da el propio protocolo. */
    suspend fun send(peerId: String, envelope: MatchEnvelope): Boolean
}

/** Sin malla: la función queda inerte (los retos siguen compartiéndose por texto, archivo o QR). */
object NoMatchTransport : MatchTransport {
    override val incoming: Flow<InboundMatch> = emptyFlow()
    override val linkedPeers: StateFlow<Int> = MutableStateFlow(0)
    override suspend fun send(peerId: String, envelope: MatchEnvelope): Boolean = false
}
