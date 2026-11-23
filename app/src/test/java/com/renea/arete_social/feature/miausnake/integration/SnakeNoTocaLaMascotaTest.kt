package com.renea.arete_social.feature.miausnake.integration

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Gates estáticos de la Fase 3. */
class SnakeNoTocaLaMascotaTest {
    private fun src(ruta: String): File =
        listOf(File("src/main/java/com/renea/arete_social/$ruta"), File("app/src/main/java/com/renea/arete_social/$ruta"))
            .firstOrNull { it.exists() } ?: error("no se encontró $ruta")

    @Test fun `Snake no importa la simulacion ni la entidad de la mascota`() {
        val prohibidos = listOf("tamagotchi.logic", "tamagotchi.model.PetEntity", "PetSimulation")
        val infractores = src("feature/miausnake").walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            f.readLines().filter { it.trim().startsWith("import ") }
                .filter { l -> prohibidos.any { l.contains(it) } }.map { "${f.name}: $it" }
        }.toList()
        assertTrue("Snake solo habla con Michi por el puente: $infractores", infractores.isEmpty())
    }

    @Test fun `Automatico sigue siendo vm play y PetScreen no lo reimplementa`() {
        val t = src("gui/tamagotchi/PetScreen.kt").readText()
        assertTrue(t.contains("automatico = vm::play"))
        assertTrue(!t.contains("repository.play("))
    }

    @Test fun `la Activity de debug de la Fase 2 ya no existe`() {
        val raiz = listOf(File("src"), File("app/src")).first { it.isDirectory }
        assertTrue(!File(raiz, "debug").exists())
    }
}
