package com.renea.arete_social.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicacionPerfilTest {

    private fun pub(id: String, likes: Set<String> = emptySet(), timestamp: Long = System.currentTimeMillis()) =
        PublicacionPerfil(
            id = id,
            usuarioId = "autor",
            tipo = TipoPublicacionPerfil.TEXTO_RETRO,
            contenido = "hola",
            timestamp = timestamp,
            likes = likes.size,
            usuariosLike = likes
        )

    @Test fun `dar like solo cambia la publicacion indicada`() {
        val lista = listOf(pub("a"), pub("b"))
        val nueva = lista.conLikeDe("b", "yo", darLike = true)
        assertEquals(0, nueva[0].likes)
        assertEquals(1, nueva[1].likes)
        assertTrue("yo" in nueva[1].usuariosLike)
    }

    @Test fun `quitar like descuenta y es idempotente`() {
        val lista = listOf(pub("a", likes = setOf("yo", "otra")))
        val sin = lista.conLikeDe("a", "yo", darLike = false)
        assertEquals(1, sin[0].likes)
        assertFalse("yo" in sin[0].usuariosLike)
        assertEquals(1, sin.conLikeDe("a", "yo", darLike = false)[0].likes)
    }

    @Test fun `dar like dos veces no duplica`() {
        val lista = listOf(pub("a"))
        val dos = lista.conLikeDe("a", "yo", true).conLikeDe("a", "yo", true)
        assertEquals(1, dos[0].likes)
    }

    @Test fun `vigencia semanal vence a los siete dias`() {
        val ahora = System.currentTimeMillis()
        val dia = 24L * 60 * 60 * 1000
        assertTrue(pub("a", timestamp = ahora - 6 * dia).estaVigenteSemanal(ahora))
        assertFalse(pub("b", timestamp = ahora - 7 * dia).estaVigenteSemanal(ahora))
    }
}
