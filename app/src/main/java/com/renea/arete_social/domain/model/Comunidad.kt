package com.renea.arete_social.domain.model

/**
 * Entidad permanente (ej. "Psicología — Univalle"). No requiere tener ningún [Grupo]
 * asociado: una comunidad puede existir sola, solo con miembros y/o eventos.
 */
data class Comunidad(
    val id: String,
    val nombre: String,
    val descripcion: String,
    val tipo: String,
    val chatId: String,
    val liderId: String = "",
    val zona: String = "Zona Norte",
    val miembrosCount: Int = 0,
    val fotoUrl: String? = null,
    /**
     * URLs de foto de hasta N miembros recientes/activos, para el componente de
     * "avatares apilados" del Plan de Fidelidad Visual v2 (sección 4.5 / 7). Default
     * vacío: no rompe nada existente que construya [Comunidad] sin este campo, y
     * [CommunityListItemCard] ya maneja una lista vacía mostrando solo el conteo.
     */
    val avataresPreview: List<String> = emptyList()
)