package com.renea.arete_social.gui.mapa

import com.renea.arete_social.gui.mapa.osm.TemaMapa
import com.renea.arete_social.gui.theme.CicloDia

/**
 * Punto único para elegir el estilo visual del mapa según el tema claro/oscuro y el ciclo día/noche.
 *
 * Tras la migración a OSMDroid (Fase 5) ya no contiene los 4 JSON de Google Maps: la decisión vive en
 * [TemaMapa.resolver] y la apariencia concreta (filtro de color, colores de carga) en
 * `EstilosTemaMapa`. Se conserva este objeto para que los documentos y el código que hablan de
 * `EstiloMapaFactory` sigan teniendo un sitio donde mirar, sin importar ningún tipo de Google ni de OSMDroid.
 */
object EstiloMapaFactory {
    fun crearTema(esOscuro: Boolean, cicloDia: CicloDia): TemaMapa = TemaMapa.resolver(esOscuro, cicloDia)
}
