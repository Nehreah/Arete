package com.renea.arete_social.feature.miausnake.levels

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Cargador de niveles desde assets del APK. */
object AssetLevels {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context): LevelCatalog {
        val am = context.assets
        val indexJson = am.open("miausnake/levels/index.json").bufferedReader().use { it.readText() }
        val root = json.parseToJsonElement(indexJson).jsonObject
        val levelsArr = root["levels"]!!.jsonArray
        val specs = ArrayList<LevelSpec>(levelsArr.size)
        for (i in 0 until levelsArr.size) {
            val filename = levelsArr[i].jsonPrimitive.content
            val levelJson = am.open("miausnake/levels/$filename").bufferedReader().use { it.readText() }
            specs.add(LevelParser.parse(levelJson))
        }
        return LevelCatalog(specs)
    }
}
