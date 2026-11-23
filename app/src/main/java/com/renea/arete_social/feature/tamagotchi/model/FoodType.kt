package com.renea.arete_social.feature.tamagotchi.model

/**
 * Tipos de comida. Los valores son *ganancias base*; la lógica las reduce si se
 * abusa de la comida el mismo día (ver PetSimulation.kt).
 */
enum class FoodType(
    val title: String,
    val desc: String,
    val hungerGain: Int,
    val energyGain: Int,
    val happinessGain: Int,
    val xpGain: Int,
    val costsSpark: Boolean
) {
    RACION_SOLAR(
        title = "Ración solar",
        desc = "Comida sencilla. Quita buena parte del hambre.",
        hungerGain = 50,
        energyGain = 3,
        happinessGain = 8,
        xpGain = 12,
        costsSpark = false
    ),
    CHISPA_SOCIAL(
        title = "Chispa social",
        desc = "Un súper bocado. Llena mucho y da más experiencia.",
        hungerGain = 70,
        energyGain = 8,
        happinessGain = 25,
        xpGain = 40,
        costsSpark = true
    )
}
