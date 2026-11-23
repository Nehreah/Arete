package com.renea.arete_social.gui.theme

/**
 * Configuración del sistema de diseño moderno de Aréte.
 *
 * NUEVO_DISENO activa la interfaz modernizada (fondo plano, jerarquía tipográfica,
 * sin bordes decorativos innecesarios). Mientras sea false, los componentes usan
 * el estilo actual sin cambios visibles.
 *
 * Ciclo de migración:
 *   1. Implementar componente/pantalla bajo `if (DisenoConfig.NUEVO_DISENO)`.
 *   2. Validar en ambos temas (claro y oscuro) y en la combinación día/noche.
 *   3. Una vez que todas las pantallas pasen QA, activar por defecto y luego
 *      eliminar la rama `else` y el flag.
 *
 * No usar este flag para lógica de negocio — solo para presentación visual.
 */
object DisenoConfig {
    /**
     * Activa el nuevo sistema de diseño M3.
     * false → estilo anterior (sin cambios visibles para el usuario).
     * true  → paleta nueva, fondos planos, sin tarjetas redundantes.
     *
     * Cambia a `true` pantalla por pantalla durante la Fase 5 (Reforma Visual),
     * y solo después de haber validado ambos temas y el ciclo día/noche.
     */
    const val NUEVO_DISENO: Boolean = true
}
