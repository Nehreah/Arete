package com.renea.arete_social.data.local.ajustes

import android.content.Context
import android.content.ContextWrapper
import com.renea.arete_social.testutils.FakeSharedPreferences
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AjustesRedTest {

    private val dummyContext: Context = ContextWrapper(null)
    private lateinit var fakePrefs: FakeSharedPreferences

    @Before
    fun setup() {
        fakePrefs = FakeSharedPreferences()
        AjustesRed.prefsOverride = fakePrefs
    }

    @After
    fun tearDown() {
        AjustesRed.prefsOverride = null
    }

    @Test
    fun soloTextoEnDatosMoviles_activoPorDefecto() {
        val activoPorDefecto = AjustesRed.soloTextoEnDatosMoviles(dummyContext)
        assertTrue("El ahorro de datos móviles debe estar activo por defecto", activoPorDefecto)
    }

    @Test
    fun definirSoloTextoEnDatosMoviles_actualizaPreferencia() {
        AjustesRed.definirSoloTextoEnDatosMoviles(dummyContext, false)
        assertFalse(AjustesRed.soloTextoEnDatosMoviles(dummyContext))

        AjustesRed.definirSoloTextoEnDatosMoviles(dummyContext, true)
        assertTrue(AjustesRed.soloTextoEnDatosMoviles(dummyContext))
    }

    @Test
    fun obtenerRelaysConfigurados_retornaValoresPorDefectoSiEstaVacio() {
        val relays = AjustesRed.obtenerRelaysConfigurados(dummyContext)
        assertTrue(relays.isNotEmpty())
        assertTrue(relays.any { it.contains("damus.io") || it.contains("nos.lol") })
    }

    @Test
    fun agregarYEliminarRelay_persisteCorrectamente() {
        val nuevoRelay = "https://mi-nodo-relay.com"
        AjustesRed.agregarRelay(dummyContext, nuevoRelay)
        var relays = AjustesRed.obtenerRelaysConfigurados(dummyContext)
        assertTrue(relays.contains(nuevoRelay))

        AjustesRed.eliminarRelay(dummyContext, nuevoRelay)
        relays = AjustesRed.obtenerRelaysConfigurados(dummyContext)
        assertFalse(relays.contains(nuevoRelay))
    }
}

