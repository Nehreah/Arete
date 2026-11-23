package com.renea.arete_social.feature.miausnake.ui.maps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
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

private const val COLUMNS = 3

/**
 * Selector de mapas por packs: una cuadrícula de tarjetas con miniatura, nombre, dificultad y
 * medalla (modo meta) o récord (modo infinito). Los 20 mapas están abiertos desde el principio.
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
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (pack in 1..MapPacks.COUNT) {
            val specs = catalog.pack(pack)
            if (specs.isEmpty()) continue
            val gold = specs.count { book.medal(it) == Medal.GOLD }
            BasicText(
                "PACK ${pack.toString().padStart(2, '0')} · ${MapPacks.title(pack).uppercase()} · ${MapPacks.edgeTitle(pack)}" +
                    (if (mode == GameMode.META) "  ·  oros $gold/${specs.size}" else ""),
                Modifier.padding(top = 6.dp),
                style = TextStyle(color = palette.textDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            )
            for (row in specs.chunked(COLUMNS)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    val fg = if (selected) palette.background else palette.text
    val dim = if (selected) palette.background else palette.textDim
    val status = when (mode) {
        GameMode.META -> if (medal == Medal.NONE) "sin medalla" else medal.label
        GameMode.INFINITE -> "récord ${record?.bestScore ?: 0}"
    }
    val description = "Mapa ${spec.number}, ${spec.name}, ${spec.edgeLabel()}, dificultad ${spec.difficulty} de 5, $status"
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) palette.food else palette.panel)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = description
                this.selected = selected
            }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LevelThumbnail(spec, palette, Modifier.fillMaxWidth().aspectRatio(spec.cols.toFloat() / spec.rows.toFloat()))
        Spacer(Modifier.height(4.dp))
        CardText("${spec.number.toString().padStart(2, '0')} ${spec.name}", fg, 12, bold = true)
        CardText("★".repeat(spec.difficulty) + "☆".repeat(5 - spec.difficulty), dim, 11)
        CardText(
            when (mode) {
                GameMode.META -> status + (record?.bestTimeMs?.let { " · ${formatTime(it)}" } ?: "")
                GameMode.INFINITE -> status
            },
            dim, 11
        )
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

