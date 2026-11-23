package com.renea.arete_social.feature.cartas

import com.renea.arete_social.domain.model.Carta

data class CartasUiState(
    val cargando: Boolean = false,
    val enviando: Boolean = false,
    /** False para sesiones de invitado: sin cuenta no hay servidor donde guardar cartas. */
    val disponible: Boolean = true,
    val recibidas: List<Carta> = emptyList(),
    val enviadas: List<Carta> = emptyList(),
    val mensaje: String? = null
) {
    val sinLeer: Int get() = recibidas.count { !it.leida }
}
