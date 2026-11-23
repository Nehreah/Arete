package com.renea.arete_social.domain.mesh

import com.renea.arete_social.domain.model.Dispositivo
import kotlinx.coroutines.flow.Flow

/**
 * Representa un enlace de comunicación directo (Punto a Punto) con un dispositivo vecino.
 * Modela el transporte de bajo nivel sobre Bluetooth Low Energy, Wi-Fi Direct o Sockets locales.
 */
interface PeerConnection {
    val dispositivo: Dispositivo
    suspend fun conectar(): Boolean
    suspend fun desconectar()
    suspend fun enviar(paquete: PaqueteMesh): Boolean
    fun observarPaquetes(): Flow<PaqueteMesh>
    fun estaConectado(): Boolean
}
