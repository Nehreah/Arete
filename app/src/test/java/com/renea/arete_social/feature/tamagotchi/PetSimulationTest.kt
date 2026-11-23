package com.renea.arete_social.feature.tamagotchi

import com.renea.arete_social.feature.tamagotchi.logic.Cuidado
import com.renea.arete_social.feature.tamagotchi.logic.acariciar
import com.renea.arete_social.feature.tamagotchi.logic.alimentar
import com.renea.arete_social.feature.tamagotchi.logic.alternarSueno
import com.renea.arete_social.feature.tamagotchi.logic.applyTick
import com.renea.arete_social.feature.tamagotchi.logic.banar
import com.renea.arete_social.feature.tamagotchi.logic.curar
import com.renea.arete_social.feature.tamagotchi.logic.ganarChispaPorEncuentro
import com.renea.arete_social.feature.tamagotchi.logic.ganarXp
import com.renea.arete_social.feature.tamagotchi.logic.inicioDiaMillis
import com.renea.arete_social.feature.tamagotchi.logic.jugar
import com.renea.arete_social.feature.tamagotchi.logic.milisHasta
import com.renea.arete_social.feature.tamagotchi.logic.nivelCuidado
import com.renea.arete_social.feature.tamagotchi.logic.nombreMascotaValido
import com.renea.arete_social.feature.tamagotchi.logic.recibirChispas
import com.renea.arete_social.feature.tamagotchi.logic.recibirMedicina
import com.renea.arete_social.feature.tamagotchi.logic.reiniciar
import com.renea.arete_social.feature.tamagotchi.logic.renombrar
import com.renea.arete_social.feature.tamagotchi.model.FoodType
import com.renea.arete_social.feature.tamagotchi.model.NivelCuidado
import com.renea.arete_social.feature.tamagotchi.model.PetBalance
import com.renea.arete_social.feature.tamagotchi.model.PetEntity
import com.renea.arete_social.feature.tamagotchi.model.PetStage
import com.renea.arete_social.feature.tamagotchi.model.ResultadoCuidado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import kotlin.random.Random

class PetSimulationTest {

    private val minuto = 60_000L
    // Instante fijo en HORA LOCAL (5:00 de un lunes de enero): el «día» de la mascota empieza a las 4:00,
    // así que los tests de pocas horas no cruzan el reinicio diario sea cual sea la zona horaria.
    private val t0: Long = Calendar.getInstance().apply {
        clear(); set(2026, Calendar.JANUARY, 5, 5, 0, 0)
    }.timeInMillis
    private val sinAzar = Random(1)

    /** Mascota adulta, sana, con todo a 80, creada "hace 2 días" respecto a [t0]. */
    private fun adulta(block: PetEntity.() -> PetEntity = { this }): PetEntity =
        PetEntity(
            ownerId = "u", name = "Michi", stage = PetStage.ADULTO,
            birthMillis = t0 - 30 * 60 * minuto, // 30 h: adulta, pero aún sin rasgos por edad
            lastTickMillis = t0,
            lastDayResetMillis = inicioDiaMillis(t0)
        ).block()

    @Test
    fun `ticks de 15 segundos desgastan igual que un solo paso (sin perder fracciones)`() {
        var a = adulta()
        var ahora = t0
        repeat(240) { ahora += 15_000L; a = a.applyTick(ahora, sinAzar) } // 60 min en pasos de 15 s
        val b = adulta().applyTick(t0 + 60 * minuto, sinAzar)
        // 60 min * 0.065 = 3.9 de hambre: ahora es MUY lento (antes eran 30 por hora).
        assertTrue("hambre ${a.hunger}", a.hunger in 76..77)
        // Tolerancia de 1 punto: son decimales acumulados en Float.
        assertTrue(kotlin.math.abs(a.hunger - b.hunger) <= 1)
        assertTrue(kotlin.math.abs(a.happiness - b.happiness) <= 1)
        assertTrue(kotlin.math.abs(a.hygiene - b.hygiene) <= 1)
        assertTrue(kotlin.math.abs(a.energy - b.energy) <= 1)
        // Ánimo: ~3 por hora.
        assertTrue("animo ${a.happiness}", a.happiness in 76..78)
    }

