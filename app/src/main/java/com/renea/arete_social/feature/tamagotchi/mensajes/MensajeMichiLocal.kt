package com.renea.arete_social.feature.tamagotchi.mensajes

import kotlinx.serialization.Serializable

@Serializable
enum class TipoMensajeMichi(val icono: String) {
    AMIGO("👥"),
    EVENTO("📍"),
    LECTURA("📖"),
    OBRA_COMPARTIDA("📜"),
    ENERGIA("⚡"),
    SUPER("✦"),
    CUIDADO("🐾"),
    GENERAL("💬")
}

@Serializable
data class MensajeMichiLocal(
    val id: String,
    val emisorNombre: String,
    val texto: String,
    val timestamp: Long = System.currentTimeMillis(),
    val tipo: TipoMensajeMichi = TipoMensajeMichi.GENERAL
)
