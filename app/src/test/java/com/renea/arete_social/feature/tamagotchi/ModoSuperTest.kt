package com.renea.arete_social.feature.tamagotchi

import com.renea.arete_social.feature.tamagotchi.logic.acariciar
import com.renea.arete_social.feature.tamagotchi.logic.alimentar
import com.renea.arete_social.feature.tamagotchi.logic.alternarSueno
import com.renea.arete_social.feature.tamagotchi.logic.applyTick
import com.renea.arete_social.feature.tamagotchi.logic.banar
import com.renea.arete_social.feature.tamagotchi.logic.cumpleMetaSuper
import com.renea.arete_social.feature.tamagotchi.logic.evaluarModoSuper
import com.renea.arete_social.feature.tamagotchi.logic.inicioDiaMillis
import com.renea.arete_social.feature.tamagotchi.logic.milisHastaDespertar
import com.renea.arete_social.feature.tamagotchi.logic.milisHastaPoderActivar
import com.renea.arete_social.feature.tamagotchi.logic.milisSuperRestantes
import com.renea.arete_social.feature.tamagotchi.logic.modoSuperActivo
import com.renea.arete_social.feature.tamagotchi.logic.momentoDespertar
import com.renea.arete_social.feature.tamagotchi.logic.progresoSuper
import com.renea.arete_social.feature.tamagotchi.logic.puedeActivarSuper
import com.renea.arete_social.feature.tamagotchi.logic.EstadisticaSuper
import com.renea.arete_social.feature.tamagotchi.model.FoodType
import com.renea.arete_social.feature.tamagotchi.model.PetBalance
import com.renea.arete_social.feature.tamagotchi.model.PetEntity
import com.renea.arete_social.feature.tamagotchi.model.PetStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.Json
import java.util.Calendar

/**
 * Modo Súper de Michi: reglas, enfriamiento, pistas, hora de despertar y escenarios de balance.
 *
 * Los escenarios (última sección) son el equivalente en Kotlin de simulacion_modo_super.py: comprueban
 * que la meta es «alcanzable pero no sencilla» con las reglas REALES de la mascota.
 */
class ModoSuperTest {

    private val minuto = 60_000L
    private val hora = 60 * minuto
    private val t0: Long = Calendar.getInstance().apply {
        clear(); set(2026, Calendar.JANUARY, 5, 5, 0, 0)
    }.timeInMillis

    private fun adulta(block: PetEntity.() -> PetEntity = { this }): PetEntity =
        PetEntity(
            ownerId = "u", name = "Michi", stage = PetStage.ADULTO,
            birthMillis = t0 - 30 * hora,
            lastTickMillis = t0,
            lastDayResetMillis = inicioDiaMillis(t0)
        ).block()

    /** Michi que cumple justo la meta: 100 en todo y energía en el mínimo. */
    private fun perfecta(block: PetEntity.() -> PetEntity = { this }): PetEntity = adulta {
        copy(
            hunger = 100, happiness = 100, hygiene = 100, health = 100,
            energy = PetBalance.SUPER_ENERGIA_MINIMA
        )
    }.block()

    // ── Reglas básicas ───────────────────────────────────────────────────────

    @Test
    fun `la meta exige 4 estadisticas al 100 y la energia minima`() {
        assertTrue(perfecta().cumpleMetaSuper())
        assertFalse(perfecta { copy(hunger = 99) }.cumpleMetaSuper())
        assertFalse(perfecta { copy(happiness = 99) }.cumpleMetaSuper())
        assertFalse(perfecta { copy(hygiene = 99) }.cumpleMetaSuper())
        assertFalse(perfecta { copy(health = 99) }.cumpleMetaSuper())
        assertFalse(perfecta { copy(energy = PetBalance.SUPER_ENERGIA_MINIMA - 1) }.cumpleMetaSuper())
        assertTrue(perfecta { copy(energy = 100) }.cumpleMetaSuper())
    }

    @Test
    fun `al cumplir la meta se activa y guarda el instante`() {
        val p = perfecta().evaluarModoSuper(t0)
        assertEquals(t0, p.superDesdeMillis)
        assertTrue(p.modoSuperActivo(t0))
        assertTrue(p.modoSuperActivo(t0 + 2 * hora + 59 * minuto))
        assertEquals(PetBalance.SUPER_DURATION_MILLIS, p.milisSuperRestantes(t0))
    }

