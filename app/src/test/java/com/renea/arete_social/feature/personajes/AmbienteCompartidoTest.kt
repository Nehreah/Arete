package com.renea.arete_social.feature.personajes

import com.renea.arete_social.feature.personajes.data.RepositorioPersonajes
import com.renea.arete_social.feature.personajes.logic.MotorAmbiente
import com.renea.arete_social.feature.personajes.logic.ResultadoIngresoAmbiente
import com.renea.arete_social.feature.personajes.model.Recuerdo
import com.renea.arete_social.feature.personajes.model.Relacion
import com.renea.arete_social.feature.personajes.model.Temperamento
import com.renea.arete_social.feature.personajes.model.ambiente.AccionAmbiente
import com.renea.arete_social.feature.personajes.model.ambiente.CatalogoAmbientes
import com.renea.arete_social.feature.personajes.model.ambiente.EventoAmbiente
import com.renea.arete_social.feature.personajes.model.ambiente.PersonajeEnAmbiente
import com.renea.arete_social.feature.personajes.model.ambiente.ProtocoloAmbienteMesh
import com.renea.arete_social.feature.personajes.model.ambiente.TipoEventoAmbiente
import com.renea.arete_social.feature.personajes.model.escena.CoordenadaTile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Suite de pruebas unitarias JVM puras para la Fase 5: Ambientes Compartidos.
 */
class AmbienteCompartidoTest {

    private class FakeRepoPersonajes : RepositorioPersonajes {
        val relaciones = mutableListOf<Relacion>()
        val recuerdos = mutableListOf<Recuerdo>()

        override fun obtenerRelaciones(): List<Relacion> = relaciones.toList()
        override fun obtenerRelacion(otroOwnerId: String): Relacion? = relaciones.find { it.otroOwnerId == otroOwnerId }
        override fun guardarRelacion(relacion: Relacion) {
            relaciones.removeAll { it.otroOwnerId == relacion.otroOwnerId }
            relaciones.add(relacion)
        }
        override fun obtenerRecuerdos(): List<Recuerdo> = recuerdos.toList()
        override fun guardarRecuerdo(recuerdo: Recuerdo) {
            recuerdos.removeAll { it.id == recuerdo.id }
            recuerdos.add(recuerdo)
        }
    }

    private lateinit var repoPersonajes: FakeRepoPersonajes
    private val miOwnerId = "mi-owner-uuid"
    private val miPersonajeId = "mi-personaje-uuid"
    private lateinit var miPersonaje: PersonajeEnAmbiente

    @Before
    fun setUp() {
        repoPersonajes = FakeRepoPersonajes()
        miPersonaje = PersonajeEnAmbiente(
            ownerId = miOwnerId,
            personajeId = miPersonajeId,
            nombre = "Michi",
            temperamento = Temperamento.TRANQUILO,
            posicion = CoordenadaTile(0, 0)
        )
    }

    private fun crearMotor(
        aforoMaximo: Int = 6,
        amigosValidos: Set<String> = emptySet(),
        bloqueados: Set<String> = emptySet()
    ): MotorAmbiente {
        val def = CatalogoAmbientes.PARQUE.copy(aforoMaximo = aforoMaximo)
        return MotorAmbiente(
            definicion = def,
            instanciaId = "parque-test",
            miPersonaje = miPersonaje,
            esAmigo = { amigosValidos.contains(it) },
            esBloqueado = { bloqueados.contains(it) },
            gestorIdentidad = null,
            repoPersonajes = repoPersonajes
        )
    }

    // ── Tests de Aforo ────────────────────────────────────────────────────────

    @Test
    fun `aforo maximo respetado - rechaza ingresos si la sala esta llena`() {
        val amigos = (1..10).map { "amigo-$it" }.toSet()
        val motor = crearMotor(aforoMaximo = 4, amigosValidos = amigos)

        // El motor ya incluye a miPersonaje (1 de 4)
        assertEquals(1, motor.participantes.size)

        // Añadir 3 amigos más (alcanza 4 de 4)
        for (i in 1..3) {
            val res = motor.ingresar(
                PersonajeEnAmbiente(
                    ownerId = "amigo-$i",
                    personajeId = "p-$i",
                    nombre = "Amigo $i",
                    temperamento = Temperamento.SOCIABLE,
                    posicion = CoordenadaTile(i, i)
                )
            )
            assertEquals(ResultadoIngresoAmbiente.EXITO, res)
        }
        assertEquals(4, motor.participantes.size)

        // El 5to amigo debe ser rechazado por aforo lleno
        val resDesborde = motor.ingresar(
            PersonajeEnAmbiente(
                ownerId = "amigo-4",
                personajeId = "p-4",
                nombre = "Amigo 4",
                temperamento = Temperamento.CURIOSO,
                posicion = CoordenadaTile(5, 5)
            )
        )
        assertEquals(ResultadoIngresoAmbiente.RECHAZADO_AFORO_LLENO, resDesborde)
        assertEquals(4, motor.participantes.size)
    }

