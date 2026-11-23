package com.renea.arete_social.domain.mesh

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Enrutador de mensajes de la red mallada (Mesh).
 */
interface MessageRouter {
    suspend fun enrutar(paquete: PaqueteMesh, origenMac: String? = null)
    suspend fun reenviar(paquete: PaqueteMesh)

    /** Paquetes recibidos cuyo chatId coincide exactamente con [chatId]. */
    fun paquetesEntrantes(chatId: String): Flow<PaqueteMesh>

    /** Todos los paquetes recibidos por este nodo. */
    fun todosLosPaquetesEntrantes(): Flow<PaqueteMesh>

    /**
     * Paquetes recibidos cuyo destinatarioId es [miId] y cuyo chatId es de
     * formato local ("local-dm-…"). Usado para entregar mensajes directos entrantes.
     */
    fun paquetesParaMi(miId: String): Flow<PaqueteMesh>


    /** Número de peers BLE con enlace listo. Sirve para republicar pendientes al conectar. */
    val peersConectados: StateFlow<Int>

    /**
     * §2.1 — Emite el `remitenteId` de un peer cuya clave pública TOFU no coincide con el
     * pin guardado previamente. La UI debe mostrar un banner de
     * advertencia de seguridad cuando recibe este evento.
     */
    val alertaClaveTofu: SharedFlow<String>
        get() = kotlinx.coroutines.flow.MutableSharedFlow()

    /**
     * Consulta si un remitente tiene una alerta TOFU no resuelta actualmente.
     */
    fun tieneAlertaTofu(remitenteId: String): Boolean = false

    /**
     * Permite al usuario confiar explícitamente en una nueva identidad presentada por [remitenteId]
     * tras una alerta TOFU (por ejemplo, si el contacto reinstaló la app o cambió de dispositivo).
     */
    fun confiarEnNuevaIdentidad(remitenteId: String) {}

    /** Olvida estado de sesión (dedup y paquetes pendientes). Se llama al cerrar sesión. */
    fun reiniciar() {}

    fun registrarPeer(peer: PeerConnection)
    fun removerPeer(dispositivoId: String)

    /** Firma criptográficamente un paquete propio usando la identidad del dispositivo actual. */
    fun firmarPaquete(paquete: PaqueteMesh): PaqueteMesh = paquete

    /** H-06: Devuelve el usuarioId autenticado criptográficamente asociado a una MAC Bluetooth. */
    fun usuarioAutenticadoDe(mac: String): String? = null

    /**
     * Entrega un paquete únicamente al peer conectado asociado al [destinatarioId] sin retransmitirlo
     * por broadcast a los demás nodos. Usado por la sincronización P2P para evitar saturar el canal.
     * Retorna true si se encontró la conexión directa y se entregó exitosamente.
     */
    suspend fun enviarADestino(destinatarioId: String, paquete: PaqueteMesh): Boolean = false
}
