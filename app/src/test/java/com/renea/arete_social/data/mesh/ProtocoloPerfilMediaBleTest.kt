package com.renea.arete_social.data.mesh

import com.renea.arete_social.domain.model.ProtocoloPerfilMediaBle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocoloPerfilMediaBleTest {

    private val hashValido = "a".repeat(64)

    @Test
    fun `formatea y parsea peticion de media correctamente`() {
        val pedido = ProtocoloPerfilMediaBle.pedir(hashValido)
        assertEquals("[MEDIA_REQ:v1:$hashValido]", pedido)
        assertEquals(hashValido, ProtocoloPerfilMediaBle.parsearPedir(pedido))
    }

    @Test
    fun `descarta peticion de media con formato o hash invalido`() {
        assertNull(ProtocoloPerfilMediaBle.parsearPedir("invalido"))
        assertNull(ProtocoloPerfilMediaBle.parsearPedir("[MEDIA_REQ:v1:hash_corto]"))
        assertNull(ProtocoloPerfilMediaBle.parsearPedir("[MEDIA_REQ:v1:${"z".repeat(64)}]")) // z no es hex
    }

    @Test
    fun `codifica y decodifica trozo de media correctamente`() {
        val payload = "Miniatura de prueba en bytes".toByteArray(Charsets.UTF_8)
        val codificado = ProtocoloPerfilMediaBle.codificarTrozo(hashValido, indice = 0, total = 3, bytesTrozo = payload)

        val trozo = ProtocoloPerfilMediaBle.parsearTrozo(codificado)
        assertNotNull(trozo)
        assertEquals(hashValido, trozo?.hashSha256)
        assertEquals(0, trozo?.indice)
        assertEquals(3, trozo?.total)
        assertTrue(payload.contentEquals(trozo?.bytes ?: ByteArray(0)))
    }

    @Test
    fun `descarta trozo con indices invalidos o fuera de rango`() {
        val payload = byteArrayOf(1, 2, 3)
        // indice >= total
        val inv1 = ProtocoloPerfilMediaBle.codificarTrozo(hashValido, indice = 3, total = 3, bytesTrozo = payload)
        assertNull(ProtocoloPerfilMediaBle.parsearTrozo(inv1))

        // total > MAX_CHUNKS (100)
        val inv2 = ProtocoloPerfilMediaBle.codificarTrozo(hashValido, indice = 0, total = 101, bytesTrozo = payload)
        assertNull(ProtocoloPerfilMediaBle.parsearTrozo(inv2))
    }

    @Test
    fun `formatea y parsea peticion de trozos faltantes correctamente`() {
        val faltantes = listOf(1, 3, 5)
        val codificado = ProtocoloPerfilMediaBle.pedirFaltantes(hashValido, faltantes)
        assertEquals("[MEDIA_MISSING:v1:$hashValido:1,3,5]", codificado)

        val resultado = ProtocoloPerfilMediaBle.parsearFaltantes(codificado)
        assertNotNull(resultado)
        assertEquals(hashValido, resultado?.hashSha256)
        assertEquals(faltantes, resultado?.indices)
    }

    @Test
    fun `descarta peticion de faltantes mal formateada`() {
        assertNull(ProtocoloPerfilMediaBle.parsearFaltantes("[MEDIA_MISSING:v1:invalido]"))
        assertNull(ProtocoloPerfilMediaBle.parsearFaltantes("[MEDIA_MISSING:v1:$hashValido:]"))
        assertNull(ProtocoloPerfilMediaBle.parsearFaltantes("[MEDIA_MISSING:v1:$hashValido:abc]"))
    }
}
