package com.renea.arete_social.data.remote

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NostrRelayTransportTest {

    @Test
    fun testNostrTransportSinRelaysRetornaFalse() = runTest {
        val transport = NostrRelayTransport(relaysProvider = { emptyList() })
        val exito = transport.deposit(
            mailboxTagHex = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            envelopeBytes = "test payload".toByteArray(),
            ttlSeconds = 3600
        )
        assertFalse("Sin relays configurados el depósito debe retornar false", exito)

        val sobres = transport.fetch(
            mailboxTagHex = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            afterTimestampMs = 0L
        )
        assertTrue("Sin relays configurados fetch debe retornar lista vacía", sobres.isEmpty())
    }

    @Test
    fun testNostrAckRetornaTrue() = runTest {
        val transport = NostrRelayTransport(relaysProvider = { listOf("wss://nos.lol") })
        val ackOk = transport.ack("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", listOf("ev1", "ev2"))
        assertTrue("Ack debe retornar true", ackOk)
    }

    @Test
    fun testLiveDepositAndFetch() = runTest {
        val transport = NostrRelayTransport(relaysProvider = { listOf("wss://nos.lol", "wss://relay.primal.net") })
        val tag = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val payload = "Prueba en vivo Aréte ${System.currentTimeMillis()}".toByteArray()
        val depositOk = transport.deposit(tag, payload, 3600)
        println("Deposit result: $depositOk")
        val fetched = transport.fetch(tag, System.currentTimeMillis() - 60_000)
        println("Fetched envelopes: ${fetched.size}")
    }
}
