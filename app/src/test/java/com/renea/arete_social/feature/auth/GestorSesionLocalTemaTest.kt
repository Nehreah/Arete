package com.renea.arete_social.feature.auth

import com.renea.arete_social.gui.theme.ModoTema
import com.renea.arete_social.testutils.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Pruebas unitarias de GestorSesionLocal — persistencia de ModoTema.
 * Utiliza [FakeSharedPreferences] sin dependencias externas ni runtime de Android.
 */
class GestorSesionLocalTemaTest {

    private lateinit var fakePrefs: FakeSharedPreferences

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        GestorSesionLocal.inicializarConPrefs(fakePrefs)
    }

    @Test
    fun `guardarModoTema CLARO persiste y recupera correctamente`() {
        GestorSesionLocal.guardarModoTema(ModoTema.CLARO)
        val resultado = GestorSesionLocal.obtenerModoTema()
        assertEquals(ModoTema.CLARO, resultado)
    }

    @Test
    fun `guardarModoTema OSCURO persiste y recupera correctamente`() {
        GestorSesionLocal.guardarModoTema(ModoTema.OSCURO)
        val resultado = GestorSesionLocal.obtenerModoTema()
        assertEquals(ModoTema.OSCURO, resultado)
    }

    @Test
    fun `obtenerModoTema devuelve null cuando no hay preferencia guardada`() {
        val resultado = GestorSesionLocal.obtenerModoTema()
        assertNull(resultado)
    }

    @Test
    fun `obtenerModoTema devuelve null para valor corrupto o desconocido del enum`() {
        fakePrefs.edit().putString("modo_tema_comunidad", "VALOR_INVALIDO").apply()
        val resultado = GestorSesionLocal.obtenerModoTema()
        assertNull("Debe ignorar valores no reconocidos en el enum", resultado)
    }

    @Test
    fun `sobrescribir ModoTema actualiza el valor persistido`() {
        GestorSesionLocal.guardarModoTema(ModoTema.CLARO)
        assertEquals(ModoTema.CLARO, GestorSesionLocal.obtenerModoTema())

        GestorSesionLocal.guardarModoTema(ModoTema.OSCURO)
        assertEquals(ModoTema.OSCURO, GestorSesionLocal.obtenerModoTema())
    }
}
