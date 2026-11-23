package com.renea.arete_social.domain.model

data class Evento(
    val id: String,
    val nombre: String,
    val descripcion: String,
    val fecha: Long,
    val ubicacion: Ubicacion,
    val lugarId: String? = null,
    val creadorId: String,
    val comunidadId: String?,
    val participantesIds: List<String>,
    val chatId: String,
    val visibilidad: VisibilidadEvento = VisibilidadEvento.PUBLICO,
    val fotoUrl: String? = null,
    val adminsIds: List<String> = emptyList()
)

data class ParticipanteEvento(
    val usuarioId: String,
    val nombre: String = "Usuario",
    val fotoUrl: String? = null,
    val rol: String = "ASISTENTE"
)


/**
 * Quién puede ver un evento (lo aplica el servidor con RLS, no solo la app).
 * El tipo de evento lo decide [Evento.comunidadId]:
 *
 * Evento personal (creado desde el mapa, sin comunidad): lógica público/privado.
 *  - [PUBLICO]: cualquier persona con sesión.
 *  - [AMIGOS]: privado; solo quien lo creó y sus amistades aceptadas.
 *
 * Evento creado desde una comunidad: conserva su origen en [Evento.comunidadId], pero
 * puede ser [PUBLICO] (lo ve todo el mundo) o [COMUNIDAD] (solo miembros).
 * [AMIGOS] representa la privacidad de eventos personales.
 */
enum class VisibilidadEvento {
    PUBLICO,
    AMIGOS,
    COMUNIDAD;

    companion object {
        fun desdeServidor(valor: String?): VisibilidadEvento =
            entries.firstOrNull { it.name == valor } ?: PUBLICO

        /**
         * Visibilidad efectiva según el origen: un evento creado desde una comunidad
         * puede ser público o exclusivo para sus miembros. Para eventos personales,
         * COMUNIDAD no tiene sentido y se convierte en privado (AMIGOS).
         */
        fun efectiva(comunidadId: String?, solicitada: VisibilidadEvento): VisibilidadEvento = when {
            !comunidadId.isNullOrBlank() && solicitada == PUBLICO -> PUBLICO
            !comunidadId.isNullOrBlank() -> COMUNIDAD
            solicitada == COMUNIDAD -> AMIGOS
            else -> solicitada
        }
    }
}