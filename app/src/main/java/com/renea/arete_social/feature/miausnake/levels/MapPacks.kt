package com.renea.arete_social.feature.miausnake.levels

/** Definición y títulos de los packs de mapas según el Plan Maestro. */
object MapPacks {
    const val COUNT = 4

    fun title(pack: Int): String = when (pack) {
        1 -> "Primeros pasos"
        2 -> "Geometría"
        3 -> "Dibujos secretos"
        4 -> "Ideas retorcidas"
        else -> "Pack $pack"
    }

    fun edgeTitle(pack: Int): String = when (pack) {
        1 -> "Wrap"
        2 -> "Sólido"
        3 -> "Parcial"
        4 -> "Túneles"
        else -> "Borde"
    }
}
