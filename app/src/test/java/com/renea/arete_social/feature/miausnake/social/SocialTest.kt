package com.renea.arete_social.feature.miausnake.social

import com.renea.arete_social.feature.miausnake.core.Challenge
import com.renea.arete_social.feature.miausnake.core.ChallengePacket
import com.renea.arete_social.feature.miausnake.core.ChallengeResult
import com.renea.arete_social.feature.miausnake.core.Level
import com.renea.arete_social.feature.miausnake.core.PacketError
import com.renea.arete_social.feature.miausnake.core.Rejection
import com.renea.arete_social.feature.miausnake.core.TestLevels
import com.renea.arete_social.feature.miausnake.core.bot.BfsBot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SocialTest {
    private val meta = TestLevels.open(goalBites = 12)
    private val resolver = LevelResolver { _, mode -> if (mode == 'M') meta else TestLevels.open(goalBites = null) }

    private fun packet(score: Int? = null): String {
        val s = BfsBot.play(meta, seed = 7L)
        val r = ChallengeResult(Challenge(1, 5, 'M', 7L), 1, "Ana", score ?: s.state.score, s.replay().inputs)
        return ChallengePacket.encode(r)
    }

    // ---- importador ----
    @Test fun `importa invitacion, resultado valido, falso y roto`() {
        assertTrue(ChallengeImporter.import("Te reto: MIAU1-17-I-7F3A9C ya!", resolver) is ImportOutcome.Invitation)
        assertTrue(ChallengeImporter.import(packet(), resolver) is ImportOutcome.Verified)
        val falso = ChallengeImporter.import(packet(score = 99_990), resolver) as ImportOutcome.Invalid
        assertEquals(Rejection.SCORE_MISMATCH, falso.reason)
        val roto = ChallengeImporter.import(packet().dropLast(5), resolver) as ImportOutcome.Unreadable
        assertTrue(roto.error in listOf(PacketError.BAD_CRC, PacketError.TRUNCATED, PacketError.BAD_ALPHABET))
        assertEquals(ImportOutcome.NothingFound, ImportOutcome.NothingFound.let { ChallengeImporter.import("hola", resolver) })
        assertTrue(ChallengeImporter.import("MIAU2-5-I-7F3A9C", resolver) is ImportOutcome.UnsupportedRules)
        assertTrue(ChallengeImporter.import("MIAU1-5-I-7F3A9C", { _, _ -> null }) is ImportOutcome.UnknownMap)
    }

    // ---- libro de retos ----
    @Test fun `libro idempotente, acotado y tolerante`() {
        val p = packet()
        var b = ChallengeBook.EMPTY.add(ChallengeRole.RECEIVED, p, null, 10)
        b = b.add(ChallengeRole.RECEIVED, p, null, 11)
        assertEquals(1, b.entries.size)
        val id = ChallengeBook.idOf(p)
        b = b.withMyScore(id, 50).withMyScore(id, 30)
        assertEquals(50, b.get(id)!!.myScore)
        val vuelta = ChallengeBook.fromText(b.toText())
        assertEquals(b.entries, vuelta.entries)
        assertEquals(0, ChallengeBook.fromText("basura\n|||||\n" + b.toText().replace(id, "0".repeat(32))).entries.size)
        assertFalse(ChallengeBook.canStore("x"))
        assertFalse(ChallengeBook.EMPTY.add(ChallengeRole.SENT, "MIAUR1:" + "A".repeat(20_000), null, 1).entries.isNotEmpty())
    }

    // ---- reto diario ----
    @Test fun `diario conserva el mejor y poda`() {
        var d = DailyBook.EMPTY.record(100, 50).record(100, 30).record(100, 80)
        assertEquals(80, d.best(100))
        for (day in 0L until 80L) d = d.record(day, 1)
        assertEquals(null, d.best(5))
        assertEquals(DailyBook.MAX_DAYS, DailyBook.fromText(d.toText()).toText().lines().size)
    }

    // ---- cola diferida ----
    @Test fun `cola idempotente, expira y reintenta con espera`() {
        val ob = DeferredOutbox.EMPTY.enqueue("peer-1", "MIAU1-5-I-7F3A9C", 0)
        assertEquals(1, ob.enqueue("peer-1", "MIAU1-5-I-7F3A9C", 5).items.size)
        assertEquals(2, ob.enqueue("peer-2", "MIAU1-5-I-7F3A9C", 5).items.size)
        assertEquals(0, ob.enqueue("mal peer", "x", 0).items.size - 1)
        val id = ob.items[0].messageId
        assertEquals(1, ob.due(0).size)
        val t = ob.attempted(id, 1_000)
        assertEquals(0, t.due(1_001).size)
        assertEquals(1, t.due(1_000 + 60_000).size)
        assertEquals(0, ob.due(DeferredOutbox.DEFAULT_TTL_MS).size)
        assertEquals(0, ob.expire(DeferredOutbox.DEFAULT_TTL_MS).items.size)
        assertEquals(ob.items, DeferredOutbox.fromText(ob.toText()).items)
    }

    @Test fun `la bomba entrega, descarta y reintenta`() = runBlocking {
        var ob = DeferredOutbox.EMPTY.enqueue("a", "MIAU1-1-I-000001", 0).enqueue("b", "MIAU1-1-I-000002", 0).enqueue("c", "MIAU1-1-I-000003", 0)
        val t = object : ChallengeTransport {
            override suspend fun send(peerId: String, messageId: String, payload: String) = when (peerId) {
                "a" -> SendOutcome.DELIVERED
                "b" -> SendOutcome.REJECTED
                else -> throw IllegalStateException("radio apagada")
            }
        }
        val (q, rep) = OutboxPump(t).flush(ob, 10)
        assertEquals(1, rep.delivered); assertEquals(1, rep.dropped); assertEquals(1, rep.retried)
        assertEquals(1, q.items.size)
        assertEquals(1, q.items[0].attempts)
        val (q2, rep2) = OutboxPump(NoTransport).flush(q, DeferredOutbox.DEFAULT_TTL_MS + 1)
        assertEquals(1, rep2.expired); assertEquals(0, q2.items.size)
    }

    @Test fun `entrada duplicada se ignora`() {
        val id = DeferredOutbox.messageIdOf("a", "MIAU1-1-I-000001")
        val (nuevo, s1) = SeenIds.EMPTY.accept(id, 0)
        assertTrue(nuevo)
        assertFalse(s1.accept(id, 5).first)
        assertFalse(SeenIds.EMPTY.accept("no-es-id", 0).first)
        assertTrue(SeenIds.fromText(s1.toText()).contains(id))
    }

    // ---- aislamiento ----
    @Test fun `social solo importa java, kotlin, core y social`() {
        val ruta = "src/main/java/com/renea/arete_social/feature/miausnake/social"
        val dir = listOf(File(ruta), File("app/$ruta")).first { it.isDirectory }
        val base = "com.renea.arete_social.feature.miausnake"
        val malos = dir.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            f.readLines().map { it.trim() }.filter { it.startsWith("import ") }.map { it.removePrefix("import ") }
                .filterNot { it.startsWith("java.") || it.startsWith("kotlin.") || it.startsWith("$base.core") || it.startsWith("$base.social") }
                .map { "${f.name}: $it" }
        }.toList()
        assertTrue(malos.toString(), malos.isEmpty())
    }
}
