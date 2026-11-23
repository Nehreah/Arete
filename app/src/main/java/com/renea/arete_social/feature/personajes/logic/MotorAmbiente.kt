package com.renea.arete_social.feature.personajes.logic

import com.renea.arete_social.data.crypto.GestorIdentidadEcdsa
import com.renea.arete_social.feature.personajes.data.RepositorioPersonajes
import com.renea.arete_social.feature.personajes.model.Recuerdo
import com.renea.arete_social.feature.personajes.model.Relacion
import com.renea.arete_social.feature.personajes.model.TipoRecuerdo
import com.renea.arete_social.feature.personajes.model.ambiente.AccionAmbiente
import com.renea.arete_social.feature.personajes.model.ambiente.AmbienteDefinicion
import com.renea.arete_social.feature.personajes.model.ambiente.CatalogoAmbientes
import com.renea.arete_social.feature.personajes.model.ambiente.EventoAmbiente
import com.renea.arete_social.feature.personajes.model.ambiente.PersonajeEnAmbiente
import com.renea.arete_social.feature.personajes.model.ambiente.ReciboActividadAmbiente
import com.renea.arete_social.feature.personajes.model.ambiente.TipoAmbiente
import com.renea.arete_social.feature.personajes.model.ambiente.TipoEventoAmbiente
import com.renea.arete_social.feature.personajes.model.escena.CoordenadaTile
import java.util.UUID

/**
 * Resultado de intentar unirse a una instancia de ambiente compartido.
 */
enum class ResultadoIngresoAmbiente {
    EXITO,
    RECHAZADO_AFORO_LLENO,
    RECHAZADO_BLOQUEO,
    RECHAZADO_NO_AMIGO,
    RECHAZADO_POSICION_INVALIDA
}

/**
 * Motor autoritativo local para la simulación, presencia y actividades en un ambiente compartido.
 *
 * Cada cliente simula autoritativamente a su propio personaje y difunde eventos ligeros.
 * Protege contra trampas, aforo desbordado, spam y usuarios bloqueados.
 */
