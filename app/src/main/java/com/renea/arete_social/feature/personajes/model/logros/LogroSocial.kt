package com.renea.arete_social.feature.personajes.model.logros

import kotlinx.serialization.Serializable

/**
 * Tipos de logros sociales desbloqueables por hitos en el mundo de los personajes.
 */
@Serializable
enum class TipoLogroSocial(
    val titulo: String,
    val descripcion: String,
    val icono: String
) {
    PRIMER_VINCULO(
        "Primer Amigo",
        "Establece tu primer vínculo de amistad con otra mascota.",
        "🤝"
    ),
    VISITANTE_ACTIVO(
        "Explorador de Hogares",
        "Visita la casa de un amigo y sella un recibo firmado.",
        "🏠"
    ),
    VINCULO_INSEPARABLE(
        "Amistad Inseparable",
        "Alcanza el nivel Inseparable con 90 o más puntos de confianza.",
        "⭐"
    ),
    COLECCIONISTA_RECUERDOS(
        "Memoria de Oro",
        "Acumula 5 o más recuerdos inolvidables con tus amigos.",
        "📜"
    ),
    DEPORTISTA_FIT(
        "Michi Atleta",
        "Participa en actividades de entrenamiento en el Gimnasio.",
        "🏋️"
    ),
    ANFITRION_POPULAR(
        "Anfitrión Estrella",
        "Recibe visitas en tu casa y comparte momentos con amigos.",
        "✨"
    )
}

/**
 * Estado de un logro social para el usuario local.
 */
@Serializable
data class LogroSocial(
    val tipo: TipoLogroSocial,
    val desbloqueado: Boolean = false,
    val fechaDesbloqueoMillis: Long? = null
) {
    val titulo: String get() = tipo.titulo
    val descripcion: String get() = tipo.descripcion
    val icono: String get() = tipo.icono
}
