package com.renea.arete_social.feature.personajes.model.ambiente

import com.renea.arete_social.feature.personajes.model.Temperamento
import com.renea.arete_social.feature.personajes.model.escena.CoordenadaTile
import com.renea.arete_social.feature.personajes.model.escena.ObjetoColocado
import kotlinx.serialization.Serializable

/**
 * Tipos de ambientes compartidos disponibles en la comunidad.
 */
@Serializable
enum class TipoAmbiente(val titulo: String, val descripcionCorta: String) {
    PARQUE("Parque Municipal", "Espacio verde al aire libre para relajarse y charlar."),
    GIMNASIO("Gimnasio Fitness", "Área deportiva para entrenar y quemar energía."),
    CAFETERIA("Cafetería Retro", "Lugar acogedor para disfrutar bocadillos y conversar.")
}

/**
 * Actividades que los personajes pueden realizar en los ambientes compartidos.
 */
@Serializable
enum class AccionAmbiente(
    val titulo: String,
    val descripcion: String,
    val temperamentoAfin: Temperamento,
    val puntosConfianzaBase: Int = 3
) {
    // Actividades de Parque
    PASEAR("Paseo tranquilo", "Caminar junto a los senderos y flores", Temperamento.TRANQUILO, 3),
    JUGAR_PELOTA("Juego de pelota", "Lanzar la pelota y correr en el césped", Temperamento.ENERGETICO, 4),
    DESCANSAR_BANCA("Descansar en la banca", "Tomar aire fresco y relajarse", Temperamento.TRANQUILO, 2),
    EXPLORAR_NATURALEZA("Explorar flores y bichos", "Inspeccionar las plantas del parque", Temperamento.CURIOSO, 3),

    // Actividades de Gimnasio
    ENTRENAR_CINTA("Correr en la cinta", "Sesión de cardio a todo ritmo", Temperamento.ENERGETICO, 4),
    LEVANTAR_PESAS("Levantar pesas", "Fortalecer músculos y celebrar logros", Temperamento.ENERGETICO, 4),
    ESTIRAMIENTOS("Estiramientos en colchoneta", "Calistenia suave y recuperación", Temperamento.TRANQUILO, 2),

    // Actividades de Cafetería
    TOMAR_CAFE("Tomar bebida caliente", "Saborear una taza de café o té", Temperamento.TRANQUILO, 3),
    MERENDAR("Degustar un postre", "Probar un delicioso pastelito o galleta", Temperamento.GLOTON, 4),
    CHARLAR_MESA("Charla entretenida", "Compartir anécdotas e historias", Temperamento.SOCIABLE, 4),
    DIBUJAR_BOCETO("Dibujar en servilleta", "Inspiración artística en la mesa", Temperamento.ARTISTA, 4)
}

/**
 * Especificación inmutable de la configuración de un ambiente (definido solo por datos).
 */
@Serializable
data class AmbienteDefinicion(
    val tipo: TipoAmbiente,
    val nombre: String,
    val anchoTiles: Int = 8,
    val altoTiles: Int = 8,
    val aforoMaximo: Int = 6,
    val objetosFijos: List<ObjetoColocado> = emptyList(),
    val accionesDisponibles: List<AccionAmbiente> = emptyList()
)

/**
 * Estado en tiempo real de un personaje dentro de un ambiente compartido.
 */
@Serializable
data class PersonajeEnAmbiente(
    val ownerId: String,
    val personajeId: String,
    val nombre: String,
    val temperamento: Temperamento,
    val posicion: CoordenadaTile,
    val accionActual: AccionAmbiente? = null,
    val timestampActualizacion: Long = System.currentTimeMillis()
)

/**
 * Tipo de evento de ambiente transmitido entre pares (mesh/local).
 */
@Serializable
enum class TipoEventoAmbiente {
    ENTRA,
    MOVER,
    ACCION,
    ACTIVIDAD_CONJUNTA,
    SALE
}

/**
 * Paquete de evento ligero transmitido en el ambiente compartido.
 */
@Serializable
data class EventoAmbiente(
    val id: String,
    val tipo: TipoEventoAmbiente,
    val instanciaId: String,
    val emisorOwnerId: String,
    val emisorPersonajeId: String,
    val emisorNombre: String,
    val temperamento: Temperamento,
    val posicion: CoordenadaTile,
    val accion: AccionAmbiente? = null,
    val amigoObjetivoOwnerId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val firmaEmisor: String? = null
)

/**
 * Recibo firmado mutuamente por dos personajes que concluyeron con éxito una actividad conjunta.
 * Indispensable para actualizar confianza y registrar el recuerdo oficial en el almacenamiento local.
 */
@Serializable
data class ReciboActividadAmbiente(
    val actividadId: String,
    val ambienteTipo: TipoAmbiente,
    val participanteAOwnerId: String,
    val participanteBOwnerId: String,
    val accion: AccionAmbiente,
    val confianzaOtorgada: Int,
    val timestamp: Long,
    val firmaParticipanteA: String? = null,
    val firmaParticipanteB: String? = null
) {
    /**
     * Cadena canónica para firma ECDSA.
     */
    fun datosParaFirmar(): String = buildString {
        append("antired-actividad-ambiente-v1|")
        append(actividadId).append('|')
        append(ambienteTipo.name).append('|')
        append(participanteAOwnerId).append('|')
        append(participanteBOwnerId).append('|')
        append(accion.name).append('|')
        append(confianzaOtorgada).append('|')
        append(timestamp)
    }
}
