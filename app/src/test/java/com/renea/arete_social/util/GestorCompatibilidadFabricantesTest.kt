package com.renea.arete_social.util

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestorCompatibilidadFabricantesTest {

    @Test
    fun verificarDeteccionDeFabricantes() {
        assertTrue(GestorCompatibilidadFabricantes.esXiaomiOPoco("Xiaomi", "Redmi"))
        assertTrue(GestorCompatibilidadFabricantes.esXiaomiOPoco("POCO", "POCO"))
        assertTrue(GestorCompatibilidadFabricantes.esSamsung("Samsung", "samsung"))
        assertTrue(GestorCompatibilidadFabricantes.esMotorola("motorola", "moto"))

        assertFalse(GestorCompatibilidadFabricantes.esXiaomiOPoco("Google", "Pixel"))
        assertFalse(GestorCompatibilidadFabricantes.esSamsung("OnePlus", "OnePlus"))
    }
}