    @Test
    fun `una estadistica al 100 tambien baja con ticks cortos (el resto decimal no se pierde)`() {
        var p = adulta { copy(hunger = 100, hygiene = 100, happiness = 100) }
        var ahora = t0
        repeat(240) { ahora += 15_000L; p = p.applyTick(ahora, sinAzar) } // 1 hora
        assertTrue("hambre ${p.hunger}", p.hunger in 95..97)
        assertTrue("higiene ${p.hygiene}", p.hygiene in 95..97)
        assertTrue("animo ${p.happiness}", p.happiness in 96..98)
    }

    @Test
    fun `dormida recupera energia aunque los ticks sean cortos`() {
        var p = adulta { copy(energy = 40, isSleeping = true) }
        var ahora = t0
        repeat(40) { ahora += 15_000L; p = p.applyTick(ahora, sinAzar) } // 10 min
        assertTrue("energia ${p.energy}", p.energy in 40..42) // +0.135/min => ~+1.35
    }

    @Test
    fun `dormida despierta sola al llegar a 100`() {
        val p = adulta { copy(energy = 95, isSleeping = true) }.applyTick(t0 + 38 * minuto, sinAzar) // llena a los ~37 min
        assertEquals(100, p.energy)
        assertFalse(p.isSleeping)
    }

    @Test
    fun `el huevo no se desgasta y nace con el tiempo`() {
        val huevo = PetEntity(ownerId = "u", name = "Michi", birthMillis = t0, lastTickMillis = t0, lastDayResetMillis = inicioDiaMillis(t0))
        val tras = huevo.applyTick(t0 + 2 * minuto, sinAzar)
        assertEquals(80, tras.hunger)
        assertEquals(PetStage.HUEVO, tras.stage)
        val nacida = tras.applyTick(t0 + 6 * minuto, sinAzar)
        assertEquals(PetStage.BEBE, nacida.stage)
        assertEquals(80, nacida.hunger)
    }

    @Test
    fun `volver tras dias no la deja fuera de linea`() {
        val p = adulta().applyTick(t0 + 7 * 24 * 60 * minuto, sinAzar)
        assertFalse(p.isCorrupted)
        assertTrue(p.health > 0)
        // Vuelve mal (enferma), pero viva: la ausencia nunca la deja por debajo del piso.
        assertTrue(p.isSick)
        assertEquals(PetBalance.AWAY_HEALTH_FLOOR, p.health)
    }

    @Test
    fun `mirando en vivo, descuidada pierde salud y puede quedar fuera de linea, y reiniciar la devuelve`() {
        // 20 min < 30: no cuenta como ausencia, así que el piso de salud no la protege.
        val mala = adulta { copy(hunger = 0, hygiene = 0, health = 1) }.applyTick(t0 + 20 * minuto, sinAzar)
        assertTrue(mala.isCorrupted)
        val r = mala.reiniciar(t0 + 21 * minuto)
        assertEquals(ResultadoCuidado.OK, r.resultado)
        assertFalse(r.pet.isCorrupted)
        // Vuelve con salud suficiente para no enfermarse al instante.
        assertEquals(PetBalance.HEALTH_TO_CURE_SICKNESS, r.pet.health)
        assertFalse(r.pet.applyTick(t0 + 22 * minuto, sinAzar).isSick)
        assertEquals(ResultadoCuidado.NO_ESTA_FUERA_DE_LINEA, adulta().reiniciar().resultado)
    }

    @Test
    fun `una ausencia larga no la deja fuera de linea aunque estuviera descuidada`() {
        val p = adulta { copy(hunger = 0, hygiene = 0, health = 5) }.applyTick(t0 + 60 * minuto, sinAzar)
        assertFalse(p.isCorrupted)
        assertEquals(5, p.health)
    }

