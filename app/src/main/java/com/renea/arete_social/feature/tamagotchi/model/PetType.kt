package com.renea.arete_social.feature.tamagotchi.model

import kotlinx.serialization.Serializable

@Serializable
enum class PetType(val displayName: String, val emoji: String) {
    GATO("Gato", "🐱"),
    PERRO("Perro", "🐶"),
    ZORRO("Zorro", "🦊"),
    CONEJO("Conejo", "🐰")
}
