package com.renea.arete_social.gui.mapa.osm

import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.tileprovider.tilesource.XYTileSource
import kotlin.math.roundToInt

/** Página oficial con la licencia y la atribución que exige OpenStreetMap. */
const val URL_COPYRIGHT_OSM = "https://www.openstreetmap.org/copyright"

/**
 * Fuente de teselas OpenStreetMap Mapnik con política que permite la precarga offline
 * para navegación táctica en Aréte.
 */
val FuenteMapnik: OnlineTileSourceBase = object : XYTileSource(
    "Mapnik",
    0, 19, 256, ".png",
    arrayOf("https://tile.openstreetmap.org/"),
    "© OpenStreetMap contributors",
    TileSourcePolicy(
        2,
        TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL or TileSourcePolicy.FLAG_USER_AGENT_NORMALIZED
    )
) {}

/**
 * De dónde salen las teselas. Está detrás de una interfaz para poder cambiar de proveedor con una línea
 * (D1 del plan): MAPNIK mientras se desarrolla; uno con estilos oscuros nativos antes de la prueba pública.
 */
interface ProveedorTeselas {
    /** Texto de atribución obligatorio; lo muestra [AtribucionMapa] sobre el mapa (Fase 5). */
    val atribucion: String

    /** Enlace que se abre al tocar la atribución. Por defecto, la página de copyright de OSM. */
    val urlAtribucion: String get() = URL_COPYRIGHT_OSM

    fun fuente(tema: TemaMapa): ITileSource
}

/**
 * Servidor público de OpenStreetMap. El tema se aplica con filtros de color.
 */
object ProveedorTeselasMapnik : ProveedorTeselas {
    override val atribucion: String = "© OpenStreetMap contributors"
    override fun fuente(tema: TemaMapa): ITileSource = FuenteMapnik
}

/**
 * Ajuste de color de las teselas, como números simples (testeable sin Android).
 * Orden: invertir luminancia → saturación → `canal * escala + desplazamiento` (0–255).
 */
data class ParametrosFiltro(
    val invertir: Boolean,
    val saturacion: Float,
    val escala: Float,
    val desplazamiento: Float
) {
    /**
     * Matriz 4×5 de Android (20 valores, por filas) equivalente a los tres pasos. Se calcula aquí, en
     * Kotlin puro, y es la MISMA que recibe `ColorMatrixColorFilter`: así [aplicar] (que usan los tests de
     * calibración) y lo que se ve en pantalla no pueden divergir.
     */
    fun matriz(): FloatArray {
        // Paso 1: inversión (c' = 255 - c) como lineal 3×3 + sesgo.
        val lineal = Array(3) { f -> FloatArray(3) { c -> if (f == c) 1f else 0f } }
        val sesgo = FloatArray(3)
        if (invertir) {
            for (i in 0..2) {
                lineal[i][i] = -1f
                sesgo[i] = 255f
            }
        }
        // Paso 2: saturación con los mismos pesos de luminancia que `ColorMatrix.setSaturation`.
        val s = saturacion
        val sat = arrayOf(
            floatArrayOf(PESO_R + (1f - PESO_R) * s, PESO_G * (1f - s), PESO_B * (1f - s)),
            floatArrayOf(PESO_R * (1f - s), PESO_G + (1f - PESO_G) * s, PESO_B * (1f - s)),
            floatArrayOf(PESO_R * (1f - s), PESO_G * (1f - s), PESO_B + (1f - PESO_B) * s)
        )
        val lineal2 = Array(3) { f ->
            FloatArray(3) { c ->
                var acc = 0f
                for (k in 0..2) acc += sat[f][k] * lineal[k][c]
                acc
            }
        }
        val sesgo2 = FloatArray(3) { f ->
            var acc = 0f
            for (k in 0..2) acc += sat[f][k] * sesgo[k]
            acc
        }
        // Paso 3: escala y desplazamiento.
        val m = FloatArray(20)
        for (f in 0..2) {
            for (c in 0..2) m[f * 5 + c] = escala * lineal2[f][c]
            m[f * 5 + 4] = escala * sesgo2[f] + desplazamiento
        }
        m[18] = 1f // alfa sin cambios
        return m
    }

    /** Color resultante de pasar [rgb] (`0xRRGGBB`, el alfa se ignora) por el filtro; devuelve `0xFFRRGGBB`. */
    fun aplicar(rgb: Int): Int {
        val m = matriz()
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        fun canal(f: Int): Int =
            (m[f * 5] * r + m[f * 5 + 1] * g + m[f * 5 + 2] * b + m[f * 5 + 4]).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (canal(0) shl 16) or (canal(1) shl 8) or canal(2)
    }

    private companion object {
        const val PESO_R = 0.213f
        const val PESO_G = 0.715f
        const val PESO_B = 0.072f
    }
}

