package com.renea.arete_social.feature.tamagotchi.model

/**
 * Rasgos que la mascota desbloquea sola por cuidado constante.
 * Cada uno tiene un efecto real en la simulación (ver PetSimulation.kt).
 */
enum class RasgoPet(val nombre: String, val descripcion: String) {
    RESISTENTE("Resistente", "Se desgasta la mitad de rápido cuando está descuidada."),
    VELOZ("Veloz", "Las esperas entre cuidados son un 20 % más cortas."),
    NOCTAMBULA("Noctámbula", "Recupera energía más rápido al dormir."),
    ESTOMAGO_DE_HIERRO("Estómago de hierro", "Comer de más no le sienta mal."),
    SOCIABLE("Sociable", "Gana una chispa extra al encontrarse con amigos.")
}