    // ── Tests de Seguridad y Bloqueos ─────────────────────────────────────────

    @Test
    fun `seguridad - rechaza usuarios no amigos y usuarios bloqueados`() {
        val motor = crearMotor(
            aforoMaximo = 6,
            amigosValidos = setOf("amigo-legitimo"),
            bloqueados = setOf("usuario-toxico")
        )

        // 1. Desconocido (no amigo)
        val resDesconocido = motor.ingresar(
            PersonajeEnAmbiente(
                ownerId = "desconocido",
                personajeId = "p-desc",
                nombre = "Extraño",
                temperamento = Temperamento.ENERGETICO,
                posicion = CoordenadaTile(1, 1)
            )
        )
        assertEquals(ResultadoIngresoAmbiente.RECHAZADO_NO_AMIGO, resDesconocido)

        // 2. Bloqueado
        val resBloqueado = motor.ingresar(
            PersonajeEnAmbiente(
                ownerId = "usuario-toxico",
                personajeId = "p-tox",
                nombre = "Tóxico",
                temperamento = Temperamento.ENERGETICO,
                posicion = CoordenadaTile(1, 1)
            )
        )
        assertEquals(ResultadoIngresoAmbiente.RECHAZADO_BLOQUEO, resBloqueado)

        // 3. Amigo legítimo
        val resAmigo = motor.ingresar(
            PersonajeEnAmbiente(
                ownerId = "amigo-legitimo",
                personajeId = "p-legit",
                nombre = "Amigo Fiel",
                temperamento = Temperamento.TRANQUILO,
                posicion = CoordenadaTile(1, 1)
            )
        )
        assertEquals(ResultadoIngresoAmbiente.EXITO, resAmigo)
    }

    // ── Tests de Movimiento ───────────────────────────────────────────────────

    @Test
    fun `movimiento - valida limites de cuadricula y colisiones con personajes`() {
        val amigoId = "amigo-1"
        val motor = crearMotor(amigosValidos = setOf(amigoId))

        motor.ingresar(
            PersonajeEnAmbiente(
                ownerId = amigoId,
                personajeId = "p-1",
                nombre = "Amigo 1",
                temperamento = Temperamento.TRANQUILO,
                posicion = CoordenadaTile(2, 2)
            )
        )

        // 1. Mover a posición libre válida (1, 1)
        val evtValido = motor.mover(CoordenadaTile(1, 1))
        assertNotNull(evtValido)
        assertEquals(CoordenadaTile(1, 1), motor.miPersonaje.posicion)

        // 2. Mover fuera de límites (15, 15 en mapa de 10x10)
        val evtFueraLimites = motor.mover(CoordenadaTile(15, 15))
        assertNull(evtFueraLimites)
        assertEquals(CoordenadaTile(1, 1), motor.miPersonaje.posicion)

        // 3. Mover a posición ocupada por el amigo (2, 2)
        val evtColision = motor.mover(CoordenadaTile(2, 2))
        assertNull(evtColision)
        assertEquals(CoordenadaTile(1, 1), motor.miPersonaje.posicion)
    }

    // ── Tests de Actividades y Afinidad ───────────────────────────────────────

    @Test
    fun `actividad conjunta - modula confianza segun afinidad y genera recibo y recuerdo`() {
        val amigoId = "amigo-parque"
        val motor = crearMotor(amigosValidos = setOf(amigoId))

        val amigo = PersonajeEnAmbiente(
            ownerId = amigoId,
            personajeId = "p-amigo",
            nombre = "Tom",
            temperamento = Temperamento.TRANQUILO, // Ambos TRANQUILOS = alta afinidad en Parque
            posicion = CoordenadaTile(3, 3)
        )
        motor.ingresar(amigo)

        val resultado = motor.iniciarActividadConjunta(amigoId, AccionAmbiente.PASEAR)
        assertNotNull(resultado)

        val (evento, recibo) = resultado!!
        assertEquals(TipoEventoAmbiente.ACTIVIDAD_CONJUNTA, evento.tipo)
        assertEquals(amigoId, evento.amigoObjetivoOwnerId)

        // Verificar recibo
        assertEquals(amigoId, recibo.participanteBOwnerId)
        assertEquals(miOwnerId, recibo.participanteAOwnerId)
        assertTrue(recibo.confianzaOtorgada > 0)

        // Verificar persistencia de relación y recuerdo
        val relacion = repoPersonajes.obtenerRelacion(amigoId)
        assertNotNull(relacion)
        assertEquals(recibo.confianzaOtorgada, relacion!!.confianza)
        assertEquals(1, relacion.interaccionesTotales)

        val recuerdos = repoPersonajes.obtenerRecuerdos()
        assertEquals(1, recuerdos.size)
        assertEquals(amigo.nombre, recuerdos.first().conQuienNombre)
    }

