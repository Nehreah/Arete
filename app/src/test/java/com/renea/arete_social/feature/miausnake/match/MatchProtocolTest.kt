package com.renea.arete_social.feature.miausnake.match

import com.renea.arete_social.feature.miausnake.core.Challenge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class MatchProtocolTest {
    private fun env(type: MatchType, payload: String, seq: Long = 1) =
        MatchEnvelope(1, MATCH_GAME_ID, "S1", "peerA", "m1", seq, 1000, type, payload)

    @Test fun `ida y vuelta de cada tipo`() {
        val cfg = LobbyConfig(Challenge(1, 17, 'M', 0x7F3A9C), "Ana").encode()
        val casos = listOf(
            MatchType.LOBBY_INVITE to cfg, MatchType.LOBBY_ACCEPT to "Bo", MatchType.LOBBY_READY to "",
            MatchType.GAME_START to GameStart(0x7F3A9C, 0, 3000).encode(), MatchType.PLAYER_LEFT to "D",
            MatchType.GAME_END to GameEnd("a".repeat(32), true).encode(), MatchType.PING to "abc-123", MatchType.CHALLENGE_RESULT to "MIAUR1:AbCd"
        )
        for ((t, p) in casos) {
            val d = MatchCodec.decode(MatchCodec.encode(env(t, p))) as EnvelopeDecode.Ok
            assertEquals(t, d.envelope.type); assertEquals(p, d.envelope.payload)
        }
    }

    @Test fun `rechaza basura sin lanzar`() {
        val malos = listOf("", "hola", "MSM2|miausnake|S|A|m|1|1|GO|x", "MSM1|otro|S|A|m|1|1|GO|x", "MSM1|miausnake|S|A|m|-1|1|GO|x",
            "MSM1|miausnake|S|A|m|1|1|ZZZ|x", "MSM1|miausnake|S|A|m|1|1|PING|a b", "MSM1|miausnake|S|A|m|1|1|RES|abc", "M".repeat(20_000))
        for (m in malos) assertTrue(m, MatchCodec.decode(m) is EnvelopeDecode.Bad)
        val rnd = Random(3)
        repeat(5_000) { MatchCodec.decode(String(CharArray(rnd.nextInt(0, 120)) { "MSM1|;-_:.\nabcXYZ09".random(rnd) })) }
    }

    @Test fun `go exige tick cero y cuenta atras razonable`() {
        assertNull(GameStart.parse("7F3A9C;5;3000")); assertNull(GameStart.parse("7F3A9C;0;50")); assertNull(GameStart.parse("7F3A9C;0;99999"))
        assertNotNull(GameStart.parse("7F3A9C;0;3000"))
    }

    @Test fun `el nombre no puede romper el formato`() {
        assertEquals("a b c", MatchText.cleanName("a|b;c"))
        assertNull(LobbyConfig.parse("MIAU1-17-M-7F3A9C;a|b"))
    }

    @Test fun `el paquete de reto cabe en un sobre`() {
        assertTrue(MatchLimits.MAX_RESULT_PAYLOAD + 100 < MatchLimits.MAX_ENVELOPE_CHARS)
        assertTrue(MatchLimits.MAX_ENVELOPE_CHARS < 16_000) // tope de contenido de la malla
    }

    /** match/ (salvo nada) no usa Android ni Compose: se prueba en JVM y se puede mover. */
    @Test fun `el paquete match no importa Android`() {
        val dir = listOf(File("src/main/java/com/renea/arete_social/feature/miausnake/match"), File("app/src/main/java/com/renea/arete_social/feature/miausnake/match")).firstOrNull { it.exists() }
        if (dir == null || !dir.exists()) return
        for (f in dir.listFiles { x -> x.extension == "kt" }!!) {
            val bad = f.readLines().filter { it.startsWith("import android.") || it.startsWith("import androidx.") }
            assertTrue("${f.name}: $bad", bad.isEmpty())
        }
    }
}
