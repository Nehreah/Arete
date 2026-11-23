package com.renea.arete_social.data.local.perfil

import android.content.Context
import android.content.SharedPreferences
import com.renea.arete_social.domain.model.PerfilIntereses
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Gestor persistente en SharedPreferences para almacenar y recuperar los gustos e intereses del usuario.
 * Sincroniza en tiempo real los intereses y emite cambios a través de [intereses].
 */
object GestorInteresesUsuario {

    private const val PREFS_NAME = "antired_intereses_prefs"
    private const val KEY_INTERESES = "perfil_intereses_data"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var prefs: SharedPreferences? = null

    private val _intereses = MutableStateFlow(PerfilIntereses())
    val intereses: StateFlow<PerfilIntereses> = _intereses.asStateFlow()

    fun inicializar(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            cargar()
        }
    }

    private fun cargar() {
        val raw = prefs?.getString(KEY_INTERESES, null)
        if (!raw.isNullOrBlank()) {
            runCatching {
                val guardado = json.decodeFromString<PerfilIntereses>(raw)
                _intereses.value = guardado
            }
        }
    }

    fun alternarCategoria(categoriaId: String) {
        val actuales = _intereses.value.categoriasSeleccionadas.toMutableSet()
        if (actuales.contains(categoriaId)) {
            actuales.remove(categoriaId)
        } else {
            actuales.add(categoriaId)
        }
        actualizar(_intereses.value.copy(categoriasSeleccionadas = actuales))
    }

    fun alternarSubcategoria(subcategoriaId: String) {
        val actuales = _intereses.value.subcategoriasSeleccionadas.toMutableSet()
        if (actuales.contains(subcategoriaId)) {
            actuales.remove(subcategoriaId)
        } else {
            actuales.add(subcategoriaId)
        }
        actualizar(_intereses.value.copy(subcategoriasSeleccionadas = actuales))
    }

    fun agregarGustoPersonalizado(gusto: String) {
        if (gusto.isBlank()) return
        val lista = _intereses.value.gustosPersonalizados.toMutableList()
        if (!lista.contains(gusto.trim())) {
            lista.add(gusto.trim())
            actualizar(_intereses.value.copy(gustosPersonalizados = lista))
        }
    }

    fun eliminarGustoPersonalizado(gusto: String) {
        val lista = _intereses.value.gustosPersonalizados.toMutableList()
        if (lista.remove(gusto.trim())) {
            actualizar(_intereses.value.copy(gustosPersonalizados = lista))
        }
    }

    private fun actualizar(nuevoPerfil: PerfilIntereses) {
        _intereses.value = nuevoPerfil
        runCatching {
            val raw = json.encodeToString(nuevoPerfil)
            prefs?.edit()?.putString(KEY_INTERESES, raw)?.apply()
        }
    }
}