    @Test
    fun `un dia y medio sin abrir la app la deja enferma pero no fuera de linea`() {
        val p = adulta().applyTick(t0 + 36 * 60 * minuto, sinAzar)
        assertTrue(p.isSick)
        assertFalse(p.isCorrupted)
        assertTrue(p.health > PetBalance.AWAY_HEALTH_FLOOR)
    }

    @Test
    fun `con las necesidades cubiertas la salud se recupera sola`() {
        val p = adulta { copy(health = 65) }.applyTick(t0 + 60 * minuto, sinAzar)
        assertTrue(p.health > 65)
        assertFalse(p.isSick)
    }

    @Test
    fun `alimentar respeta la espera pero la salta si tiene mucha hambre`() {
        val comio = adulta { copy(hunger = 50) }.alimentar(FoodType.RACION_SOLAR, t0)
        assertEquals(ResultadoCuidado.OK, comio.resultado)
        assertEquals(100, comio.pet.hunger) // una ración: +50
        // Dentro de la espera: rechazado y sin cambios
        val repetir = comio.pet.copy(hunger = 60).alimentar(FoodType.RACION_SOLAR, t0 + minuto)
        assertEquals(ResultadoCuidado.EN_ESPERA, repetir.resultado)
        assertEquals(60, repetir.pet.hunger)
        // Con hambre urgente la espera no cuenta
        val urgente = comio.pet.copy(hunger = 20).alimentar(FoodType.RACION_SOLAR, t0 + minuto)
        assertEquals(ResultadoCuidado.OK, urgente.resultado)
    }

    @Test
    fun `no come si esta llena`() {
        val r = adulta { copy(hunger = 95) }.alimentar(FoodType.RACION_SOLAR, t0)
        assertEquals(ResultadoCuidado.NO_TIENE_HAMBRE, r.resultado)
    }

    @Test
    fun `la chispa social cuesta una chispa y sin chispas no se aplica`() {
        val sin = adulta { copy(hunger = 20, chispas = 0) }.alimentar(FoodType.CHISPA_SOCIAL, t0)
        assertEquals(ResultadoCuidado.SIN_CHISPAS, sin.resultado)
        assertEquals(20, sin.pet.hunger)
        val con = adulta { copy(hunger = 20, chispas = 2) }.alimentar(FoodType.CHISPA_SOCIAL, t0)
        assertEquals(ResultadoCuidado.OK, con.resultado)
        assertEquals(1, con.pet.chispas)
        assertEquals(90, con.pet.hunger) // chispa social: +70
    }

    @Test
    fun `comer de mas hoy le sienta mal salvo con estomago de hierro`() {
        val saturada = adulta { copy(hunger = 30, vecesAlimentadoHoy = PetBalance.MAX_FEEDINGS_PER_DAY) }
        val mal = saturada.alimentar(FoodType.RACION_SOLAR, t0)
        assertTrue(mal.pet.health < 100)
        val hierro = saturada.copy(rasgosDesbloqueados = "ESTOMAGO_DE_HIERRO").alimentar(FoodType.RACION_SOLAR, t0)
        assertEquals(100, hierro.pet.health)
    }

    @Test
    fun `no se puede hacer nada dormida, en huevo o fuera de linea`() {
        assertEquals(ResultadoCuidado.DORMIDA, adulta { copy(isSleeping = true) }.alimentar(FoodType.RACION_SOLAR, t0).resultado)
        assertEquals(ResultadoCuidado.DORMIDA, adulta { copy(isSleeping = true) }.jugar(t0).resultado)
        assertEquals(ResultadoCuidado.FUERA_DE_LINEA, adulta { copy(isCorrupted = true) }.banar(t0).resultado)
        assertEquals(ResultadoCuidado.HUEVO, adulta { copy(stage = PetStage.HUEVO) }.jugar(t0).resultado)
    }