/** Apariencia de un tema: filtro (null = teselas tal cual) y colores mientras cargan las teselas. */
data class EstiloTema(
    val filtro: ParametrosFiltro?,
    val fondoCarga: Int,
    val lineaCarga: Int
)

/**
 * Equivalente a los 4 estilos JSON de Google que tenía `EstiloMapaFactory` (cartografía neutra en
 * grafito: `#202020`–`#303030` en oscuro, `#F7F7F7` en claro, para que el radar y los pines de marca
 * destaquen). CALIBRADO (Fase 5): los valores salen de pasar los colores estándar de MAPNIK por el
 * filtro y compararlos con los hex de esos estilos (ver `CalibracionTemaTest`):
 *
 * | Tema          | Tierra MAPNIK `#F2EFE9` queda en | Objetivo (Google) |
 * |---------------|----------------------------------|-------------------|
 * | CLARO_DIA     | `#F1F1F0`                        | `#F0F0F0`         |
 * | CLARO_NOCHE   | `#E9E8E8`                        | `#E8E8E8`         |
 * | OSCURO_DIA    | `#303030`                        | `#303030`         |
 * | OSCURO_NOCHE  | `#252525`                        | `#252525`         |
 *
 * Todos los temas desaturan casi por completo: así el verde de los parques de MAPNIK no compite con el
 * pin verde de los lugares ni el azul del agua con el pin del amigo.
 *
 * Límite conocido de un filtro (no de la calibración): al invertir, las calles blancas quedan algo
 * MÁS oscuras que la tierra (con borde más claro) y el agua algo más clara. Se arregla de raíz con un
 * proveedor de estilos oscuros nativos (D2-2).
 */
object EstilosTemaMapa {
    fun de(tema: TemaMapa): EstiloTema = when (tema) {
        TemaMapa.CLARO_DIA -> EstiloTema(
            ParametrosFiltro(invertir = false, saturacion = 0.10f, escala = 0.91f, desplazamiento = 23f),
            0xFFF0F0F0.toInt(), 0xFFDADADA.toInt()
        )
        TemaMapa.CLARO_NOCHE -> EstiloTema(
            ParametrosFiltro(invertir = false, saturacion = 0.10f, escala = 0.80f, desplazamiento = 41f),
            0xFFE8E8E8.toInt(), 0xFFD0D0D0.toInt()
        )
        TemaMapa.OSCURO_DIA -> EstiloTema(
            ParametrosFiltro(invertir = true, saturacion = 0.05f, escala = 0.82f, desplazamiento = 35f),
            0xFF303030.toInt(), 0xFF3A3A3A.toInt()
        )
        TemaMapa.OSCURO_NOCHE -> EstiloTema(
            ParametrosFiltro(invertir = true, saturacion = 0.10f, escala = 0.82f, desplazamiento = 24f),
            0xFF252525.toInt(), 0xFF2E2E2E.toInt()
        )
    }
}

/** Convierte [ParametrosFiltro] en el filtro de Android (la matriz sale de [ParametrosFiltro.matriz]). */
internal fun ParametrosFiltro.aColorFilter(): ColorFilter = ColorMatrixColorFilter(ColorMatrix(matriz()))

// ---- Proveedores adicionales de capas ----

/**
 * OpenTopoMap: teselas con curvas de nivel, elevación y senderos de montaña.
 * Servidor público, política de uso razonable. Se guarda en la misma caché local que MAPNIK.
 * No aplica filtro de color: las teselas ya tienen su propio estilo cartográfico.
 */