    @Test
    fun `sin cumplir la meta no se activa`() {
        assertEquals(0L, perfecta { copy(hunger = 99) }.evaluarModoSuper(t0).superDesdeMillis)
        assertEquals(0L, perfecta { copy(energy = 96) }.evaluarModoSuper(t0).superDesdeMillis)
    }

    @Test
    fun `dormida enferma fuera de linea o huevo no se activa`() {
        assertFalse(perfecta { copy(isSleeping = true) }.puedeActivarSuper(t0))
        assertFalse(perfecta { copy(isSick = true) }.puedeActivarSuper(t0))
        assertFalse(perfecta { copy(isCorrupted = true) }.puedeActivarSuper(t0))
        assertFalse(perfecta { copy(stage = PetStage.HUEVO) }.puedeActivarSuper(t0))
        assertTrue(perfecta().puedeActivarSuper(t0))
    }

    @Test
    fun `una vez activo no se reinicia ni se extiende aunque siga cumpliendo la meta`() {
        val activa = perfecta().evaluarModoSuper(t0)
        val despues = activa.evaluarModoSuper(t0 + hora)
        assertEquals(t0, despues.superDesdeMillis)
        assertEquals(activa, despues)
    }

    @Test
    fun `el super dura exactamente 3 horas aunque las estadisticas bajen`() {
        val activa = perfecta().evaluarModoSuper(t0).copy(hunger = 40, happiness = 40, hygiene = 40, energy = 20)
        assertTrue(activa.modoSuperActivo(t0 + 3 * hora - 1))
        assertFalse(activa.modoSuperActivo(t0 + 3 * hora))
        assertEquals(0L, activa.milisSuperRestantes(t0 + 3 * hora))
    }

    @Test
    fun `enfriamiento de 20 horas desde la activacion`() {
        val activa = perfecta().evaluarModoSuper(t0)
        val de20 = perfecta { copy(superDesdeMillis = t0) }
        // Tras vencer el Súper vuelve a cumplir la meta, pero aún no pasaron 20 h.
        assertFalse(de20.puedeActivarSuper(t0 + 19 * hora + 59 * minuto))
        assertEquals(minuto, de20.milisHastaPoderActivar(t0 + 19 * hora + 59 * minuto))
        assertTrue(de20.puedeActivarSuper(t0 + 20 * hora))
        assertEquals(t0 + 20 * hora, de20.evaluarModoSuper(t0 + 20 * hora).superDesdeMillis)
        assertEquals(PetBalance.SUPER_COOLDOWN_MILLIS, activa.milisHastaPoderActivar(t0))
    }

    @Test
    fun `si el reloj retrocede el enfriamiento nunca supera las 20 horas`() {
        val p = perfecta { copy(superDesdeMillis = t0) }
        assertEquals(PetBalance.SUPER_COOLDOWN_MILLIS, p.milisHastaPoderActivar(t0 - 5 * hora))
    }

    @Test
    fun `sin activaciones previas no hay enfriamiento`() {
        assertEquals(0L, perfecta().milisHastaPoderActivar(t0))
    }

    // ── Enganche en la simulación ────────────────────────────────────────────

    @Test
    fun `alimentar hasta dejarla perfecta activa el super`() {
        val antes = perfecta { copy(hunger = 60, energy = 100) }
        assertFalse(antes.modoSuperActivo(t0))
        val r = antes.alimentar(FoodType.RACION_SOLAR, t0)
        assertEquals(100, r.pet.hunger)
        assertTrue(r.pet.modoSuperActivo(t0))
    }

    @Test
    fun `banar o acariciar tambien pueden activarlo`() {
        val banada = perfecta { copy(hygiene = 70, energy = 100, happiness = 90) }.banar(t0)
        assertEquals(100, banada.pet.hygiene)
        assertEquals(98, banada.pet.happiness.coerceAtMost(98))
        val mimos = perfecta { copy(happiness = 95, energy = 100) }.acariciar(t0)
        assertEquals(100, mimos.pet.happiness)
        assertTrue(mimos.pet.modoSuperActivo(t0))
    }

    // ── Dormir y despertar ───────────────────────────────────────────────────