    @Test
    fun `jugar pide energia y comida y tiene espera`() {
        assertEquals(ResultadoCuidado.POCA_ENERGIA, adulta { copy(energy = 20) }.jugar(t0).resultado)
        assertEquals(ResultadoCuidado.TIENE_HAMBRE, adulta { copy(hunger = 30) }.jugar(t0).resultado)
        val ok = adulta().jugar(t0)
        assertEquals(ResultadoCuidado.OK, ok.resultado)
        assertEquals(76, ok.pet.energy) // jugar gasta solo 4: la energía dura todo el día
        assertEquals(ResultadoCuidado.EN_ESPERA, ok.pet.jugar(t0 + minuto).resultado)
    }

    @Test
    fun `banar deja la higiene al maximo y respeta la espera`() {
        val ok = adulta { copy(hygiene = 40) }.banar(t0)
        assertEquals(100, ok.pet.hygiene)
        assertEquals(ResultadoCuidado.EN_ESPERA, ok.pet.copy(hygiene = 70).banar(t0 + minuto).resultado)
        assertEquals(ResultadoCuidado.YA_LIMPIA, adulta { copy(hygiene = 98) }.banar(t0).resultado)
    }

    @Test
    fun `curar solo funciona si esta enferma y tiene medicina`() {
        assertEquals(ResultadoCuidado.NO_ESTA_ENFERMA, adulta { copy(medicinas = 1) }.curar(t0).resultado)
        val r = adulta { copy(isSick = true, health = 40, medicinas = 1) }.curar(t0)
        assertEquals(ResultadoCuidado.OK, r.resultado)
        assertEquals(90, r.pet.health)
        assertFalse(r.pet.isSick)
        assertEquals(0, r.pet.medicinas)
    }

    @Test
    fun `sin medicina enviada por alguien no se puede curar`() {
        val enferma = adulta { copy(isSick = true, health = 40) }
        val r = enferma.curar(t0)
        assertEquals(ResultadoCuidado.SIN_MEDICINA, r.resultado)
        assertTrue(r.pet.isSick)
        assertEquals(40, r.pet.health)
    }

    @Test
    fun `recibir medicina la guarda y el botiquin tiene tope`() {
        var p = adulta()
        repeat(PetBalance.MAX_MEDICINES_STORED) { p = p.recibirMedicina().pet }
        assertEquals(PetBalance.MAX_MEDICINES_STORED, p.medicinas)
        assertEquals(ResultadoCuidado.BOTIQUIN_LLENO, p.recibirMedicina().resultado)
        assertEquals(PetBalance.MAX_MEDICINES_STORED, p.recibirMedicina().pet.medicinas)
    }

    @Test
    fun `recibir chispas las suma y limita la cantidad por regalo`() {
        val uno = adulta().recibirChispas(1)
        assertEquals(1, uno.pet.chispas)
        val enorme = adulta().recibirChispas(1_000)
        assertEquals(PetBalance.MAX_SPARKS_PER_GIFT, enorme.pet.chispas)
        assertEquals(ResultadoCuidado.SIN_CHISPAS, adulta().recibirChispas(0).resultado)
    }

    @Test
    fun `el nombre se puede editar y se valida`() {
        assertEquals("Luna", adulta().renombrar("  Luna ").pet.name)
        assertEquals(ResultadoCuidado.NOMBRE_INVALIDO, adulta().renombrar("   ").resultado)
        assertEquals(ResultadoCuidado.NOMBRE_INVALIDO, adulta().renombrar("x".repeat(PetBalance.NAME_MAX_LENGTH + 1)).resultado)
        assertEquals("Michi", adulta().renombrar("Michi").pet.name)
        assertEquals("Mi gato", nombreMascotaValido("Mi    gato"))
        assertEquals(null, nombreMascotaValido("\n\t"))
        assertEquals("Michi", PetBalance.DEFAULT_NAME)
    }

    @Test
    fun `dormir exige sueno y despertar siempre se puede`() {
        assertEquals(ResultadoCuidado.NO_TIENE_SUENO, adulta { copy(energy = 95) }.alternarSueno().resultado)
        val duerme = adulta { copy(energy = 50) }.alternarSueno()
        assertTrue(duerme.pet.isSleeping)
        assertFalse(duerme.pet.alternarSueno().pet.isSleeping)
    }

