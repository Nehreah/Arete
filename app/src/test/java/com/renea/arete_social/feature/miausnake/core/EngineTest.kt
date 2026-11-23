package com.renea.arete_social.feature.miausnake.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    private val lv = TestLevels.open(cols = 6, rows = 6, edge = EdgeMode.WRAP)

    @Test fun `puede entrar en la celda que deja la cola`() {
        val s = GameState(
            snake = listOf(Cell(1, 0), Cell(1, 1), Cell(0, 1), Cell(0, 0)), direction = Direction.LEFT,
            queue = emptyList(), food = Cell(5, 5), foodEaten = 0, score = 0,
            tick = 0, elapsedMs = 0, rng = 1L, status = GameStatus.Running
        )
        assertEquals(GameStatus.Running, s.applyTick(lv).status)
    }

    @Test fun `la cola no admite giro opuesto al ultimo encolado`() {
        val q = enqueue(enqueue(emptyList(), Direction.RIGHT, Direction.UP), Direction.RIGHT, Direction.DOWN)
        assertEquals(listOf(Direction.UP), q)
    }

    @Test fun `la cola descarta repetido y opuesto a la direccion actual y admite maximo 2`() {
        assertEquals(emptyList<Direction>(), enqueue(emptyList(), Direction.RIGHT, Direction.RIGHT))
        assertEquals(emptyList<Direction>(), enqueue(emptyList(), Direction.RIGHT, Direction.LEFT))
        val dos = enqueue(enqueue(emptyList(), Direction.RIGHT, Direction.UP), Direction.RIGHT, Direction.LEFT)
        assertEquals(listOf(Direction.UP, Direction.LEFT), dos)
        assertEquals(dos, enqueue(dos, Direction.RIGHT, Direction.DOWN))
    }

    @Test fun `comer crece un segmento suma 10 y acelera el reloj virtual`() {
        val s = TestLevels.state(listOf(Cell(2, 2), Cell(1, 2), Cell(0, 2)), Direction.RIGHT, food = Cell(3, 2))
        val n = s.applyTick(lv)
        assertEquals(4, n.snake.size)
        assertEquals(Cell(3, 2), n.snake.first())
        assertEquals(10, n.score)
        assertEquals(1, n.foodEaten)
        assertEquals(1L, n.tick)
        assertEquals(200L, n.elapsedMs) // tick jugado con la velocidad previa a comer
        assertTrue(n.food !in n.snake)
        assertTrue(n.food in lv.floorCells)
    }

    @Test fun `sin comer el tiempo virtual suma tickMs por tick`() {
        var s = TestLevels.state(listOf(Cell(3, 3), Cell(2, 3), Cell(1, 3)), Direction.RIGHT, food = Cell(0, 0))
        repeat(2) { s = s.applyTick(lv) }
        assertEquals(400L, s.elapsedMs)
        assertEquals(2L, s.tick)
    }

    @Test fun `el borde solido y las paredes matan`() {
        val solido = TestLevels.open(cols = 6, rows = 6, edge = EdgeMode.SOLID)
        val contraBorde = TestLevels.state(listOf(Cell(5, 3), Cell(4, 3), Cell(3, 3)), Direction.RIGHT, Cell(0, 0))
        assertEquals(GameStatus.GameOver, contraBorde.applyTick(solido).status)
        val conPared = TestLevels.open(cols = 6, rows = 6, walls = setOf(Cell(4, 3)))
        val contraPared = TestLevels.state(listOf(Cell(3, 3), Cell(2, 3), Cell(1, 3)), Direction.RIGHT, Cell(0, 0))
        assertEquals(GameStatus.GameOver, contraPared.applyTick(conPared).status)
    }

    @Test fun `chocar con el cuerpo mata`() {
        val s = TestLevels.state(
            listOf(Cell(2, 2), Cell(2, 3), Cell(1, 3), Cell(1, 2), Cell(1, 1)), Direction.LEFT, Cell(5, 5)
        )
        assertEquals(GameStatus.GameOver, s.applyTick(lv).status)
    }

    @Test fun `el wrap cruza el borde`() {
        val s = TestLevels.state(listOf(Cell(5, 3), Cell(4, 3), Cell(3, 3)), Direction.RIGHT, Cell(2, 0))
        assertEquals(Cell(0, 3), s.applyTick(lv).snake.first())
    }

    @Test fun `alcanzar la meta gana`() {
        val meta = TestLevels.open(cols = 6, rows = 6, goalBites = 1)
        val s = TestLevels.state(listOf(Cell(2, 2), Cell(1, 2), Cell(0, 2)), Direction.RIGHT, food = Cell(3, 2))
        assertEquals(GameStatus.Won, s.applyTick(meta).status)
    }

    @Test fun `tablero lleno es victoria sin bucle`() {
        val diminuto = TestLevels.open(cols = 4, rows = 1, spawn = Cell(2, 0))
        val s = GameEngine.initial(diminuto, seed = 5L)
        assertEquals(Cell(3, 0), s.food)
        val n = s.applyTick(diminuto)
        assertEquals(GameStatus.Won, n.status)
        assertEquals(10, n.score)
    }

    @Test fun `pausa congela y reanudar continua`() {
        val s = GameEngine.initial(lv, 1L)
        val p = s.pause()
        assertEquals(GameStatus.Paused, p.status)
        assertEquals(p, p.applyTick(lv))
        assertEquals(GameStatus.Running, p.resume().status)
    }

    @Test fun `la comida inicial nunca cae en el cuerpo ni en paredes`() {
        val paredes = setOf(Cell(0, 0), Cell(1, 0), Cell(2, 0))
        val nivel = TestLevels.open(cols = 6, rows = 6, walls = paredes)
        for (seed in 1L..50L) {
            val s = GameEngine.initial(nivel, seed)
            assertFalse(s.food in s.snake)
            assertFalse(s.food in paredes)
        }
    }

    @Test fun `misma semilla y mismos giros dan el mismo estado`() {
        assertEquals(guion(), guion())
    }

    /** Golden: fija el resultado exacto del guion; cualquier cambio de reglas o PRNG lo rompe. */
    @Test fun `golden del guion de 300 ticks`() {
        val s = guion()
        assertEquals(GameStatus.GameOver, s.status)
        assertEquals(89L, s.tick)
        assertEquals(4, s.foodEaten)
        assertEquals(40, s.score)
        assertEquals(17127L, s.elapsedMs)
        assertEquals(6045135835962859838L, s.rng)
        assertEquals(Cell(1, 4), s.food)
        assertEquals(
            listOf(Cell(3, 4), Cell(3, 5), Cell(3, 0), Cell(3, 1), Cell(3, 2), Cell(3, 3), Cell(2, 3)),
            s.snake
        )
        assertEquals(Direction.UP, s.direction)
        assertEquals(listOf(Direction.RIGHT, Direction.UP), s.queue)
    }

    private fun guion(): GameState {
        var s = GameEngine.initial(lv, seed = 42L)
        repeat(300) { t ->
            if (t % 7 == 0) s = s.copy(queue = enqueue(s.queue, s.direction,
                if ((t / 7) % 2 == 0) Direction.UP else Direction.RIGHT))
            s = s.applyTick(lv)
        }
        return s
    }

    @Test(expected = IllegalArgumentException::class)
    fun `un spawn en pared se rechaza`() {
        TestLevels.open(cols = 6, rows = 6, walls = setOf(Cell(3, 3)))
    }
}
