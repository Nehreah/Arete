package com.renea.arete_social.gui.navigation

import androidx.compose.runtime.mutableStateListOf

/**
 * Gestor reactivo de historial LIFO de pestañas de navegación.
 * Permite que la navegación con retroceso del sistema (gesto o botón Back)
 * vuelva secuencialmente por las pestañas visitadas antes de salir de la app.
 */
class HistorialPantallas(
    inicial: TabNavegacion = TabNavegacion.MAPA
) : HistorialNavegable {

    private val pila = mutableStateListOf(inicial)

    override val actual: TabNavegacion
        get() = pila.lastOrNull() ?: TabNavegacion.MAPA

    override fun visitar(destino: TabNavegacion) {
        if (pila.isEmpty() || destino != pila.last()) {
            pila.add(destino)
        }
    }

    override fun volver(): Boolean {
        return if (pila.size > 1) {
            pila.removeAt(pila.lastIndex)
            true
        } else {
            false
        }
    }
}