    @Test
    fun `el aviso de despertar coincide con cuando la simulacion la despierta`() {
        val durmiendo = adulta { copy(energy = 50) }.alternarSueno(t0).pet
        assertTrue(durmiendo.isSleeping)
        val momento = durmiendo.momentoDespertar(t0)
        assertNotNull(momento)
        assertTrue("sigue dormida un poco antes", durmiendo.applyTick(momento!! - 2 * minuto).isSleeping)
        assertFalse("despierta un poco después", durmiendo.applyTick(momento + 2 * minuto).isSleeping)
        // 50 → 100 a 0,135/min ≈ 6 h 10 min
        assertEquals(370.0, (momento - t0) / 60_000.0, 1.0)
    }

    @Test
    fun `si esta despierta con sueno estima la hora si la duermes ahora`() {
        val p = adulta { copy(energy = 60) }
        val faltan = p.milisHastaDespertar(t0)
        assertNotNull(faltan)
        assertEquals(296.0, faltan!! / 60_000.0, 1.0)
    }

    @Test
    fun `sin sueno o sin poder dormir no hay hora de despertar`() {
        assertNull(adulta { copy(energy = 95) }.momentoDespertar(t0))
        assertNull(adulta { copy(isCorrupted = true) }.momentoDespertar(t0))
        assertNull(adulta { copy(stage = PetStage.HUEVO) }.momentoDespertar(t0))
    }

    // ── Compatibilidad con mascotas guardadas antes del Modo Súper ───────────

    @Test
    fun `una mascota guardada con esquema 5 se lee sin perder datos y sin super`() {
        // Misma configuración que PetRepositoryLocal (ignoreUnknownKeys + encodeDefaults).
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val actual = adulta { copy(hunger = 61, happiness = 72, chispas = 4) }
        // JSON como lo escribía la versión anterior: sin el campo superDesdeMillis y con schemaVersion 5.
        val viejo = json.encodeToString(PetEntity.serializer(), actual)
            .replace(Regex(",?\"superDesdeMillis\":\\d+"), "")
            .replace(Regex("\"schemaVersion\":\\d+"), "\"schemaVersion\":5")
        assertFalse(viejo.contains("superDesdeMillis"))
        val leida = json.decodeFromString(PetEntity.serializer(), viejo)
        assertEquals(0L, leida.superDesdeMillis)
        assertEquals(5, leida.schemaVersion)
        assertEquals(61, leida.hunger)
        assertEquals(72, leida.happiness)
        assertEquals(4, leida.chispas)
        assertFalse(leida.modoSuperActivo(t0))
    }

