package com.renea.arete_social.feature.tamagotchi.viewmodel

import com.renea.arete_social.domain.model.Dispositivo
import com.renea.arete_social.feature.tamagotchi.model.PetEntity
import com.renea.arete_social.feature.tamagotchi.model.PetMood
import com.renea.arete_social.gui.tamagotchi.PetActionType

/**
 * Todo lo que la pantalla necesita para dibujarse. Los tiempos de espera se expresan
 * en segundos y se recalculan cada segundo (antes quedaban congelados en pantalla).
 */
data class PetUiState(
    val ownerId: String = "",
    val ownerNombre: String = "",
    /** True si quien mira es el dueño. Si es una visita, la pantalla es solo de lectura. */
    val isOwner: Boolean = true,
    val pet: PetEntity? = null,
    val mood: PetMood = PetMood.NORMAL,
    /** Mensaje temporal (resultado de una acción, saludo...) o, si no hay, el aviso más urgente. */
    val alertMessage: String? = null,
    val activeAnimation: PetActionType? = null,
    val nearbyFriends: List<Dispositivo> = emptyList(),
    val radarReport: String? = null,

    val cooldownAlimentarSecs: Int = 0,
    val cooldownJugarSecs: Int = 0,
    val cooldownLimpiarSecs: Int = 0,
    /** Segundos hasta la siguiente etapa de vida (null si ya es anciana). */
    val segundosParaCrecer: Long? = null
)
