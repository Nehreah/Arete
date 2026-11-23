package com.renea.arete_social.feature.auth

import com.renea.arete_social.testutils.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** Persistencia local de la última portada (para verla sin conexión). */
class GestorSesionLocalPortadaTest {

    @Before
    fun setUp() {
        GestorSesionLocal.inicializarConPrefs(FakeSharedPreferences())
    }

    @Test
    fun `sin portada guardada devuelve null`() {
        assertNull(GestorSesionLocal.obtenerUltimaPortada())
    }

    @Test
    fun `guarda y recupera la url de la portada`() {
        GestorSesionLocal.guardarUltimaPortada("file:///data/local/portada.jpg?t=1")
        assertEquals("file:///data/local/portada.jpg?t=1", GestorSesionLocal.obtenerUltimaPortada())
    }

    @Test
    fun `null o vacio borra la portada recordada`() {
        GestorSesionLocal.guardarUltimaPortada("https://x/portada.jpg")
        GestorSesionLocal.guardarUltimaPortada(null)
        assertNull(GestorSesionLocal.obtenerUltimaPortada())

        GestorSesionLocal.guardarUltimaPortada("https://x/otra.jpg")
        GestorSesionLocal.guardarUltimaPortada("  ")
        assertNull(GestorSesionLocal.obtenerUltimaPortada())
    }

    @Test
    fun `una imagen local en data url no se guarda en disco`() {
        GestorSesionLocal.guardarUltimaPortada("https://x/portada.jpg")
        GestorSesionLocal.guardarUltimaPortada("data:image/jpeg;base64,AAAA")
        assertNull(GestorSesionLocal.obtenerUltimaPortada())
    }

    @Test
    fun `cerrar sesion olvida la portada`() {
        GestorSesionLocal.guardarUltimaPortada("https://x/portada.jpg")
        GestorSesionLocal.limpiar()
        assertNull(GestorSesionLocal.obtenerUltimaPortada())
    }
}
