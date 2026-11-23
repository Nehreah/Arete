package com.renea.arete_social.feature.miausnake.release

import com.renea.arete_social.domain.model.FeatureFlags
import com.renea.arete_social.domain.model.ProtocoloCanalesMesh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Fase 7: vigilantes de la versión candidata. Son baratos y fallan ruidosamente si alguien
 * enciende por error algo que aún no pasó su puerta o deja basura de depuración en el juego.
 */
class ReleaseHigieneTest {
    private fun modulo(): File =
        listOf(File("."), File("app")).first { File(it, "src/main").isDirectory }

    private fun fuentesJuego(): List<File> =
        File(modulo(), "src/main/java/com/renea/arete_social/feature/miausnake")
            .walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test fun `la partida cercana sigue apagada hasta medir en telefonos reales`() {
        assertFalse(
            "MIAUSNAKE_CERCANIA debe ser false en 1.0: la puerta de la Fase 6 no está superada",
            FeatureFlags.MIAUSNAKE_CERCANIA
        )
    }

    @Test fun `el canal del duelo no es un chat visible`() {
        assertTrue(ProtocoloCanalesMesh.esCanalP2P(ProtocoloCanalesMesh.CANAL_MIAUSNAKE_MATCH))
    }

    @Test fun `el juego no declara ni pide permisos propios`() {
        val culpables = fuentesJuego().filter { "android.permission" in it.readText() }.map { it.name }
        assertTrue("El juego no debe pedir permisos: $culpables", culpables.isEmpty())
    }

    @Test fun `el juego no escribe repeticiones ni paquetes en el log`() {
        val log = Regex("""\b(Log\.[dviwe]|println)\(""")
        val sensible = Regex("""replay|payload|packet|firma|secret|nombre|player""", RegexOption.IGNORE_CASE)
        val culpables = fuentesJuego().flatMap { f ->
            f.readLines().withIndex()
                .filter { (_, l) -> log.containsMatchIn(l) && sensible.containsMatchIn(l) }
                .map { (i, _) -> "${f.name}:${i + 1}" }
        }
        assertTrue("Log con datos sensibles: $culpables", culpables.isEmpty())
    }

    @Test fun `el panel de depuracion del host solo existe en builds debug`() {
        val host = fuentesJuego().first { it.name == "MiauSnakeEnMichi.kt" }.readText()
        val usos = Regex("""ajustes\.showDebug""").findAll(host).map { it.range.first }.toList()
        val ifDebug = host.indexOf("if (BuildConfig.DEBUG)")
        assertTrue("falta el guardián BuildConfig.DEBUG", ifDebug >= 0)
        assertTrue("el panel de depuración se usa antes del guardián", usos.all { it > ifDebug })
    }

    @Test fun `el texto del juego no baja de 12 sp salvo la depuracion`() {
        val chicos = fuentesJuego()
            .filter { "spike" !in it.path && "Playtest" !in it.name }
            .flatMap { f ->
                f.readLines().withIndex().filter { (_, l) ->
                    Regex("""size\s*=\s*(\d+)""").find(l)?.groupValues?.get(1)?.toInt()?.let { it < 12 } == true &&
                        "debugText" !in l
                }.map { (i, _) -> "${f.name}:${i + 1}" }
            }
        assertEquals("Texto por debajo de 12 sp: $chicos", emptyList<String>(), chicos)
    }
}
