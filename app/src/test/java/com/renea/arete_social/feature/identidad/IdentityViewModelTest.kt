package com.renea.arete_social.feature.identidad

import com.renea.arete_social.data.identity.IdentityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IdentityViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var identityManager: IdentityManager
    private lateinit var viewModel: IdentityViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        identityManager = IdentityManager()
        viewModel = IdentityViewModel(identityManager, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testEstadoInicialEsVacio() {
        assertTrue(viewModel.estado.value is IdentidadUiState.Vacio)
    }

    @Test
    fun testGenerarNuevaIdentidadEmiteCargadaConPalabras() = runTest(testDispatcher) {
        viewModel.generarNuevaIdentidad()
        advanceUntilIdle()

        val estado = viewModel.estado.value
        assertTrue("El estado debe ser Cargada tras generar", estado is IdentidadUiState.Cargada)
        val cargada = estado as IdentidadUiState.Cargada
        assertEquals(12, cargada.palabras.size)
        assertTrue(cargada.ikPublicHex.isNotEmpty())
        assertEquals(130, cargada.ikPublicHex.length) // 65 bytes = 130 hex chars (04 || X || Y)

        // Al confirmar que se anotó en papel, las palabras se borran del estado UI
        viewModel.confirmarFraseAnotada()
        val estadoSinPalabras = viewModel.estado.value as IdentidadUiState.Cargada
        assertTrue(estadoSinPalabras.palabras.isEmpty())
        assertEquals(cargada.ikPublicHex, estadoSinPalabras.ikPublicHex)
    }

    @Test
    fun testRestaurarDesdeFraseInvalidaEmiteError() = runTest(testDispatcher) {
        viewModel.restaurarDesde(listOf("palabra1", "palabra2")) // Menos de 12
        advanceUntilIdle()

        val estado = viewModel.estado.value
        assertTrue("Debe fallar si no son 12 palabras", estado is IdentidadUiState.Error)
    }

    @Test
    fun testRestaurarYOlvidarIdentidad() = runTest(testDispatcher) {
        // Generamos una primera vez para obtener palabras válidas BIP39
        val palabrasGeneradas = identityManager.generateAndLoad()
        val ikOriginal = identityManager.ikPublicBytes.joinToString("") { "%02x".format(it) }

        // Creamos nuevo manager y VM limpio
        val cleanManager = IdentityManager()
        val cleanVm = IdentityViewModel(cleanManager, testDispatcher)

        cleanVm.restaurarDesde(palabrasGeneradas)
        advanceUntilIdle()

        val estado = cleanVm.estado.value
        assertTrue(estado is IdentidadUiState.Cargada)
        val cargada = estado as IdentidadUiState.Cargada
        assertEquals(ikOriginal, cargada.ikPublicHex)
        assertTrue("Al restaurar no se exponen las palabras", cargada.palabras.isEmpty())

        // Olvidar identidad resetea a Vacio
        cleanVm.olvidarIdentidad()
        assertTrue(cleanVm.estado.value is IdentidadUiState.Vacio)
        assertTrue(!cleanManager.isLoaded)
    }
}
