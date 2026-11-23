package com.renea.arete_social.feature.tamagotchi.model

/**
 * Cómo está de cuidada la mascota en este momento. Pensado para que la persona entienda,
 * sin números, si va por buen camino con las visitas que le da al día.
 */
enum class NivelCuidado(val titulo: String) {
    EXCELENTE("Excelente"),
    ESTABLE("Estable"),
    JUSTA("Justa"),
    NECESITA_ATENCION("Necesita atención")
}
