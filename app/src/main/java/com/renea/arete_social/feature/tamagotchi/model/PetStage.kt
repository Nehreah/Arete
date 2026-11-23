package com.renea.arete_social.feature.tamagotchi.model

import kotlinx.serialization.Serializable

@Serializable
enum class PetStage(
    val displayName: String,
    val badgeEmoji: String,
    val minAgeMillis: Long
) {
    HUEVO("Huevo", "🥚", 0L),
    BEBE("Bebé", "🍼", 5 * 60 * 1000L),               // 5 minutos
    NINO("Niño", "🎒", 60 * 60 * 1000L),              // 1 hora
    ADULTO("Adulto", "👑", 24 * 60 * 60 * 1000L),     // 24 horas
    ANCIANO("Anciano", "👴", 7 * 24 * 60 * 60 * 1000L); // 7 días

    /** Siguiente etapa de vida, o null si ya es la última. */
    fun siguiente(): PetStage? = entries.getOrNull(ordinal + 1)

    companion object {
        fun forAge(ageMillis: Long): PetStage =
            entries.lastOrNull { ageMillis >= it.minAgeMillis } ?: HUEVO
    }
}
