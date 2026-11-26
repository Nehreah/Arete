package com.renea.arete_social.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuzonCiegoCriptoTest {

    @Test
    fun derivarTagBuzonUsuario_esDeterministaParaMismaVentana() {
        val userId = "user_abc_123"
        val timestamp = 1700000000000L

        val tag1 = BuzonCiegoCripto.derivarTagBuzonUsuario(userId, timestamp)
        val tag2 = BuzonCiegoCripto.derivarTagBuzonUsuario(userId, timestamp + 1000) // Dentro de la misma ventana de 24h

        assertEquals(tag1, tag2)
        assertEquals(64, tag1.length) // SHA-256 en hexadecimal = 64 chars
    }

    @Test
    fun derivarTagBuzonUsuario_rotaEnVentanaSiguiente() {
        val userId = "user_abc_123"
        val ventana1 = 1700000000000L
        val ventana2 = ventana1 + BuzonCiegoCripto.VENTANA_ROTACION_MS + 1000

        val tag1 = BuzonCiegoCripto.derivarTagBuzonUsuario(userId, ventana1)
        val tag2 = BuzonCiegoCripto.derivarTagBuzonUsuario(userId, ventana2)

        assertNotEquals(tag1, tag2)
    }

    @Test
    fun derivarTagComunidad_esDistintoDeUsuario() {
        val id = "comunidad_general"
        val tagComunidad = BuzonCiegoCripto.derivarTagComunidad(id)
        val tagUsuario = BuzonCiegoCripto.derivarTagBuzonUsuario(id)

        assertNotEquals(tagComunidad, tagUsuario)
    }

    @Test
    fun tagsActivosParaUsuario_retornaVentanaActualYAnterior() {
        val userId = "user_test_xyz"
        val tags = BuzonCiegoCripto.tagsActivosParaUsuario(userId)

        assertEquals(2, tags.size)
        assertNotEquals(tags[0], tags[1])
    }

    @Test
    fun padding_multiploDeBloqueYReversible() {
        val mensajeOriginal = "Hola mundo desde Aréte!".toByteArray(Charsets.UTF_8)
        val padded = BuzonCiegoCripto.aplicarPadding(mensajeOriginal)

        // El tamaño con padding debe ser múltiplo exacto de TAMANO_BLOQUE_PADDING (256)
        assertEquals(0, padded.size % BuzonCiegoCripto.TAMANO_BLOQUE_PADDING)
        assertTrue(padded.size >= mensajeOriginal.size)

        val recuperado = BuzonCiegoCripto.removerPadding(padded)
        assertEquals(String(mensajeOriginal, Charsets.UTF_8), String(recuperado, Charsets.UTF_8))
    }
}