    @Test
    fun `el super sobrevive a guardar y leer la mascota`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val activa = perfecta().evaluarModoSuper(t0)
        val leida = json.decodeFromString(PetEntity.serializer(), json.encodeToString(PetEntity.serializer(), activa))
        assertEquals(t0, leida.superDesdeMillis)
        assertTrue(leida.modoSuperActivo(t0 + hora))
    }

    // ── Panel «Camino al Súper» y pistas ─────────────────────────────────────

    @Test
    fun `el progreso lista las cinco estadisticas con su objetivo`() {
        val prog = adulta { copy(hunger = 100, happiness = 80, hygiene = 100, health = 100, energy = 97) }.progresoSuper(t0)
        assertEquals(5, prog.requisitos.size)
        assertEquals(4, prog.cumplidos)
        assertFalse(prog.metaCumplida)
        assertEquals(PetBalance.SUPER_ENERGIA_MINIMA, prog.requisitos.first { it.estadistica == EstadisticaSuper.ENERGIA }.objetivo)
        assertEquals(PetBalance.SUPER_STAT_REQUERIDA, prog.requisitos.first { it.estadistica == EstadisticaSuper.HAMBRE }.objetivo)
    }

    @Test
    fun `activo no muestra pista y cuenta el tiempo restante`() {
        val p = perfecta().evaluarModoSuper(t0).progresoSuper(t0 + hora)
        assertTrue(p.activo)
        assertNull(p.pista)
        assertEquals(2 * hora, p.restanteMillis)
    }

    @Test
    fun `pista de energia baja`() {
        val pista = adulta { copy(energy = 50) }.progresoSuper(t0).pista
        assertTrue(pista!!.contains("durmiendo"))
    }

    @Test
    fun `pista de comer le sube la energia si falta poco`() {
        val pista = adulta { copy(energy = 95, hunger = 60) }.progresoSuper(t0).pista
        assertEquals("Comer le sube un poco la energía.", pista)
    }

    @Test
    fun `pista de chispa social si le falta un poco mas de energia`() {
        val pista = adulta { copy(energy = 91, hunger = 95, chispas = 2) }.progresoSuper(t0).pista
        assertEquals("Una Chispa social le sube la energía lo justo.", pista)
    }

    @Test
    fun `zona muerta de la comida entre 90 y 99 explica cuanto esperar`() {
        // energía suficiente; hambre 93 no quiere comer hasta bajar de 90.
        val pista = adulta { copy(energy = 100, hunger = 93) }.progresoSuper(t0).pista!!
        assertTrue(pista, pista.startsWith("Aún no tiene hambre; podrá comer en"))
    }

    @Test
    fun `una racion no basta si el hambre es menor de 50`() {
        val pista = adulta { copy(energy = 100, hunger = 40) }.progresoSuper(t0).pista!!
        assertTrue(pista, pista.contains("Chispa social"))
    }

    @Test
    fun `zona muerta de la higiene entre 95 y 99`() {
        val pista = adulta { copy(energy = 100, hunger = 100, hygiene = 97) }.progresoSuper(t0).pista!!
        assertTrue(pista, pista.startsWith("Está muy limpia para bañarla; espera"))
    }

    @Test
    fun `pista de banar cuando falta higiene y se puede`() {
        assertEquals("Báñala: la deja al 100.", adulta { copy(energy = 100, hunger = 100, hygiene = 60) }.progresoSuper(t0).pista)
    }

    @Test
    fun `pista de mimos cuando solo falta animo`() {
        val pista = adulta { copy(energy = 100, hunger = 100, hygiene = 100, happiness = 90) }.progresoSuper(t0).pista
        assertEquals("Los mimos le suben el ánimo.", pista)
    }

    @Test
    fun `pistas de dormida enferma y enfriamiento`() {
        assertTrue(adulta { copy(isSleeping = true, energy = 60) }.progresoSuper(t0).pista!!.contains("despertará en"))
        assertTrue(adulta { copy(isSick = true) }.progresoSuper(t0).pista!!.contains("enferma"))
        val enfriando = perfecta { copy(superDesdeMillis = t0) }.progresoSuper(t0 + 10 * hora)
        assertFalse(enfriando.activo)
        assertTrue(enfriando.pista!!.startsWith("El Súper volverá a estar disponible en 10 h"))
    }

    // ── Escenarios de balance (equivalente a simulacion_modo_super.py) ───────

    private class Cuidador(
        val visitas: List<Pair<Int, Int>>,
        val cama: Pair<Int, Int>? = null,
        val planificaDespertar: Pair<Int, Int>? = null
    )

    /**
     * Vive [dias] días con las funciones reales de la mascota. La app solo «se abre» en las visitas
     * (y, si planifica, a la hora ideal de dormir), como en la vida real: lo demás es la puesta al
     * día retroactiva de applyTick. Devuelve los días (desde el 4.º) en que se activó el Súper.
     */
    private fun vivir(c: Cuidador, dias: Int = 14, conChispas: Boolean = false): Set<Int> {
        var pet = adulta { copy(chispas = if (conChispas) 99 else 0) }
        val activados = mutableSetOf<Int>()
        fun registrar(p: PetEntity, ahora: Long) {
            if (p.superDesdeMillis != pet.superDesdeMillis) {
                activados += ((ahora - t0) / (24 * hora)).toInt()
            }
            pet = p
        }
        fun visita(ahora: Long) {
            var t = ahora
            registrar(pet.applyTick(t), t)
            val comida = if (conChispas && pet.hunger < PetBalance.FULL_THRESHOLD) FoodType.CHISPA_SOCIAL else FoodType.RACION_SOLAR
            registrar(pet.alimentar(comida, t).pet, t)
            registrar(pet.banar(t).pet, t)
            repeat(PetBalance.MAX_CUDDLES_PER_DAY) {
                t += PetBalance.CUDDLE_MIN_GAP_MILLIS + 1_000
                registrar(pet.acariciar(t).pet, t)
            }
        }
        for (dia in 0 until dias) {
            val base = t0 - 5 * hora + dia * 24 * hora          // 0:00 de ese día
            val eventos = sortedMapOf<Long, () -> Unit>()
            for ((h, m) in c.visitas) eventos[base + h * hora + m * minuto] = { visita(base + h * hora + m * minuto) }
            c.cama?.let { (h, m) ->
                val instante = base + h * hora + m * minuto + 5_000   // justo después de la visita de esa hora
                eventos[instante] = { registrar(pet.applyTick(instante).alternarSueno(instante).pet, instante) }
            }
            for ((_, accion) in eventos) accion()

            c.planificaDespertar?.let { (h, m) ->
                // Se abre la app cada minuto entre las 21:00 y las 2:00 solo para mirar la hora de despertar
                // y acostarla en el momento justo. Se adelanta al día siguiente: 21:00 de este día a 2:00 del siguiente.
                val objetivo = base + 24 * hora + h * hora + m * minuto
                var t = base + 21 * hora
                val limite = base + 26 * hora
                while (t <= limite) {
                    val alDia = pet.applyTick(t)
                    val despierta = alDia.momentoDespertar(t)
                    if (!alDia.isSleeping && alDia.energy < PetBalance.NOT_SLEEPY_THRESHOLD &&
                        despierta != null && despierta >= objetivo - 30 * minuto
                    ) {
                        registrar(alDia.alternarSueno(t).pet, t)
                        break
                    }
                    t += minuto
                }
            }
        }
        return activados.filter { it >= 3 }.toSet()
    }

    @Test
    fun `escenario A cuidador casual de 2 visitas nunca despierta el super`() {
        val dias = vivir(Cuidador(visitas = listOf(8 to 0, 21 to 30), cama = 21 to 30))
        assertTrue("activó el Súper en $dias", dias.isEmpty())
    }

    @Test
    fun `escenario B constante que duerme a Michi temprano no lo despierta`() {
        val dias = vivir(Cuidador(visitas = listOf(7 to 30, 12 to 30, 18 to 30, 22 to 30), cama = 22 to 30))
        assertTrue("activó el Súper en $dias", dias.isEmpty())
    }

    @Test
    fun `escenario C constante que la duerme a la hora justa lo despierta cada dia`() {
        // Con el rasgo «Noctámbula» (sueño más rápido), acostarla entre las 01:00 y las 02:30 la hace
        // despertar poco antes de la visita de las 7:30. Ver el barrido en el manual de cambios.
        val dias = vivir(Cuidador(visitas = listOf(7 to 30, 12 to 30, 18 to 30, 22 to 30), cama = 1 to 30))
        assertTrue("activó solo ${dias.size} de 11 días", dias.size >= 9)
    }

    @Test
    fun `acostarla demasiado pronto o demasiado tarde no sirve aunque cuide igual`() {
        val visitas = listOf(7 to 30, 12 to 30, 18 to 30, 22 to 30)
        // 00:30: despierta de madrugada y a las 7:30 la energía ya bajó demasiado.
        assertTrue(vivir(Cuidador(visitas, cama = 0 to 30)).isEmpty())
        // 03:00: a las 7:30 todavía duerme y no se la puede cuidar.
        assertTrue(vivir(Cuidador(visitas, cama = 3 to 0)).isEmpty())
    }

    @Test
    fun `la Chispa social amplia la ventana de acostarla`() {
        val visitas = listOf(7 to 30, 12 to 30, 18 to 30, 22 to 30)
        assertTrue(vivir(Cuidador(visitas, cama = 0 to 30), conChispas = false).isEmpty())
        assertTrue(vivir(Cuidador(visitas, cama = 0 to 30), conChispas = true).isNotEmpty())
    }

    @Test
    fun `escenario D quien planifica la hora de dormir lo despierta casi cada dia`() {
        val dias = vivir(Cuidador(visitas = listOf(7 to 15, 12 to 30, 18 to 30, 22 to 0), planificaDespertar = 7 to 0))
        assertTrue("activó solo ${dias.size} de 11 días", dias.size >= 9)
    }

    @Test
    fun `el enfriamiento impide activarlo mas de una vez cada 20 horas`() {
        var pet = perfecta().evaluarModoSuper(t0)
        val primera = pet.superDesdeMillis
        // Aunque en las siguientes 19 h 59 min vuelva a estar perfecta, no se reactiva.
        var t = t0 + 4 * hora
        while (t < t0 + 20 * hora) {
            pet = pet.copy(hunger = 100, happiness = 100, hygiene = 100, health = 100, energy = 100).evaluarModoSuper(t)
            assertEquals(primera, pet.superDesdeMillis)
            t += 30 * minuto
        }
        assertEquals(t0 + 20 * hora, pet.copy(energy = 100).evaluarModoSuper(t0 + 20 * hora).superDesdeMillis)
    }
}
