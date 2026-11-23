package com.renea.arete_social.feature.miausnake.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Gate de la Fase 2: «el repositorio no contiene assets de arte ni audio» de Miau Snake.
 * Se vigilan los recursos y assets propios del juego (nombre con «miau» o «snake») y que el paquete
 * del feature solo contenga código. Los recursos del resto de Aréte (íconos, etc.) no se tocan.
 */
class SinAssetsDeArteTest {
    private val extensiones = setOf(
        "png", "webp", "jpg", "jpeg", "gif", "svg", "xml.png", "9.png",
        "ogg", "mp3", "wav", "m4a", "flac", "mid", "midi", "aac",
        "ttf", "otf", "woff", "woff2"
    )

    private fun modulo(): File =
        listOf(File("."), File("app")).first { File(it, "src/main").isDirectory }

    @Test fun `no hay arte ni audio propios de Miau Snake en res ni assets`() {
        val src = File(modulo(), "src")
        val infractores = src.walkTopDown()
            .filter { it.isFile }
            .filter { f ->
                val path = f.path.replace('\\', '/')
                (path.contains("/res/") || path.contains("/assets/")) &&
                    f.name.lowercase().let { n -> n.contains("miau") || n.contains("snake") } &&
                    f.extension.lowercase() in extensiones
            }
            .map { it.path }
            .toList()
        assertTrue("Assets de Miau Snake prohibidos en la Fase 2: $infractores", infractores.isEmpty())
    }

    @Test fun `el paquete del feature solo contiene codigo Kotlin`() {
        val ruta = "src/main/java/com/renea/arete_social/feature/miausnake"
        val raiz = File(modulo(), ruta)
        assertTrue("no se encontró $ruta", raiz.isDirectory)
        val noCodigo = raiz.walkTopDown().filter { it.isFile && it.extension != "kt" }.map { it.path }.toList()
        assertTrue("Archivos que no son código en el feature: $noCodigo", noCodigo.isEmpty())
    }
}
