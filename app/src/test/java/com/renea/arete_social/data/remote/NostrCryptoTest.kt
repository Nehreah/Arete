package com.renea.arete_social.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NostrCryptoTest {

    @Test
    fun testGeneracionYFirmaSchnorrBip340() {
        val keys = NostrCrypto.generarClaves()
        assertEquals(64, keys.privKeyHex.length)
        assertEquals(64, keys.pubKeyHex.length)

        val msg = "Hola mundo Nostr Aréte"
        val msgHash = NostrCrypto.sha256Bytes(msg.toByteArray(Charsets.UTF_8))
        assertEquals(32, msgHash.size)

        val sig = NostrCrypto.firmarBip340(msgHash, keys.privKeyHex)
        assertEquals(128, sig.length)

        val esValida = NostrCrypto.verificarBip340(msgHash, keys.pubKeyHex, sig)
        assertTrue("La firma Schnorr BIP-340 debe ser válida con la clave pública generada", esValida)
    }

    @Test
    fun testSerializacionCanonicoNip01() {
        val pubkey = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"
        val createdAt = 1234567890L
        val kind = 1
        val tags = listOf(listOf("t", "test-tag"), listOf("e", "abc123def456"))
        val content = "Mensaje de prueba con \"comillas\" y salto\nde línea"

        val canonico = NostrCrypto.serializarEventoCanónico(pubkey, createdAt, kind, tags, content)
        val esperado = """[0,"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",1234567890,1,[["t","test-tag"],["e","abc123def456"]],"Mensaje de prueba con \"comillas\" y salto\nde línea"]"""
        assertEquals(esperado, canonico)

        val eventId = NostrCrypto.calcularEventId(pubkey, createdAt, kind, tags, content)
        assertEquals(64, eventId.length)
    }
}
