package com.renea.arete_social.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VisibilidadEventoTest {
    @Test
    fun `evento creado desde comunidad puede ser publico para todos`() {
        assertEquals(VisibilidadEvento.PUBLICO, VisibilidadEvento.efectiva("comunidad-1", VisibilidadEvento.PUBLICO))
    }

    @Test
    fun `evento creado desde comunidad puede ser privado para miembros`() {
        assertEquals(VisibilidadEvento.COMUNIDAD, VisibilidadEvento.efectiva("comunidad-1", VisibilidadEvento.COMUNIDAD))
        assertEquals(VisibilidadEvento.COMUNIDAD, VisibilidadEvento.efectiva("comunidad-1", VisibilidadEvento.AMIGOS))
    }

    @Test
    fun `evento personal conserva publico o privado`() {
        assertEquals(VisibilidadEvento.PUBLICO, VisibilidadEvento.efectiva(null, VisibilidadEvento.PUBLICO))
        assertEquals(VisibilidadEvento.AMIGOS, VisibilidadEvento.efectiva(null, VisibilidadEvento.AMIGOS))
        assertEquals(VisibilidadEvento.PUBLICO, VisibilidadEvento.efectiva("", VisibilidadEvento.PUBLICO))
    }

    @Test
    fun `evento personal nunca queda como COMUNIDAD`() {
        assertEquals(VisibilidadEvento.AMIGOS, VisibilidadEvento.efectiva(null, VisibilidadEvento.COMUNIDAD))
    }
}