object ProveedorTeselasTopoMap : ProveedorTeselas {
    override val atribucion: String = "© OpenTopoMap (CC-BY-SA) | © OpenStreetMap contributors"

    override fun fuente(tema: TemaMapa): ITileSource =
        org.osmdroid.tileprovider.tilesource.XYTileSource(
            "OpenTopoMap",
            /* minZoom */ 0, /* maxZoom */ 17,
            /* tileSizePixels */ 256,
            /* fileExtension */ ".png",
            /* baseUrls */ arrayOf(
                "https://a.tile.opentopomap.org/",
                "https://b.tile.opentopomap.org/",
                "https://c.tile.opentopomap.org/"
            ),
            "© OpenTopoMap (CC-BY-SA)",
            TileSourcePolicy(
                2,
                TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL or TileSourcePolicy.FLAG_USER_AGENT_NORMALIZED
            )
        )
}

/**
 * Capa satelital real de alta resolución global (Esri World Imagery).
 * Fotografía aérea y satelital verdadera disponible mundialmente hasta zoom 19.
 */
object ProveedorTeselasSatelite : ProveedorTeselas {
    override val atribucion: String = "© Esri, Maxar, Earthstar Geographics"
    override val urlAtribucion: String = "https://www.esri.com/en-us/legal/copyright-trademarks"

    private val fuenteSatelite: OnlineTileSourceBase = object : OnlineTileSourceBase(
        "EsriWorldImagery",
        0, 19, 256, "",
        arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
        "© Esri, Maxar, Earthstar Geographics",
        TileSourcePolicy(
            2,
            TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL or TileSourcePolicy.FLAG_USER_AGENT_NORMALIZED
        )
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = org.osmdroid.util.MapTileIndex.getZoom(pMapTileIndex)
            val x = org.osmdroid.util.MapTileIndex.getX(pMapTileIndex)
            val y = org.osmdroid.util.MapTileIndex.getY(pMapTileIndex)
            return "$baseUrl$z/$y/$x"
        }
    }

    override fun fuente(tema: TemaMapa): ITileSource = fuenteSatelite
}

/**
 * Resuelve qué [ProveedorTeselas] y qué [TemaMapa] usar según la [CapaMapa] seleccionada
 * y el tema visual actual (esOscuro / ciclo).
 *
 * - ESTANDAR: MAPNIK + tema claro/oscuro normal.
 * - OSCURO:   MAPNIK + tema oscuro forzado (independientemente del tema del sistema).
 * - TERRENO:  OpenTopoMap + tema CLARO_DIA (el topo ya tiene su propio estilo).
 * - SATELITE: Esri World Imagery (fotografía satelital real de alta resolución).
 */
fun resolverProveedorYTema(
    capa: CapaMapa,
    esOscuro: Boolean,
    ciclo: com.renea.arete_social.gui.theme.CicloDia
): Pair<ProveedorTeselas, TemaMapa> = when (capa) {
    CapaMapa.ESTANDAR  -> ProveedorTeselasMapnik to TemaMapa.resolver(esOscuro, ciclo)
    CapaMapa.OSCURO    -> ProveedorTeselasMapnik to TemaMapa.OSCURO_NOCHE
    CapaMapa.TERRENO   -> ProveedorTeselasTopoMap to TemaMapa.CLARO_DIA
    CapaMapa.SATELITE  -> ProveedorTeselasSatelite to TemaMapa.CLARO_DIA
}

/**
 * Extiende [EstilosTemaMapa.de] para capas con estilo nativo propio (Satélite, Terreno).
 * Para ellas no aplica filtros de color destructivos, conservando sus colores naturales.
 */
fun estiloParaCapa(proveedor: ProveedorTeselas, tema: TemaMapa): EstiloTema = when (proveedor) {
    is ProveedorTeselasSatelite -> EstiloTema(
        filtro       = null,
        fondoCarga   = 0xFF121815.toInt(),
        lineaCarga   = 0xFF233028.toInt()
    )
    is ProveedorTeselasTopoMap -> EstiloTema(
        filtro       = null,
        fondoCarga   = 0xFFF0F0F0.toInt(),
        lineaCarga   = 0xFFDADADA.toInt()
    )
    else -> EstilosTemaMapa.de(tema)
}