    @Test
    fun `dormir una noche entera y despertar da XP doble`() {
        val duerme = adulta { copy(energy = 30) }.alternarSueno(t0)
        assertTrue(duerme.pet.isSleeping)
        assertFalse(duerme.pet.modoTurbo)

        val despierta = duerme.pet.alternarSueno(t0 + 7 * 60 * minuto)
        assertFalse(despierta.pet.isSleeping)
        assertTrue(despierta.pet.modoTurbo)
        // Se levanta de mejor humor
        assertEquals(90, despierta.pet.happiness)
        // En turbo, ganar XP vale el doble
        val enTurbo = t0 + 7 * 60 * minuto + minuto
        assertEquals(20, despierta.pet.ganarXp(10, enTurbo).experiencia)
        // El turbo termina solo (a las 3 h)
        assertFalse(despierta.pet.applyTick(t0 + 10 * 60 * minuto + 2 * minuto, sinAzar).modoTurbo)
    }

    @Test
    fun `una siesta o dormir poco no da XP doble`() {
        val duerme = adulta { copy(energy = 30) }.alternarSueno(t0)
        assertFalse(duerme.pet.alternarSueno(t0 + 5 * minuto).pet.modoTurbo)
        assertFalse(duerme.pet.alternarSueno(t0 + 5 * 60 * minuto).pet.modoTurbo) // 5 h: aún no es noche completa
    }

    @Test
    fun `si se despierta sola tras dormir mucho tambien gana XP doble`() {
        val duerme = adulta { copy(energy = 30) }.alternarSueno(t0)
        // 30 -> 100 de energia tarda ~8,6 h dormida
        val despierta = duerme.pet.applyTick(t0 + 10 * 60 * minuto, sinAzar)
        assertFalse(despierta.isSleeping)
        assertTrue(despierta.modoTurbo)
        assertEquals(PetStage.ADULTO, despierta.stage)
    }

    // ── Ritmo del día: sueño y energía ───────────────────────────────────────

    @Test
    fun `una noche de 7 u 8 horas recupera toda la energia de un dia normal`() {
        val duerme = adulta { copy(energy = 45) }.alternarSueno(t0)
        val manana = duerme.pet.applyTick(t0 + 8 * 60 * minuto, sinAzar)
        // Se llenó a las ~6,8 h, despertó sola y lleva ~1 h despierta (gasta ~3 por hora).
        assertTrue("energia ${manana.energy}", manana.energy in 95..100)
        assertFalse(manana.isSleeping)
        assertTrue(manana.modoTurbo)
    }

    @Test
    fun `dormir poco solo recupera una parte de la energia`() {
        val duerme = adulta { copy(energy = 45) }.alternarSueno(t0)
        val cuatroHoras = duerme.pet.applyTick(t0 + 4 * 60 * minuto, sinAzar)
        assertTrue("energia ${cuatroHoras.energy}", cuatroHoras.energy in 75..79)
        assertTrue(cuatroHoras.isSleeping)
    }

    @Test
    fun `despierta la energia dura todo el dia (16 horas gastan menos de la mitad)`() {
        val p = adulta { copy(energy = 100) }.applyTick(t0 + 16 * 60 * minuto, sinAzar)
        assertTrue("energia ${p.energy}", p.energy in 50..54)
        assertFalse(p.isSleeping)
    }

    @Test
    fun `dormida casi no se desgasta el hambre (incentivo para dormirla de noche)`() {
        val dormida = adulta { copy(energy = 40) }.alternarSueno(t0).pet.applyTick(t0 + 8 * 60 * minuto, sinAzar)
        val despierta = adulta { copy(energy = 40) }.applyTick(t0 + 8 * 60 * minuto, sinAzar)
        assertTrue("dormida ${dormida.hunger} vs despierta ${despierta.hunger}", dormida.hunger > despierta.hunger + 15)
    }