class MotorAmbiente(
    val definicion: AmbienteDefinicion,
    val instanciaId: String,
    var miPersonaje: PersonajeEnAmbiente,
    private val esAmigo: (ownerId: String) -> Boolean = { true },
    private val esBloqueado: (ownerId: String) -> Boolean = { false },
    private val gestorIdentidad: GestorIdentidadEcdsa? = null,
    private val repoPersonajes: RepositorioPersonajes? = null
) {
    private val _participantes = mutableMapOf<String, PersonajeEnAmbiente>()
    private val _silenciados = mutableSetOf<String>()

    init {
        // El usuario local ingresa a la sala
        _participantes[miPersonaje.ownerId] = miPersonaje
    }

    /**
     * Lista inmutable de todos los personajes presentes (excluyendo o incluyendo silenciados según sea necesario).
     */
    val participantes: List<PersonajeEnAmbiente>
        @Synchronized get() = _participantes.values.toList()

    /**
     * Conjunto de IDs de usuarios silenciados localmente.
     */
    val silenciados: Set<String>
        @Synchronized get() = _silenciados.toSet()

    /**
     * Valida e ingresa a un personaje nuevo en la sala.
     */
    @Synchronized
    fun ingresar(personaje: PersonajeEnAmbiente): ResultadoIngresoAmbiente {
        if (esBloqueado(personaje.ownerId)) {
            return ResultadoIngresoAmbiente.RECHAZADO_BLOQUEO
        }
        if (personaje.ownerId != miPersonaje.ownerId && !esAmigo(personaje.ownerId)) {
            return ResultadoIngresoAmbiente.RECHAZADO_NO_AMIGO
        }
        if (!_participantes.containsKey(personaje.ownerId) && _participantes.size >= definicion.aforoMaximo) {
            return ResultadoIngresoAmbiente.RECHAZADO_AFORO_LLENO
        }
        if (!posicionValida(personaje.posicion)) {
            return ResultadoIngresoAmbiente.RECHAZADO_POSICION_INVALIDA
        }

        _participantes[personaje.ownerId] = personaje
        return ResultadoIngresoAmbiente.EXITO
    }

    /**
     * Mueve el personaje local a una nueva coordenada de la cuadrícula.
     */
    @Synchronized
    fun mover(nuevaPos: CoordenadaTile): EventoAmbiente? {
        if (!posicionValida(nuevaPos)) return null
        if (posicionOcupada(nuevaPos, excluirOwnerId = miPersonaje.ownerId)) return null

        miPersonaje = miPersonaje.copy(
            posicion = nuevaPos,
            timestampActualizacion = System.currentTimeMillis()
        )
        _participantes[miPersonaje.ownerId] = miPersonaje

        return crearEvento(TipoEventoAmbiente.MOVER, posicion = nuevaPos)
    }

    /**
     * Cambia la acción o actividad actual del personaje local.
     */
    @Synchronized
    fun cambiarAccion(accion: AccionAmbiente?): EventoAmbiente {
        miPersonaje = miPersonaje.copy(
            accionActual = accion,
            timestampActualizacion = System.currentTimeMillis()
        )
        _participantes[miPersonaje.ownerId] = miPersonaje

        return crearEvento(TipoEventoAmbiente.ACCION, accion = accion)
    }

    /**
     * Procesa un evento remoto recibido desde la red mesh/local.
     */
    @Synchronized
    fun procesarEventoRemoto(evento: EventoAmbiente): Boolean {
        if (evento.instanciaId != instanciaId) return false
        if (esBloqueado(evento.emisorOwnerId)) return false

        when (evento.tipo) {
            TipoEventoAmbiente.ENTRA -> {
                val p = PersonajeEnAmbiente(
                    ownerId = evento.emisorOwnerId,
                    personajeId = evento.emisorPersonajeId,
                    nombre = evento.emisorNombre,
                    temperamento = evento.temperamento,
                    posicion = evento.posicion,
                    accionActual = evento.accion,
                    timestampActualizacion = evento.timestamp
                )
                return ingresar(p) == ResultadoIngresoAmbiente.EXITO
            }

            TipoEventoAmbiente.MOVER -> {
                val actual = _participantes[evento.emisorOwnerId] ?: return false
                if (!posicionValida(evento.posicion)) return false
                _participantes[evento.emisorOwnerId] = actual.copy(
                    posicion = evento.posicion,
                    timestampActualizacion = evento.timestamp
                )
                return true
            }

            TipoEventoAmbiente.ACCION -> {
                val actual = _participantes[evento.emisorOwnerId] ?: return false
                _participantes[evento.emisorOwnerId] = actual.copy(
                    accionActual = evento.accion,
                    timestampActualizacion = evento.timestamp
                )
                return true
            }

            TipoEventoAmbiente.SALE -> {
                _participantes.remove(evento.emisorOwnerId)
                return true
            }

            TipoEventoAmbiente.ACTIVIDAD_CONJUNTA -> {
                // Notificación o confirmación de actividad con miPersonaje
                return evento.amigoObjetivoOwnerId == miPersonaje.ownerId
            }
        }
    }

    /**
     * Inicia una actividad conjunta con otro personaje amigo presente en la sala.
     * Retorna el evento a difundir y el recibo de actividad firmado.
     */
    @Synchronized
    fun iniciarActividadConjunta(
        amigoOwnerId: String,
        accion: AccionAmbiente
    ): Pair<EventoAmbiente, ReciboActividadAmbiente>? {
        val amigo = _participantes[amigoOwnerId] ?: return null
        if (esBloqueado(amigoOwnerId) || !esAmigo(amigoOwnerId)) return null

        val tipoActividad = mapearAccionATipoActividad(accion)
        val relacionPrevia = repoPersonajes?.obtenerRelacion(amigoOwnerId) ?: Relacion(
            otroPersonajeId = amigo.personajeId,
            otroOwnerId = amigo.ownerId,
            nombreOtro = amigo.nombre
        )

        val resultado = ReglasAfinidad.aplicarInteraccion(
            relacion = relacionPrevia,
            actividad = tipoActividad,
            miTemperamento = miPersonaje.temperamento,
            otroTemperamento = amigo.temperamento
        )
        if (!resultado.exito) return null

        val actividadId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        var recibo = ReciboActividadAmbiente(
            actividadId = actividadId,
            ambienteTipo = definicion.tipo,
            participanteAOwnerId = miPersonaje.ownerId,
            participanteBOwnerId = amigo.ownerId,
            accion = accion,
            confianzaOtorgada = resultado.puntosConfianzaGanados,
            timestamp = timestamp
        )

        // Firmar digitalmente con la clave ECDSA local
        gestorIdentidad?.let { gestor ->
            val firma = gestor.firmar(recibo.datosParaFirmar())
            recibo = recibo.copy(firmaParticipanteA = firma)
        }

        // Actualizar repositorio local
        repoPersonajes?.guardarRelacion(resultado.relacionActualizada)
        repoPersonajes?.guardarRecuerdo(
            Recuerdo(
                id = actividadId,
                fechaMillis = timestamp,
                tipo = mapearTipoRecuerdo(definicion.tipo),
                conQuienId = amigo.ownerId,
                conQuienNombre = amigo.nombre,
                lugar = definicion.nombre,
                textoCorto = "Compartimos ${accion.titulo} en ${definicion.nombre}",
                firmaMutua = recibo.firmaParticipanteA
            )
        )

        val evento = crearEvento(
            tipo = TipoEventoAmbiente.ACTIVIDAD_CONJUNTA,
            accion = accion,
            amigoObjetivoOwnerId = amigoOwnerId
        )

        return evento to recibo
    }

    /**
     * Silencia localmente a un participante de la sala.
     */
    @Synchronized
    fun silenciar(ownerId: String) {
        if (ownerId != miPersonaje.ownerId) {
            _silenciados.add(ownerId)
        }
    }

    /**
     * Quita el silenciado a un participante.
     */
    @Synchronized
    fun desilenciar(ownerId: String) {
        _silenciados.remove(ownerId)
    }

    /**
     * Verifica si un participante está silenciado.
     */
    @Synchronized
    fun estaSilenciado(ownerId: String): Boolean = _silenciados.contains(ownerId)

    /**
     * Sale del ambiente compartido y emite el evento de salida.
     */
    @Synchronized
    fun salir(): EventoAmbiente {
        _participantes.remove(miPersonaje.ownerId)
        return crearEvento(TipoEventoAmbiente.SALE)
    }

    // ── Validaciones internas ─────────────────────────────────────────────────

    private fun posicionValida(pos: CoordenadaTile): Boolean =
        pos.x in 0 until definicion.anchoTiles && pos.y in 0 until definicion.altoTiles

    private fun posicionOcupada(pos: CoordenadaTile, excluirOwnerId: String? = null): Boolean {
        // Revisar si choca con otro personaje
        val chocaConPersonaje = _participantes.values.any {
            it.ownerId != excluirOwnerId && it.posicion == pos
        }
        if (chocaConPersonaje) return true

        // Revisar si choca con objetos fijos no transitables
        return definicion.objetosFijos.any { it.x == pos.x && it.y == pos.y }
    }

    private fun crearEvento(
        tipo: TipoEventoAmbiente,
        posicion: CoordenadaTile = miPersonaje.posicion,
        accion: AccionAmbiente? = miPersonaje.accionActual,
        amigoObjetivoOwnerId: String? = null
    ): EventoAmbiente {
        var evento = EventoAmbiente(
            id = UUID.randomUUID().toString(),
            tipo = tipo,
            instanciaId = instanciaId,
            emisorOwnerId = miPersonaje.ownerId,
            emisorPersonajeId = miPersonaje.personajeId,
            emisorNombre = miPersonaje.nombre,
            temperamento = miPersonaje.temperamento,
            posicion = posicion,
            accion = accion,
            amigoObjetivoOwnerId = amigoObjetivoOwnerId,
            timestamp = System.currentTimeMillis()
        )

        gestorIdentidad?.let { gestor ->
            val payload = "${evento.id}|${evento.tipo}|${evento.instanciaId}|${evento.emisorOwnerId}|${evento.timestamp}"
            val firma = gestor.firmar(payload)
            evento = evento.copy(firmaEmisor = firma)
        }

        return evento
    }

    private fun mapearAccionATipoActividad(accion: AccionAmbiente): TipoActividad = when (accion) {
        AccionAmbiente.PASEAR,
        AccionAmbiente.DESCANSAR_BANCA,
        AccionAmbiente.EXPLORAR_NATURALEZA,
        AccionAmbiente.JUGAR_PELOTA -> TipoActividad.PASEAR_PARQUE

        AccionAmbiente.ENTRENAR_CINTA,
        AccionAmbiente.LEVANTAR_PESAS,
        AccionAmbiente.ESTIRAMIENTOS -> TipoActividad.ENTRENAR_GIMNASIO

        AccionAmbiente.TOMAR_CAFE,
        AccionAmbiente.CHARLAR_MESA,
        AccionAmbiente.DIBUJAR_BOCETO -> TipoActividad.CHARLAR_CAFETERIA

        AccionAmbiente.MERENDAR -> TipoActividad.COMER_JUNTOS
    }

    private fun mapearTipoRecuerdo(ambiente: TipoAmbiente): TipoRecuerdo = when (ambiente) {
        TipoAmbiente.PARQUE -> TipoRecuerdo.ACTIVIDAD_PARQUE
        TipoAmbiente.GIMNASIO -> TipoRecuerdo.ENTRENAMIENTO_GIMNASIO
        TipoAmbiente.CAFETERIA -> TipoRecuerdo.CHARLA_CAFETERIA
    }
}
