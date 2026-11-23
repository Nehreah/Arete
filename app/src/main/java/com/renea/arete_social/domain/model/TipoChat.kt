package com.renea.arete_social.domain.model

/**
 * A qué entidad está asociado un [Chat]. Un chat nunca pertenece solo a una persona:
 * siempre cuelga de un lugar, un evento, un grupo, o es una conversación 1 a 1 entre usuarios.
 */
enum class TipoChat {
    GENERAL,
    SALA,
    COMUNIDAD,
    EVENTO,
    LUGAR,
    USUARIO
}