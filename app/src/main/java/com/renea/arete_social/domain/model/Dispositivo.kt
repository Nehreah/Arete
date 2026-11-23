package com.renea.arete_social.domain.model

/**
 * Representa un dispositivo cercano descubierto por un [com.renea.arete_social.domain.proximity.DetectorProximidad].
 * Hoy solo lo produce el detector por GPS (como un "dispositivo virtual" ligado a un usuario);
 * en la Parte III lo producirá también el detector BLE.
 */
data class Dispositivo(
    val id: String,
    val nombre: String,
    val señal: Int?,
    val ultimaConexion: Long,
    val usuarioId: String?,
    val distanciaMetros: Double?,
    // Posición real, cuando la fuente la conoce. El detector GPS (Etapa 4) sí la
    // rellena; un futuro detector BLE (Parte III) la dejará en null, porque BLE por
    // sí solo no da una posición GPS precisa (ver documento original del proyecto).
    val ubicacion: Ubicacion? = null,
    // Dirección MAC Bluetooth del dispositivo físico, necesaria para abrir sockets
    // RFCOMM (mensajería offline). Solo disponible cuando la fuente es BLE.
    val macAddress: String? = null,
    val parcheActivo: Boolean = false,
    val estadoMichi: String? = null,
    val estadoMichiHasta: Long? = null
)