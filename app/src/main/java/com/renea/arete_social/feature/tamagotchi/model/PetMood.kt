package com.renea.arete_social.feature.tamagotchi.model

/**
 * Estado de ánimo visible. Los nombres de los valores se mantienen estables porque el
 * dibujo de la mascota (PetCanvas) cambia de color y expresión según ellos.
 */
enum class PetMood(val label: String, val badgeEmoji: String) {
    DORMIDO("Durmiendo", "💤"),
    ENFERMO("Enferma", "🤒"),
    HAMBRIENTO("Con hambre", "🍽️"),
    SUCIO("Sucia", "💦"),
    CANSADO("Cansada", "😴"),
    TRISTE("Triste", "💧"),
    FELIZ("Feliz", "✨"),
    NORMAL("Tranquila", "😊");

    companion object {
        fun from(pet: PetEntity): PetMood = when {
            pet.isCorrupted -> ENFERMO
            pet.isSleeping -> DORMIDO
            pet.isSick -> ENFERMO
            pet.hunger <= PetBalance.CRITICAL_THRESHOLD -> HAMBRIENTO
            pet.hygiene <= PetBalance.CRITICAL_THRESHOLD -> SUCIO
            pet.energy <= PetBalance.CRITICAL_THRESHOLD -> CANSADO
            pet.happiness <= PetBalance.CRITICAL_THRESHOLD -> TRISTE
            pet.happiness >= 75 && pet.hunger >= 70 && pet.health >= 80 -> FELIZ
            else -> NORMAL
        }
    }
}
