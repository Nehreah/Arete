package com.renea.arete_social.domain.model

/**
 * Lo que ve una persona cuyo Michi está en Modo Súper: todos los lugares y los eventos públicos
 * de su zona regional, aunque estén lejos. Se vacía en cuanto termina el Súper.
 */
data class VisionRegional(
    val region: Region,
    val lugares: List<EntidadSnapshot>,
    val eventos: List<EntidadSnapshot>,
    val cargadaEn: Long
)
