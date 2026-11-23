package com.renea.arete_social.feature.miausnake.core

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Gate de la Fase 1: cero dependencias de Android (ni de Michi/Aréte) dentro del core. */
class CoreAislamientoTest {
    private val ruta = "src/main/java/com/renea/arete_social/feature/miausnake/core"

    private fun raiz(): File =
        listOf(File(ruta), File("app/$ruta")).firstOrNull { it.isDirectory }
            ?: error("No se encontró el paquete del core ($ruta); ejecuta las pruebas desde el proyecto")

    @Test fun `el core solo importa java, kotlin y su propio paquete`() {
        val archivos = raiz().walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("faltan archivos del core", archivos.size >= 10)
        val propios = "com.renea.arete_social.feature.miausnake.core"
        val infractores = archivos.flatMap { f ->
            f.readLines().map { it.trim() }
                .filter { it.startsWith("import ") }
                .map { it.removePrefix("import ").trim() }
                .filterNot { it.startsWith("java.") || it.startsWith("kotlin.") || it.startsWith(propios) }
                .map { "${f.name}: $it" }
        }
        assertTrue("Dependencias prohibidas en el core: $infractores", infractores.isEmpty())
    }
}
