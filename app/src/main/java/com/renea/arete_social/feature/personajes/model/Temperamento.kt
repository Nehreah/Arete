package com.renea.arete_social.feature.personajes.model

import kotlinx.serialization.Serializable

/**
 * Temperamento base de un personaje (Michi).
 *
 * Define la personalidad innata, preferencias de actividades en ambientes, ritmo
 * de desgaste de ciertas necesidades y la afinidad/compatibilidad con otros personajes.
 */
@Serializable
enum class Temperamento(
    val nombreVisible: String,
    val descripcion: String,
    val factorHambre: Float = 1.0f,
    val factorEnergia: Float = 1.0f,
    val factorFelicidad: Float = 1.0f
) {
    ENERGETICO(
        nombreVisible = "Enérgico",
        descripcion = "Siempre en movimiento, adora el gimnasio y los juegos activos.",
        factorEnergia = 1.2f,
        factorFelicidad = 1.1f
    ),
    TRANQUILO(
        nombreVisible = "Tranquilo",
        descripcion = "Pacífico y sereno, disfruta descansar y pasear con calma.",
        factorEnergia = 0.8f,
        factorHambre = 0.9f
    ),
    CURIOSO(
        nombreVisible = "Curioso",
        descripcion = "Explorador nato, le encanta descubrir objetos y nuevos rincones.",
        factorFelicidad = 1.2f
    ),
    SOCIABLE(
        nombreVisible = "Sociable",
        descripcion = "Muy amigable, gana confianza rápidamente y disfruta charlar.",
        factorFelicidad = 1.15f
    ),
    GLOTON(
        nombreVisible = "Glotón",
        descripcion = "Apasionado por los bocadillos, siempre listo para comer.",
        factorHambre = 1.25f
    ),
    ARTISTA(
        nombreVisible = "Artista",
        descripcion = "Sensible y creativo, se inspira con decoraciones y música.",
        factorFelicidad = 1.1f,
        factorEnergia = 0.95f
    )
}
