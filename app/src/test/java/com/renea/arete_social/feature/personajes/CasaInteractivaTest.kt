package com.renea.arete_social.feature.personajes

import com.renea.arete_social.data.crypto.GestorIdentidadEcdsa
import com.renea.arete_social.feature.personajes.data.RepositorioCasaLocal
import com.renea.arete_social.feature.personajes.logic.MotorCasa
import com.renea.arete_social.feature.personajes.model.escena.AccionObjeto
import com.renea.arete_social.feature.personajes.model.escena.CasaSnapshot
import com.renea.arete_social.feature.personajes.model.escena.CatalogoObjetosCasa
import com.renea.arete_social.feature.personajes.model.escena.ObjetoColocado
import com.renea.arete_social.feature.tamagotchi.model.PetEntity
import com.renea.arete_social.testutils.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Pruebas unitarias de caracterización para la Fase 2: Casa interactiva (un solo jugador).
 */
class CasaInteractivaTest {

    // ── 1. Cuadrícula y Colisiones ───────────────────────────────────────────

    @Test
    fun `validarColocacion permite posicionar objetos validos en la cuadricula 8x8`() {
        val cama = ObjetoColocado(
            instanciaId = "cama_1",
            catalogoId = "cama_futurista", // 2x1
            x = 0,
            y = 0
        )

        val resultado = MotorCasa.validarColocacion(emptyList(), cama)
        assertTrue("La colocación dentro de los límites debe ser válida", resultado.exito)
    }

    @Test
    fun `validarColocacion rechaza objetos que exceden los bordes de la habitacion`() {
        val fueraX = ObjetoColocado(
            instanciaId = "cama_fuera_x",
            catalogoId = "cama_futurista", // 2x1
            x = 7, // Con ancho 2 llega a x=8 (fuera del rango 0..7)
            y = 0
        )
        val resX = MotorCasa.validarColocacion(emptyList(), fueraX)
        assertFalse("Debe rechazar desbordamiento en eje X", resX.exito)

        val fueraY = ObjetoColocado(
            instanciaId = "ducha_fuera_y",
            catalogoId = "cabina_ducha_cyber", // 1x2
            x = 0,
            y = 7 // Con alto 2 llega a y=8
        )
        val resY = MotorCasa.validarColocacion(emptyList(), fueraY)
        assertFalse("Debe rechazar desbordamiento en eje Y", resY.exito)

        val negativo = ObjetoColocado(
            instanciaId = "negativo",
            catalogoId = "espejo_neon",
            x = -1,
            y = 0
        )
        assertFalse("Debe rechazar coordenadas negativas", MotorCasa.validarColocacion(emptyList(), negativo).exito)
    }

    @Test
    fun `validarColocacion detecta solapamientos entre objetos`() {
        val mesa = ObjetoColocado(
            instanciaId = "mesa_1",
            catalogoId = "mesa_centro", // 2x2 en (2..3, 2..3)
            x = 2,
            y = 2
        )

        val solapado = ObjetoColocado(
            instanciaId = "planta_colision",
            catalogoId = "planta_bonsai_led", // 1x1 en (3, 3)
            x = 3,
            y = 3
        )

        val resSolapado = MotorCasa.validarColocacion(listOf(mesa), solapado)
        assertFalse("Debe rechazar la celda solapada con la mesa", resSolapado.exito)
        assertNotNull(resSolapado.motivo)

        val adyacente = ObjetoColocado(
            instanciaId = "planta_libre",
            catalogoId = "planta_bonsai_led", // 1x1 en (4, 2)
            x = 4,
            y = 2
        )
        val resLibre = MotorCasa.validarColocacion(listOf(mesa), adyacente)
        assertTrue("Objetos contiguos sin solapamiento deben ser válidos", resLibre.exito)
    }

    @Test
    fun `validarColocacion impone el tope maximo de 16 objetos`() {
        val listaLlena = mutableListOf<ObjetoColocado>()
        // Llenar con 16 objetos 1x1 en posiciones libres
        for (i in 0 until 16) {
            listaLlena.add(
                ObjetoColocado(
                    instanciaId = "obj_$i",
                    catalogoId = "planta_bonsai_led",
                    x = i % 8,
                    y = i / 8
                )
            )
        }

        val objeto17 = ObjetoColocado(
            instanciaId = "obj_extra",
            catalogoId = "planta_bonsai_led",
            x = 0,
            y = 3
        )

        val res = MotorCasa.validarColocacion(listaLlena, objeto17)
        assertFalse("Debe rechazar al superar el tope de 16 objetos", res.exito)
    }

