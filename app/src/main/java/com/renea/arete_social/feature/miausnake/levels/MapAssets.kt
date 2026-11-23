package com.renea.arete_social.feature.miausnake.levels

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.InputStream

/** Catálogo estático para tests y entornos JVM que no tienen Context de Android. */
object MapAssets {
    private val json = Json { ignoreUnknownKeys = true }

    val catalog: LevelCatalog by lazy { loadFromClasspathOrFile() }

    private fun loadFromClasspathOrFile(): LevelCatalog {
        val cl = MapAssets::class.java.classLoader
        fun readStream(path: String): InputStream? = cl?.getResourceAsStream(path)

        val indexStream = readStream("miausnake/levels/index.json")
        if (indexStream != null) {
            val indexText = indexStream.bufferedReader().use { it.readText() }
            val root = json.parseToJsonElement(indexText).jsonObject
            val levelsArr = root["levels"]!!.jsonArray
            val specs = ArrayList<LevelSpec>(levelsArr.size)
            for (i in 0 until levelsArr.size) {
                val fn = levelsArr[i].jsonPrimitive.content
                val s = readStream("miausnake/levels/$fn")
                    ?: error("No se encontró recurso para $fn")
                val text = s.bufferedReader().use { it.readText() }
                specs.add(LevelParser.parse(text))
            }
            return LevelCatalog(specs)
        }

        // Fallback a ruta de sistema de archivos relativa al workspace en tests JVM
        val dir = listOf(
            File("app/src/main/assets/miausnake/levels"),
            File("src/main/assets/miausnake/levels"),
            File("../app/src/main/assets/miausnake/levels")
        ).firstOrNull { it.isDirectory } ?: error("No se encontró la carpeta de mapas en classpath ni filesystem")

        val indexFile = File(dir, "index.json")
        val root = json.parseToJsonElement(indexFile.readText()).jsonObject
        val levelsArr = root["levels"]!!.jsonArray
        val specs = ArrayList<LevelSpec>(levelsArr.size)
        for (i in 0 until levelsArr.size) {
            val fn = levelsArr[i].jsonPrimitive.content
            val f = File(dir, fn)
            specs.add(LevelParser.parse(f.readText()))
        }
        return LevelCatalog(specs)
    }
}