    // ── Ritmo del día: enfermedad ────────────────────────────────────────────

    @Test
    fun `una enferma bien cuidada se recupera sola, despacio`() {
        var p = adulta { copy(isSick = true, health = 55) }
        var ahora = t0
        repeat(30) { // 30 horas; cada hora se le da de comer y se la baña
            ahora += 60 * minuto
            p = p.copy(hunger = 100, hygiene = 100).applyTick(ahora, sinAzar)
        }
        assertFalse(p.isSick)
        assertTrue(p.health >= PetBalance.HEALTH_TO_CURE_SICKNESS)
    }

    @Test
    fun `una enferma descuidada empeora`() {
        val p = adulta { copy(isSick = true, health = 55, hunger = 0, hygiene = 0) }.applyTick(t0 + 20 * minuto, sinAzar)
        assertTrue(p.isSick)
        assertTrue(p.health < 55)
    }

    @Test
    fun `ganar mucha experiencia sube varios niveles de golpe`() {
        val p = adulta().ganarXp(450) // 100 + 200 = 300 -> nivel 3, sobran 150
        assertEquals(3, p.nivel)
        assertEquals(150, p.experiencia)
    }

    @Test
    fun `los mimos tienen pausa corta y tope diario`() {
        val a = adulta().acariciar(t0)
        assertEquals(ResultadoCuidado.OK, a.resultado)
        assertEquals(85, a.pet.happiness) // rascarla sube +5 (antes +2)
        assertEquals(ResultadoCuidado.EN_ESPERA, a.pet.acariciar(t0 + 1_000L).resultado)
        val tope = adulta { copy(cariciasHoy = PetBalance.MAX_CUDDLES_PER_DAY, lastDayResetMillis = inicioDiaMillis(t0)) }.acariciar(t0)
        assertEquals(ResultadoCuidado.LIMITE_DIARIO, tope.resultado)
    }

    @Test
    fun `las chispas por amigos tienen tope diario y se reinician al dia siguiente`() {
        var p = adulta()
        repeat(6) { p = p.ganarChispaPorEncuentro(t0).pet }
        assertEquals(6, p.chispas)
        assertEquals(ResultadoCuidado.LIMITE_DIARIO, p.ganarChispaPorEncuentro(t0).resultado)
        val manana = p.ganarChispaPorEncuentro(t0 + 24 * 60 * minuto + 1_000L)
        assertEquals(ResultadoCuidado.OK, manana.resultado)
    }

    @Test
    fun `la espera se calcula bien y nunca supera su maximo`() {
        val p = adulta { copy(ultimoAlimentadoMillis = t0) }
        val espera = PetBalance.COOLDOWN_FEED_MILLIS
        assertEquals(espera - 5 * minuto, p.milisHasta(Cuidado.ALIMENTAR, t0 + 5 * minuto))
        assertEquals(0L, p.milisHasta(Cuidado.ALIMENTAR, t0 + espera + minuto))
        // Reloj movido hacia atras: no se queda bloqueada para siempre
        assertTrue(p.milisHasta(Cuidado.ALIMENTAR, t0 - 5 * 60 * minuto) <= espera)
    }

    // ── Visitas y nivel de cuidado ───────────────────────────────────────────

    @Test
    fun `comer banar y jugar de seguido cuentan como UNA visita`() {
        var p = adulta { copy(hunger = 45, hygiene = 60) }
        p = p.alimentar(FoodType.RACION_SOLAR, t0).pet
        p = p.banar(t0 + 2 * minuto).pet
        p = p.jugar(t0 + 4 * minuto).pet
        assertEquals(1, p.visitasHoy)
        // Tres horas después es otra visita
        p = p.copy(hunger = 45).alimentar(FoodType.RACION_SOLAR, t0 + 3 * 60 * minuto).pet
        assertEquals(2, p.visitasHoy)
    }

