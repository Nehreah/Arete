package com.renea.arete_social.gui.navigation

/**
 * Abstracción de navegación por historial LIFO para pestañas principales.
 * Sigue el principio de Inversión de Dependencias (DIP) del plan v3.
 */
interface HistorialNavegable {
    val actual: TabNavegacion
    fun visitar(destino: TabNavegacion)
    fun volver(): Boolean
}
