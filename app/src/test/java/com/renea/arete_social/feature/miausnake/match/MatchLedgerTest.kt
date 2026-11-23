package com.renea.arete_social.feature.miausnake.match

import org.junit.Assert.assertEquals
import org.junit.Test

class MatchLedgerTest {
    private fun rec(id: String, o: MatchOutcome = MatchOutcome.WON) = MatchRecord(id, 20_000, "peerB", "Bo", 5, 'M', 10, null, o)

    @Test fun `idempotente, acotado y ida y vuelta`() {
        var l = MatchLedger.EMPTY.add(rec("s1")).add(rec("s1")).add(rec("s2", MatchOutcome.LOST))
        assertEquals(2, l.entries.size); assertEquals(1, l.wins()); assertEquals(1, l.losses())
        repeat(50) { l = l.add(rec("x$it")) }
        assertEquals(MatchLedger.MAX_ENTRIES, l.entries.size)
        assertEquals(l.entries, MatchLedger.fromText(l.toText()).entries)
    }

    @Test fun `ignora lineas corruptas`() {
        val l = MatchLedger.fromText("basura\ns1|1|p|Bo|99|M|-|-|W\ns2|1|p|Bo|5|M|3|-|W\n|||")
        assertEquals(listOf("s2"), l.entries.map { it.id })
    }
}
