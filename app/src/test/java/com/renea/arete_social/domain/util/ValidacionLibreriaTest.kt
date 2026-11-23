package com.renea.arete_social.domain.util

import com.renea.arete_social.domain.model.ArchivoPdf
import com.renea.arete_social.domain.model.LimitesCarta
import com.renea.arete_social.domain.model.LimitesLibreria
import com.renea.arete_social.domain.model.TipoObra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidacionLibreriaTest {

    @Test fun `enlace vacio es valido y se ignora`() =
        assertEquals(EnlaceValidado.Vacio, validarEnlace("   "))

    @Test fun `enlace sin esquema recibe https`() =
        assertEquals(EnlaceValidado.Valido("https://poemas.org/luna"), validarEnlace(" poemas.org/luna "))

    @Test fun `esquemas peligrosos se rechazan`() {
        listOf("javascript:alert(1)", "file:///etc/passwd", "intent://x#Intent;end", "mailto:a@b.co", "ftp://sitio.com")
            .forEach { assertEquals(it, EnlaceValidado.Invalido, validarEnlace(it)) }
    }

    @Test fun `host sin punto o enlace larguisimo es invalido`() {
        assertEquals(EnlaceValidado.Invalido, validarEnlace("localhost"))
        assertEquals(EnlaceValidado.Invalido, validarEnlace("https://a.co/" + "x".repeat(LimitesLibreria.MAX_ENLACE)))
    }

    @Test fun `obra necesita titulo`() {
        val r = validarNuevaObra(TipoObra.CUENTO, " ", "texto", "", null)
        assertTrue(r is ResultadoObra.Error)
    }

    @Test fun `obra necesita texto o pdf`() {
        assertTrue(validarNuevaObra(TipoObra.POEMARIO, "Titulo", "  ", "", null) is ResultadoObra.Error)
        val conPdf = validarNuevaObra(TipoObra.POEMARIO, "Titulo", "", "", ArchivoPdf("a.pdf", ByteArray(10)))
        assertTrue(conPdf is ResultadoObra.Correcta)
    }

    @Test fun `pdf demasiado grande se rechaza`() {
        val grande = ArchivoPdf("a.pdf", ByteArray(LimitesLibreria.MAX_PDF_BYTES + 1))
        assertTrue(validarNuevaObra(TipoObra.CUENTO, "T", "x", "", grande) is ResultadoObra.Error)
    }

    @Test fun `obra correcta limpia espacios y normaliza enlace`() {
        val r = validarNuevaObra(TipoObra.CUENTO, "  Mi cuento ", " Erase una vez ", "cuentos.com", null)
        val obra = (r as ResultadoObra.Correcta).obra
        assertEquals("Mi cuento", obra.titulo)
        assertEquals("Erase una vez", obra.contenido)
        assertEquals("https://cuentos.com", obra.enlace)
    }

    @Test fun `carta vacia o demasiado larga no se envia`() {
        assertNull(validarTextoCarta("  ").first)
        assertNull(validarTextoCarta("x".repeat(LimitesCarta.MAX_CARACTERES + 1)).first)
        assertEquals("Hola", validarTextoCarta("  Hola ").first)
    }
}