    @Test
    fun `limite diario de 5 actividades - rechaza la sexta interaccion`() {
        val amigoId = "amigo-frecuente"
        val motor = crearMotor(amigosValidos = setOf(amigoId))

        motor.ingresar(
            PersonajeEnAmbiente(
                ownerId = amigoId,
                personajeId = "p-amigo",
                nombre = "Sam",
                temperamento = Temperamento.ENERGETICO,
                posicion = CoordenadaTile(4, 4)
            )
        )

        // Ejecutar 5 actividades consecutivas exitosas
        for (i in 1..5) {
            val res = motor.iniciarActividadConjunta(amigoId, AccionAmbiente.JUGAR_PELOTA)
            assertNotNull("La interacción $i debió ser exitosa", res)
        }

        // La 6ta interacción debe ser rechazada por límite diario
        val resSexta = motor.iniciarActividadConjunta(amigoId, AccionAmbiente.JUGAR_PELOTA)
        assertNull("La 6ta interacción debe ser rechazada por límite diario", resSexta)

        val relacion = repoPersonajes.obtenerRelacion(amigoId)
        assertEquals(5, relacion?.interaccionesHoy)
        assertEquals(5, relacion?.interaccionesTotales)
    }

    // ── Tests de Moderación ───────────────────────────────────────────────────

    @Test
    fun `moderacion - silenciar, desilenciar y salir de la sala`() {
        val amigoId = "amigo-ruidoso"
        val motor = crearMotor(amigosValidos = setOf(amigoId))

        motor.ingresar(
            PersonajeEnAmbiente(
                ownerId = amigoId,
                personajeId = "p-amigo",
                nombre = "Spammy",
                temperamento = Temperamento.SOCIABLE,
                posicion = CoordenadaTile(1, 1)
            )
        )

        // Silenciar
        assertFalse(motor.estaSilenciado(amigoId))
        motor.silenciar(amigoId)
        assertTrue(motor.estaSilenciado(amigoId))

        // Desilenciar
        motor.desilenciar(amigoId)
        assertFalse(motor.estaSilenciado(amigoId))

        // Salir de la sala
        val evtSalida = motor.salir()
        assertEquals(TipoEventoAmbiente.SALE, evtSalida.tipo)
        assertFalse(motor.participantes.any { it.ownerId == miOwnerId })
    }

    // ── Tests de Protocolo Mesh ───────────────────────────────────────────────

    @Test
    fun `protocolo mesh - serializa y deserializa eventos de ambiente fielmente`() {
        val eventoOriginal = EventoAmbiente(
            id = UUID.randomUUID().toString(),
            tipo = TipoEventoAmbiente.ACCION,
            instanciaId = "parque-comunidad-01",
            emisorOwnerId = "usr-01",
            emisorPersonajeId = "pet-01",
            emisorNombre = "Michi",
            temperamento = Temperamento.ARTISTA,
            posicion = CoordenadaTile(3, 4),
            accion = AccionAmbiente.DIBUJAR_BOCETO
        )

        val codificado = ProtocoloAmbienteMesh.codificarEvento(eventoOriginal)
        assertTrue(codificado.startsWith("[AMBIENTE_EVT:v1:"))

        val decodificado = ProtocoloAmbienteMesh.parsearEvento(codificado)
        assertNotNull(decodificado)
        assertEquals(eventoOriginal.id, decodificado!!.id)
        assertEquals(eventoOriginal.tipo, decodificado.tipo)
        assertEquals(eventoOriginal.accion, decodificado.accion)
        assertEquals(eventoOriginal.temperamento, decodificado.temperamento)
        assertEquals(eventoOriginal.posicion, decodificado.posicion)
    }
}
