package com.renea.arete_social.feature.miausnake.ui.maps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renea.arete_social.feature.miausnake.core.Side
import com.renea.arete_social.feature.miausnake.levels.GameMode
import com.renea.arete_social.feature.miausnake.levels.LevelCatalog
import com.renea.arete_social.feature.miausnake.levels.LevelRecord
import com.renea.arete_social.feature.miausnake.levels.LevelSpec
import com.renea.arete_social.feature.miausnake.levels.MapPacks
import com.renea.arete_social.feature.miausnake.levels.Medal
import com.renea.arete_social.feature.miausnake.levels.ProgressBook
import com.renea.arete_social.feature.miausnake.levels.formatTime
import com.renea.arete_social.feature.miausnake.ui.theme.GamePalette

private const val COLUMNS = 2

/**
 * Selector de mapas por packs: interfaz moderna con filtro por paquetes y tarjetas estilizadas.
 * Los 20 mapas están disponibles desde el principio..
 */
@Composable
fun MapSelector(
    catalog: LevelCatalog,
    book: ProgressBook,
    mode: GameMode,
    selectedId: String?,
    palette: GamePalette,
    onSelect: (LevelSpec) -> Unit,
    modifier: Modifier = Modifier
) {
    val initialPack = remember(selectedId) {
        selectedId?.let { id ->
            val num = id.substringAfterLast('_').toIntOrNull() ?: 1
            ((num - 1) / 5) + 1
        } ?: 1
    }
    var packSeleccionado by remember(initialPack) {
        mutableIntStateOf(initialPack.coerceIn(1, MapPacks.COUNT))
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Selector de packs en fila horizontal
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (p in 1..MapPacks.COUNT) {
                val activo = p == packSeleccionado
                val shape = RoundedCornerShape(10.dp)
                val goldCount = catalog.pack(p).count { book.medal(it) == Medal.GOLD }
                Box(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(shape)
                        .border(1.dp, if (activo) palette.food else palette.wallEdge.copy(alpha = 0.4f), shape)
                        .background(if (activo) palette.panel else palette.background.copy(alpha = 0.4f))
                        .clickable(role = Role.Tab, onClickLabel = "Pack $p") { packSeleccionado = p },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CardText(
                            "PACK $p",
                            if (activo) palette.food else palette.text,
                            12,
                            bold = true
                        )
                        if (mode == GameMode.META && goldCount > 0) {
                            CardText("★ $goldCount", palette.food, 12, bold = true)
                        } else {
                            CardText(MapPacks.title(p), palette.textDim, 12)
                        }
                    }
                }
            }
        }

        val specs = catalog.pack(packSeleccionado)
        val packTitle = MapPacks.title(packSeleccionado).uppercase()
        val edgeTitle = MapPacks.edgeTitle(packSeleccionado)
        val orosPack = specs.count { book.medal(it) == Medal.GOLD }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CardText("PACK $packSeleccionado: $packTitle · $edgeTitle", palette.textDim, 12, bold = true)
            if (mode == GameMode.META) {
                CardText("Oros: $orosPack/${specs.size}", palette.food, 12, bold = true)
            }
        }

        // Cuadrícula de 2 columnas para que las miniaturas sean grandes y nítidas
        for (row in specs.chunked(COLUMNS)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (spec in row) {
                    MapCard(
                        spec = spec,
                        record = book.get(spec, mode),
                        medal = book.medal(spec),
                        mode = mode,
                        selected = spec.id == selectedId,
                        palette = palette,
                        onClick = { onSelect(spec) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun MapCard(
    spec: LevelSpec,
    record: LevelRecord?,
    medal: Medal,
    mode: GameMode,
    selected: Boolean,
    palette: GamePalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = when (mode) {
        GameMode.META -> if (medal == Medal.NONE) "Sin medalla" else medal.label
        GameMode.INFINITE -> "Récord: ${record?.bestScore ?: 0}"
    }
    val description = "Mapa ${spec.number}, ${spec.name}, ${spec.edgeLabel()}, dificultad ${spec.difficulty} de 5, $status"
    val shape = RoundedCornerShape(14.dp)
    val borderModifier = if (selected) {
        Modifier.border(2.dp, palette.food, shape)
    } else {
        Modifier.border(1.dp, palette.wallEdge.copy(alpha = 0.45f), shape)
    }

    Column(
        modifier
            .clip(shape)
            .then(borderModifier)
            .background(if (selected) palette.panel else palette.panel.copy(alpha = 0.7f))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = description
                this.selected = selected
            }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(spec.cols.toFloat() / spec.rows.toFloat())
                .clip(RoundedCornerShape(8.dp))
        ) {
            LevelThumbnail(spec, palette, Modifier.fillMaxSize())
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.food)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    CardText("ELEGIDO", palette.background, 12, bold = true)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CardText("${spec.number.toString().padStart(2, '0')}. ${spec.name}", palette.text, 13, bold = true)
            CardText("★".repeat(spec.difficulty), palette.food, 12)
        }
        Spacer(Modifier.height(2.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val statusColor = if (medal == Medal.GOLD) palette.food else palette.textDim
            CardText(
                when (mode) {
                    GameMode.META -> status + (record?.bestTimeMs?.let { " · ${formatTime(it)}" } ?: "")
                    GameMode.INFINITE -> status
                },
                statusColor,
                12
            )
        }
    }
}

@Composable
private fun CardText(text: String, color: Color, size: Int, bold: Boolean = false) {
    BasicText(
        text,
        style = TextStyle(color = color, fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal),
        maxLines = 1
    )
}

/**
 * Miniatura dibujada con los DATOS del mapa (paredes, bordes, túneles y spawn): nunca hay que
 * mantener una imagen aparte. Borde continuo = mata; punteado = envuelve; marca amarilla = túnel.
 */
@Composable
fun LevelThumbnail(spec: LevelSpec, palette: GamePalette, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cell = minOf(size.width / spec.cols, size.height / spec.rows)
        val bw = cell * spec.cols
        val bh = cell * spec.rows
        val ox = (size.width - bw) / 2f
        val oy = (size.height - bh) / 2f
        drawRect(palette.floorB, Offset(ox, oy), Size(bw, bh))
        for (w in spec.walls) {
            drawRect(palette.wallEdge, Offset(ox + w.x * cell, oy + w.y * cell), Size(cell, cell))
        }

        val stroke = maxOf(1.5f, cell * 0.7f)
        val dash = PathEffect.dashPathEffect(floatArrayOf(cell * 1.6f, cell * 1.6f))
        fun sideLine(side: Side, a: Offset, b: Offset) {
            val open = spec.sideIsWrap(side)
            drawLine(
                color = if (open) palette.frameWrap else palette.frameSolid,
                start = a, end = b, strokeWidth = stroke,
                pathEffect = if (open) dash else null
            )
        }
        sideLine(Side.TOP, Offset(ox, oy), Offset(ox + bw, oy))
        sideLine(Side.BOTTOM, Offset(ox, oy + bh), Offset(ox + bw, oy + bh))
        sideLine(Side.LEFT, Offset(ox, oy), Offset(ox, oy + bh))
        sideLine(Side.RIGHT, Offset(ox + bw, oy), Offset(ox + bw, oy + bh))

        // Túneles: una marca en cada extremo.
        for (g in spec.edge.gaps) {
            val horizontal = g.side == Side.TOP || g.side == Side.BOTTOM
            for (p in g.from..g.to) {
                if (horizontal) {
                    drawRect(palette.tunnel, Offset(ox + p * cell, oy), Size(cell, cell))
                    drawRect(palette.tunnel, Offset(ox + p * cell, oy + (spec.rows - 1) * cell), Size(cell, cell))
                } else {
                    drawRect(palette.tunnel, Offset(ox, oy + p * cell), Size(cell, cell))
                    drawRect(palette.tunnel, Offset(ox + (spec.cols - 1) * cell, oy + p * cell), Size(cell, cell))
                }
            }
        }

        // Spawn.
        drawCircle(
            palette.head,
            radius = maxOf(2f, cell * 1.1f),
            center = Offset(ox + (spec.spawn.x + 0.5f) * cell, oy + (spec.spawn.y + 0.5f) * cell)
        )
    }
}

/** Ayuda para el menú: texto de detalle del mapa seleccionado. */
fun LevelSpec.detail(mode: GameMode): String = when (mode) {
    GameMode.META -> "Meta: $goalBites bocados · par ${formatTime(parMs)} · ${edgeLabel()}"
    GameMode.INFINITE -> "Sin meta: gana la puntuación · ${edgeLabel()}"
}

