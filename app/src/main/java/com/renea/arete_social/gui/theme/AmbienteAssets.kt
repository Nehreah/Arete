package com.renea.arete_social.gui.theme

import androidx.annotation.DrawableRes
import com.renea.arete_social.R

/**
 * Resolver centralizado de assets de imagen ambiental por pantalla según el [CicloDia] activo.
 * Sigue la regla de oro: las imágenes responden a la hora (Día/Noche), no al tema de la UI (Claro/Oscuro).
 */
object AmbienteAssets {
    @DrawableRes
    fun plazoletaComunidad(ciclo: CicloDia): Int = when (ciclo) {
        CicloDia.DIA -> R.drawable.img_campus_plazoleta_dia
        CicloDia.NOCHE -> R.drawable.img_campus_plazoleta_noche
    }

    @DrawableRes
    fun bannerPerfil(ciclo: CicloDia): Int = when (ciclo) {
        CicloDia.DIA -> R.drawable.img_campus_plazoleta_dia
        CicloDia.NOCHE -> R.drawable.img_campus_plazoleta_noche
}
}
