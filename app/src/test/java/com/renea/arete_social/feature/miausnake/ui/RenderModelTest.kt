package com.renea.arete_social.feature.miausnake.ui

import com.renea.arete_social.feature.miausnake.core.Cell
import com.renea.arete_social.feature.miausnake.core.Direction
import com.renea.arete_social.feature.miausnake.core.Edge
import com.renea.arete_social.feature.miausnake.core.EdgeMode
import com.renea.arete_social.feature.miausnake.core.GameSession
import com.renea.arete_social.feature.miausnake.core.Side
import com.renea.arete_social.feature.miausnake.core.TestLevels
import com.renea.arete_social.feature.miausnake.core.bot.BfsBot
import com.renea.arete_social.feature.miausnake.core.step
import com.renea.arete_social.feature.miausnake.levels.PlaytestLevels
import com.renea.arete_social.feature.miausnake.levels.PlaytestMap
import com.renea.arete_social.feature.miausnake.ui.render.BodyShape
import com.renea.arete_social.feature.miausnake.ui.render.RenderModel
import com.renea.arete_social.feature.miausnake.ui.render.TunnelMark
import com.renea.arete_social.feature.miausnake.ui.render.WallEdges
import com.renea.arete_social.feature.miausnake.ui.render.bodyShape
import com.renea.arete_social.feature.miausnake.ui.render.dirTo
import com.renea.arete_social.feature.miausnake.ui.spike.CellSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderModelTest {
    private val lv = TestLevels.open(cols = 6, rows = 5)

    @Test fun `dirTo entre vecinos normales`() {
        assertEquals(Direction.RIGHT, dirTo(lv, Cell(2, 2), Cell(3, 2)))
        assertEquals(Direction.LEFT, dirTo(lv, Cell(2, 2), Cell(1, 2)))
        assertEquals(Direction.UP, dirTo(lv, Cell(2, 2), Cell(2, 1)))
        assertEquals(Direction.DOWN, dirTo(lv, Cell(2, 2), Cell(2, 3)))
    }

    @Test fun `dirTo resuelve el salto por el borde`() {
        assertEquals(Direction.LEFT, dirTo(lv, Cell(0, 2), Cell(5, 2)))
        assertEquals(Direction.RIGHT, dirTo(lv, Cell(5, 2), Cell(0, 2)))
        assertEquals(Direction.UP, dirTo(lv, Cell(3, 0), Cell(3, 4)))
        assertEquals(Direction.DOWN, dirTo(lv, Cell(3, 4), Cell(3, 0)))
    }

    @Test fun `bodyShape es simetrico y cubre las seis piezas`() {
        val u = Direction.UP; val d = Direction.DOWN; val l = Direction.LEFT; val r = Direction.RIGHT
        assertEquals(BodyShape.HORIZONTAL, bodyShape(l, r))
        assertEquals(BodyShape.HORIZONTAL, bodyShape(r, l))
        assertEquals(BodyShape.VERTICAL, bodyShape(u, d))
        assertEquals(BodyShape.VERTICAL, bodyShape(d, u))
        assertEquals(BodyShape.CORNER_NE, bodyShape(u, r))
        assertEquals(BodyShape.CORNER_NE, bodyShape(r, u))
        assertEquals(BodyShape.CORNER_NW, bodyShape(u, l))
        assertEquals(BodyShape.CORNER_SE, bodyShape(d, r))
        assertEquals(BodyShape.CORNER_SW, bodyShape(l, d))
    }

    @Test fun `los bordes de pared solo miran al suelo del tablero`() {
        val level = TestLevels.open(cols = 5, rows = 5, walls = setOf(Cell(0, 0), Cell(1, 0), Cell(2, 2)), spawn = Cell(4, 4))
        val byCell = RenderModel(level).walls.associateBy { it.cell }
        // (0,0): arriba y a la izquierda es el exterior; a la derecha hay pared; abajo, suelo.
        assertEquals(WallEdges(top = false, bottom = true, left = false, right = false), byCell.getValue(Cell(0, 0)).edges)
        assertEquals(WallEdges(top = false, bottom = true, left = false, right = true), byCell.getValue(Cell(1, 0)).edges)
        assertEquals(WallEdges(top = true, bottom = true, left = true, right = true), byCell.getValue(Cell(2, 2)).edges)
    }

    @Test fun `un mapa WRAP no dibuja marcas de tunel`() {
        assertTrue(RenderModel(PlaytestLevels.create(PlaytestMap.WRAP, CellSpec.PX16)).tunnels.isEmpty())
    }

    @Test fun `el tunel del jardin marca ambos extremos con la direccion de salida`() {
        val m = RenderModel(PlaytestLevels.create(PlaytestMap.SOLID, CellSpec.PX16))
        assertEquals(
            setOf(
                TunnelMark(Cell(9, 0), Direction.UP), TunnelMark(Cell(10, 0), Direction.UP),
                TunnelMark(Cell(9, 27), Direction.DOWN), TunnelMark(Cell(10, 27), Direction.DOWN)
            ),
            m.tunnels.toSet()
        )
        assertTrue(m.isSolid)
    }

    @Test fun `un lado en wrapSides marca todo el lado y su opuesto`() {
        val level = TestLevels.open(cols = 6, rows = 5, edgeRules = Edge(EdgeMode.SOLID, wrapSides = setOf(Side.LEFT)))
        val t = RenderModel(level).tunnels
        assertEquals(10, t.size)
        assertEquals(5, t.count { it.dir == Direction.LEFT && it.cell.x == 0 })
        assertEquals(5, t.count { it.dir == Direction.RIGHT && it.cell.x == 5 })
    }

    /** Invariante que el renderer necesita: segmentos consecutivos son vecinos (también por borde y túnel). */
    @Test fun `dirTo concuerda con step en partidas reales de ambos mapas y rejillas`() {
        for (spec in CellSpec.entries) for (map in PlaytestMap.entries) for (seed in 1L..3L) {
            val level = PlaytestLevels.create(map, spec)
            val session = GameSession(level, seed)
            var guard = 0
            while (!session.state.isFinished && guard++ < 700) {
                val d = BfsBot.nextDirection(session.state, level)
                if (d != session.state.direction) session.turn(d)
                session.tick()
                val snake = session.state.snake
                for (i in 0 until snake.lastIndex) {
                    val dir = dirTo(level, snake[i + 1], snake[i])
                    assertEquals("${level.id} seed=$seed tick=${session.state.tick} seg=$i", snake[i], step(snake[i + 1], dir, level))
                }
            }
        }
    }
}
