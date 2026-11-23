package com.renea.arete_social.gui.mapa.osm

import com.renea.arete_social.gui.theme.CicloDia

/**
 * Tipos propios del mapa. El resto de la app habla SOLO en estos términos: ninguna pantalla
 * importa `org.osmdroid` (ni `com.google...`), así cambiar de motor de mapa afecta a este paquete.
 */

/**
 * Coordenada geográfica en grados. Sustituye a `LatLng` / `GeoPoint` fuera de `gui/mapa/osm`.
 *
 * @param precisionMetros Radio de incertidumbre en metros (solo relevante para la ubicación
 *   propia del usuario; nulo en lugares, eventos y posiciones de otros usuarios).
 */
data class PuntoMapa(
    val latitud: Double,
    val longitud: Double,
    val precisionMetros: Float? = null
)

/** Color del pin (ARGB). Equivale a los antiguos `BitmapDescriptorFactory.HUE_*`. */
enum class ColorPin(val argb: Int) {
    LUGAR(0xFF34C759.toInt()),
    SUPER(0xFFFFD60A.toInt()),
    EVENTO(0xFFAF52DE.toInt()),
    AMIGO(0xFF0A84FF.toInt())
}

/**
 * Marcador declarativo. Es una `data class` de valores simples (sin lambdas) para poder comparar
 * la lista anterior con la nueva y tocar solo lo que cambió. El toque llega por `onClickMarcador(id)`.
 *
 * @param abreInfo si es true, al tocarlo se muestra el globo de OSMDroid con [titulo] y
 * [descripcion] (el caso del marcador del amigo en un encuentro). Si es false el toque se
 * entrega a `onClickMarcador` y no se muestra globo (comportamiento actual de lugares y eventos).
 */
data class MarcadorMapa(
    val id: String,
    val posicion: PuntoMapa,
    val color: ColorPin,
    val titulo: String? = null,
    val descripcion: String? = null,
    val abreInfo: Boolean = false
)

/** Polilínea declarativa (ruta de encuentro). [punteada] = línea recta de respaldo. */
data class LineaMapa(
    val id: String,
    val puntos: List<PuntoMapa>,
    val colorArgb: Int,
    val anchoDp: Float = 4f,
    val punteada: Boolean = false
)

/** Combinación de tema claro/oscuro y ciclo día/noche (las 4 variantes que tenía `EstiloMapaFactory`). */
enum class TemaMapa {
    CLARO_DIA, CLARO_NOCHE, OSCURO_DIA, OSCURO_NOCHE;

    companion object {
        fun resolver(esOscuro: Boolean, ciclo: CicloDia): TemaMapa = when {
            esOscuro && ciclo == CicloDia.NOCHE -> OSCURO_NOCHE
            esOscuro -> OSCURO_DIA
            ciclo == CicloDia.NOCHE -> CLARO_NOCHE
            else -> CLARO_DIA
        }
    }
}

/**
 * Capa del mapa que el usuario puede seleccionar con [SelectorCapaMapa].
 *
 * - [ESTANDAR]: MAPNIK desaturado (el estilo actual).
 * - [OSCURO]: MAPNIK invertido (el filtro oscuro que ya teníamos).
 * - [TERRENO]: OpenTopoMap — curvas de nivel, montañas, senderos.
 * - [SATURADO]: MAPNIK con saturación alta para distinguir zonas verdes, agua y calles a color.
 */
enum class CapaMapa(val etiqueta: String, val icono: String) {
    ESTANDAR("Estándar", "🗺️"),
    OSCURO("Oscuro",    "🌑"),
    TERRENO("Terreno",  "⛰️"),
    SATURADO("Saturado", "🛰️")
}
