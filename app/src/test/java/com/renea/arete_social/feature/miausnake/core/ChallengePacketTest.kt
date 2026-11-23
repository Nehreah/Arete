package com.renea.arete_social.feature.miausnake.core

import com.renea.arete_social.feature.miausnake.core.bot.BfsBot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Random

class ChallengePacketTest {
    private val nivel = TestLevels.open(goalBites = 12)

    private fun resultado(nombre: String = "Ana"): ChallengeResult {
        val s = BfsBot.play(nivel, seed = 7L)
        return ChallengeResult(Challenge(1, 5, 'M', 7L), 1, nombre, s.state.score, s.replay().inputs)
    }

    private fun bad(text: String) = (ChallengePacket.decode(text) as PacketDecode.Bad).error

    @Test fun `ida y vuelta`() {
        val r = resultado("Miaú 🐱")
        val dec = ChallengePacket.decode(ChallengePacket.encode(r))
        assertEquals(r, (dec as PacketDecode.Ok).result)
    }

    @Test fun `base64url canonico - ida y vuelta y bits sobrantes rechazados`() {
        for (n in 0..40) {
            val b = ByteArray(n) { (it * 37 + n).toByte() }
            assertTrue(b.contentEquals(Base64Url.decode(Base64Url.encode(b))!!))
        }
        assertNull(Base64Url.decode("AB")) // 'B' deja bits sobrantes distintos de 0
        assertNull(Base64Url.decode("A"))
        assertNull(Base64Url.decode("AB=="))
    }

    @Test fun `texto dentro de un mensaje y con saltos de linea`() {
        val p = ChallengePacket.encode(resultado())
        assertTrue(ChallengePacket.decode("Mira mi reto!\n$p\nsaludos") is PacketDecode.Ok)
        val partido = p.chunked(40).joinToString("\n")
        assertTrue(ChallengePacket.decode(partido) is PacketDecode.Ok)
    }

    @Test fun `cualquier cambio de un caracter se rechaza`() {
        val p = ChallengePacket.encode(resultado())
        val alfabeto = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        val rnd = Random(1)
        repeat(500) {
            val i = ChallengePacket.PREFIX.length + rnd.nextInt(p.length - ChallengePacket.PREFIX.length)
            val c = alfabeto[rnd.nextInt(alfabeto.length)]
            if (c != p[i]) {
                val t = p.substring(0, i) + c + p.substring(i + 1)
                assertTrue(t, ChallengePacket.decode(t) is PacketDecode.Bad)
            }
        }
    }

    @Test fun `truncado, vacio, ajeno y enorme`() {
        val p = ChallengePacket.encode(resultado())
        assertTrue(ChallengePacket.decode(p.dropLast(7)) is PacketDecode.Bad)
        assertEquals(PacketError.NOT_A_PACKET, bad("hola"))
        assertEquals(PacketError.NOT_A_PACKET, bad(""))
        assertEquals(PacketError.TOO_LONG, bad("x".repeat(ChallengePacket.MAX_INPUT_CHARS + 1)))
        assertEquals(PacketError.TOO_LONG, bad(ChallengePacket.PREFIX + "A".repeat(ChallengePacket.MAX_TEXT_CHARS + 1)))
        assertEquals(PacketError.BAD_ALPHABET, bad(ChallengePacket.PREFIX + "AAAAA"))
    }

    @Test fun `basura aleatoria nunca lanza`() {
        val rnd = Random(9)
        val chars = "ABCxyz019-_!= \n"
        repeat(3_000) {
            val s = ChallengePacket.PREFIX + String(CharArray(rnd.nextInt(300)) { chars[rnd.nextInt(chars.length)] })
            ChallengePacket.decode(s)
        }
    }

    @Test fun `el nombre se sanea y se acota a 24 bytes`() {
        assertEquals("Ana Perez", ChallengePacket.sanitizeName("  Ana \n\t Perez "))
        assertEquals("AB", ChallengePacket.sanitizeName("A\u202EB\u0007"))
        assertTrue(ChallengePacket.sanitizeName("🐱".repeat(20)).toByteArray(Charsets.UTF_8).size <= 24)
        val r = resultado("Ana\u202E")
        val dec = ChallengePacket.decode(ChallengePacket.encode(r)) as PacketDecode.Ok
        assertEquals("Ana", dec.result.player)
    }
}
