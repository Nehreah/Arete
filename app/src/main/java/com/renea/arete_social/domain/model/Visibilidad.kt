package com.renea.arete_social.domain.model

/**
 * Controla si un [Usuario] es descubrible por personas cercanas y con qué precisión.
 */
enum class Visibilidad {
    VISIBLE,
    APROXIMADA,
    INVISIBLE;

    companion object {
        fun desdeServidor(v: String?): Visibilidad = when (v?.uppercase()?.trim()) {
            "INVISIBLE", "OCULTO" -> INVISIBLE
            "APROXIMADA" -> APROXIMADA
            else -> VISIBLE
        }
    }
}