    @Test
    fun `las visitas se reinician a las 4 de la manana`() {
        val cal = Calendar.getInstance()
        cal.clear(); cal.set(2026, Calendar.JANUARY, 5, 23, 0, 0)
        val noche = cal.timeInMillis
        cal.set(2026, Calendar.JANUARY, 6, 3, 59, 0)
        val madrugada = cal.timeInMillis
        cal.set(2026, Calendar.JANUARY, 6, 4, 1, 0)
        val manana = cal.timeInMillis
        assertEquals(inicioDiaMillis(noche), inicioDiaMillis(madrugada)) // misma jornada
        assertTrue(inicioDiaMillis(manana) > inicioDiaMillis(madrugada)) // jornada nueva
        val p = PetEntity(
            ownerId = "u", name = "Michi", stage = PetStage.ADULTO, birthMillis = noche - 30 * 60 * minuto,
            lastTickMillis = noche, lastDayResetMillis = inicioDiaMillis(noche), hunger = 40
        ).alimentar(FoodType.RACION_SOLAR, noche).pet
        assertEquals(1, p.visitasHoy)
        assertEquals(1, p.applyTick(madrugada, sinAzar).visitasHoy)
        assertEquals(0, p.applyTick(manana, sinAzar).visitasHoy)
    }

    @Test
    fun `el nivel de cuidado resume como va la mascota`() {
        assertEquals(NivelCuidado.EXCELENTE, adulta { copy(hunger = 90, happiness = 90, hygiene = 90) }.nivelCuidado())
        assertEquals(NivelCuidado.ESTABLE, adulta { copy(hunger = 50, happiness = 50, hygiene = 50) }.nivelCuidado())
        assertEquals(NivelCuidado.JUSTA, adulta { copy(hunger = 25, happiness = 60, hygiene = 60) }.nivelCuidado())
        assertEquals(NivelCuidado.NECESITA_ATENCION, adulta { copy(hunger = 10) }.nivelCuidado())
        assertEquals(NivelCuidado.NECESITA_ATENCION, adulta { copy(isSick = true) }.nivelCuidado())
    }

    // ── Escenarios de varios días: lo que de verdad importa del balance ──────
    // Una persona despierta ~16 h (de 8:00 a 22:30). Cada visita: da de comer, baña, juega y
    // hace mimos; en la última visita la manda a dormir (despierta sola a las ~6 h).

    private val hora = 60 * minuto
    private val base4am: Long = Calendar.getInstance().apply {
        clear(); set(2026, Calendar.JANUARY, 5, 4, 0, 0)
    }.timeInMillis

    private class Corrida(
        val pet: PetEntity,
        val diaEnfermaPrimeraVez: Int?,
        val minHambre: Int,
        val minHigiene: Int,
        val fraccionExcelente: Double,
        val fraccionEstableOMejor: Double
    )

    private fun horasDeVisita(visitas: Int): List<Double> = when (visitas) {
        0 -> emptyList()
        1 -> listOf(22.5)
        else -> List(visitas) { 8.0 + (22.5 - 8.0) * it / (visitas - 1) }
    }

