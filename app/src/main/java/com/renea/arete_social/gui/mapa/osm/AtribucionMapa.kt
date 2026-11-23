package com.renea.arete_social.gui.mapa.osm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Colores (ARGB) de la etiqueta de atribución. Se separa del composable para poder probarlo en JVM. */
internal data class PaletaAtribucion(val fondo: Int, val texto: Int)

/** Etiqueta clara sobre fondo oscuro en los temas oscuros, y al revés; fondo semitransparente para que se lea sobre cualquier tesela. */
internal fun paletaAtribucion(tema: TemaMapa): PaletaAtribucion = when (tema) {
    TemaMapa.OSCURO_DIA, TemaMapa.OSCURO_NOCHE -> PaletaAtribucion(fondo = 0xB3202020.toInt(), texto = 0xFFE8E8E8.toInt())
    TemaMapa.CLARO_DIA, TemaMapa.CLARO_NOCHE -> PaletaAtribucion(fondo = 0xB3FFFFFF.toInt(), texto = 0xFF292929.toInt())
}

/**
 * Atribución obligatoria de los datos del mapa ("© OpenStreetMap contributors"), visible y enlazada
 * a la página de copyright del proveedor. Va como capa Compose por encima del [OsmMapa], en una
 * esquina libre; no cambia con el giro del mapa porque no es un overlay de OSMDroid.
 *
 * Ubicación recomendada: `Alignment.BottomStart` con ~4 dp de margen inferior (queda por debajo del
 * panel de encuentro y de los botones flotantes, que empiezan a 24 dp del borde).
 */
@Composable
fun AtribucionMapa(
    tema: TemaMapa,
    modifier: Modifier = Modifier,
    proveedor: ProveedorTeselas = ProveedorTeselasMapnik
) {
    val manejadorUri = LocalUriHandler.current
    val paleta = paletaAtribucion(tema)
    Text(
        text = proveedor.atribucion,
        color = Color(paleta.texto),
        fontSize = 10.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textDecoration = TextDecoration.Underline,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(paleta.fondo))
            .clickable(role = Role.Button, onClickLabel = "Abrir información de copyright del mapa") {
                // Sin navegador instalado openUri lanza ActivityNotFoundException: no debe tumbar la app.
                runCatching { manejadorUri.openUri(proveedor.urlAtribucion) }
            }
            .padding(horizontal = 6.dp, vertical = 3.dp)
    )
}