    // ── 2. Acciones y Efectos en PetEntity ───────────────────────────────────

    @Test
    fun `ejecutarAccionObjeto aplica efectos de descanso y recuperacion`() {
        val petCansada = PetEntity(
            ownerId = "owner-1",
            name = "Michi",
            energy = 30,
            hunger = 40,
            hygiene = 40,
            happiness = 50
        )

        val (petTrasDormir, _) = MotorCasa.ejecutarAccionObjeto(petCansada, AccionObjeto.DORMIR)
        assertEquals(55, petTrasDormir.energy) // +25

        val (petTrasComer, _) = MotorCasa.ejecutarAccionObjeto(petCansada, AccionObjeto.COMER)
        assertEquals(60, petTrasComer.hunger) // +20

        val (petTrasAseo, _) = MotorCasa.ejecutarAccionObjeto(petCansada, AccionObjeto.ASEARSE)
        assertEquals(65, petTrasAseo.hygiene) // +25

        val (petTrasJugar, _) = MotorCasa.ejecutarAccionObjeto(petCansada, AccionObjeto.JUGAR)
        assertEquals(70, petTrasJugar.happiness) // +20
        assertEquals(22, petTrasJugar.energy) // -8
    }

    // ── 3. Sellado Criptográfico e Integridad de Instantánea ──────────────────

    @Test
    fun `firmar y verificar snapshot detecta cualquier alteracion en los datos`() {
        val gestor = GestorIdentidadEcdsa(prefsOverride = FakeSharedPreferences())
        val clavePublica = gestor.obtenerClavePublicaBase64()!!

        val snapshotOriginal = CasaSnapshot(
            propietarioId = "owner-123",
            personajeId = "pers-456",
            version = 1,
            temaId = "tema_pixel_013",
            objetos = listOf(
                ObjetoColocado("inst-1", "cama_futurista", 1, 1),
                ObjetoColocado("inst-2", "comedero_automatico", 4, 1)
            )
        )

        val snapshotFirmado = MotorCasa.firmarSnapshot(snapshotOriginal, gestor)
        assertNotNull("La firma no debe ser nula tras firmar", snapshotFirmado.firmaPropietario)

        val verificacionValida = MotorCasa.verificarFirmaSnapshot(
            snapshot = snapshotFirmado,
            clavePublicaPropietario = clavePublica,
            gestorIdentidad = gestor
        )
        assertTrue("La firma legítima debe ser verificada exitosamente", verificacionValida)

        // Intento de manipulación tramposa: mover un mueble sin re-firmar
        val snapshotManipulado = snapshotFirmado.copy(
            objetos = listOf(
                ObjetoColocado("inst-1", "cama_futurista", 2, 2), // Modificado
                ObjetoColocado("inst-2", "comedero_automatico", 4, 1)
            )
        )

        val verificacionInvalida = MotorCasa.verificarFirmaSnapshot(
            snapshot = snapshotManipulado,
            clavePublicaPropietario = clavePublica,
            gestorIdentidad = gestor
        )
        assertFalse("Cualquier alteración a la casa debe invalidar la firma", verificacionInvalida)
    }

    // ── 4. Persistencia Local ────────────────────────────────────────────────

    @Test
    fun `repositorio genera casa por defecto y persiste actualizaciones`() {
        val fakePrefs = FakeSharedPreferences()
        val gestor = GestorIdentidadEcdsa(prefsOverride = FakeSharedPreferences())
        val repo = RepositorioCasaLocal(prefsOverride = fakePrefs, gestorIdentidad = gestor)

        val casaInicial = repo.obtenerCasa("propietario-1", "personaje-1")
        assertEquals(4, casaInicial.objetos.size) // Cama, comedero, rascador, alfombra
        assertNotNull(casaInicial.firmaPropietario)

        // Agregar un objeto y guardar
        val nuevoObjeto = ObjetoColocado(UUID.randomUUID().toString(), "consola_arcade", 0, 7)
        val casaModificada = casaInicial.copy(
            version = casaInicial.version + 1,
            objetos = casaInicial.objetos + nuevoObjeto
        )
        repo.guardarCasa(casaModificada)

        val casaRecuperada = repo.obtenerCasa("propietario-1", "personaje-1")
        assertEquals(5, casaRecuperada.objetos.size)
        assertEquals(2, casaRecuperada.version)
        assertTrue(casaRecuperada.objetos.any { it.catalogoId == "consola_arcade" })
    }
}
