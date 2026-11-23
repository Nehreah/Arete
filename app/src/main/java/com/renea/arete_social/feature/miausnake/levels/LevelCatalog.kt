package com.renea.arete_social.feature.miausnake.levels

/** Catálogo inmutable de mapas ordenados. */
class LevelCatalog(val levels: List<LevelSpec>) {
    fun byId(id: String?): LevelSpec? = if (id == null) null else levels.firstOrNull { it.id == id }
    fun pack(packNo: Int): List<LevelSpec> = levels.filter { it.pack == packNo }
}