    /** Vive [dias] días mirando la mascota cada 15 min (como si la app estuviera abierta). */
    private fun vivir(visitasPorDia: Int, dias: Int): Corrida {
        var p = PetEntity(
            ownerId = "u", name = "Michi", stage = PetStage.ADULTO,
            birthMillis = base4am - 30 * hora, lastTickMillis = base4am,
            lastDayResetMillis = inicioDiaMillis(base4am)
        )
        var diaEnferma: Int? = null
        var minH = 100
        var minY = 100
        var muestras = 0
        var excelentes = 0
        var estables = 0
        for (d in 0 until dias) {
            val ini = base4am + d * 24 * hora
            var t = ini
            fun avanzarHasta(objetivo: Long) {
                while (t + 15 * minuto <= objetivo) {
                    t += 15 * minuto
                    p = p.applyTick(t, sinAzar)
                    if (p.isSick && diaEnferma == null) diaEnferma = d + 1
                    if (d >= 2) { // ya en régimen (los dos primeros días aún arrastran el 100 % inicial)
                        minH = minOf(minH, p.hunger)
                        minY = minOf(minY, p.hygiene)
                        muestras++
                        when (p.nivelCuidado()) {
                            NivelCuidado.EXCELENTE -> { excelentes++; estables++ }
                            NivelCuidado.ESTABLE -> estables++
                            else -> Unit
                        }
                    }
                }
                if (t < objetivo) { t = objetivo; p = p.applyTick(t, sinAzar) }
            }
            val horas = horasDeVisita(visitasPorDia)
            horas.forEachIndexed { i, h ->
                avanzarHasta(ini + ((h - 4.0) * 60 * minuto).toLong())
                if (i == 0 && p.isSleeping) p = p.alternarSueno(t).pet
                p = p.alimentar(FoodType.RACION_SOLAR, t).pet
                p = p.banar(t).pet
                p = p.jugar(t).pet
                repeat(3) { k -> p = p.acariciar(t + (k + 1) * 5_000L).pet }
                if (i == horas.lastIndex) p = p.alternarSueno(t + 20_000L).pet
            }
            avanzarHasta(ini + 24 * hora)
        }
        val m = muestras.coerceAtLeast(1)
        return Corrida(p, diaEnferma, minH, minY, excelentes / m.toDouble(), estables / m.toDouble())
    }

    @Test
    fun `un dia entero sin cuidarla la enferma al segundo dia`() {
        val c = vivir(visitasPorDia = 0, dias = 2)
        assertEquals(2, c.diaEnfermaPrimeraVez)
    }

    @Test
    fun `una sola visita al dia la mantiene viva pero a los 3 dias se enferma`() {
        assertEquals(null, vivir(visitasPorDia = 1, dias = 2).diaEnfermaPrimeraVez)
        val c = vivir(visitasPorDia = 1, dias = 4)
        val dia = c.diaEnfermaPrimeraVez
        assertTrue("se enfermó el día $dia", dia != null && dia in 3..4)
    }

    @Test
    fun `dos visitas al dia la mantienen estable sin enfermarse nunca`() {
        val c = vivir(visitasPorDia = 2, dias = 14)
        assertEquals(null, c.diaEnfermaPrimeraVez)
        assertFalse(c.pet.isCorrupted)
        assertTrue("min hambre ${c.minHambre}", c.minHambre >= 25)
        assertTrue("min higiene ${c.minHigiene}", c.minHigiene >= 25)
        assertTrue("estable o mejor ${c.fraccionEstableOMejor}", c.fraccionEstableOMejor >= 0.9)
    }

    @Test
    fun `tres visitas al dia la dejan bien casi todo el dia`() {
        val c = vivir(visitasPorDia = 3, dias = 14)
        assertEquals(null, c.diaEnfermaPrimeraVez)
        assertTrue("min hambre ${c.minHambre}", c.minHambre >= 45)
        assertTrue("excelente ${c.fraccionExcelente}", c.fraccionExcelente >= 0.7)
    }

    @Test
    fun `cuatro o cinco visitas repartidas la dejan excelente`() {
        for (visitas in 4..5) {
            val c = vivir(visitasPorDia = visitas, dias = 14)
            assertEquals(null, c.diaEnfermaPrimeraVez)
            assertTrue("$visitas visitas: min hambre ${c.minHambre}", c.minHambre >= 55)
            assertTrue("$visitas visitas: min higiene ${c.minHigiene}", c.minHigiene >= 45)
            assertTrue("$visitas visitas: excelente ${c.fraccionExcelente}", c.fraccionExcelente >= 0.9)
            // Y con todo eso la energía llega bien a la noche (no se agota).
            assertTrue("energia ${c.pet.energy}", c.pet.energy >= 50)
        }
    }

    @Test
    fun `visitar mas de cinco veces no da mas, los topes diarios protegen a la mascota`() {
        val c = vivir(visitasPorDia = 8, dias = 7)
        assertEquals(null, c.diaEnfermaPrimeraVez)
        assertFalse(c.pet.isCorrupted)
        assertTrue(c.pet.health >= 90)
    }
}
