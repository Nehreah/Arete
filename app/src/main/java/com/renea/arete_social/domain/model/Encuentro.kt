package com.renea.arete_social.domain.model

enum class EstadoEncuentro {
    PENDIENTE,
    ACTIVO,
    RECHAZADO,
    TERMINADO,
    EXPIRADO
}

enum class ModoRuta(val etiqueta: String, val velocidadMs: Double) {
    WALKING("A pie", 1.4),       // ~5 km/h
    BICYCLING("Bicicleta", 4.5), // ~16 km/h
    DRIVING("Auto", 8.5)         // ~30 km/h en ciudad
}

/**
 * Trazado entre las dos personas de un encuentro.
 * [porCalles] es false cuando no se pudo consultar el servicio de rutas (sin red, servicio caído):
 * entonces [puntos] es una línea recta y [distanciaMetros] es la distancia en línea recta.
 */
data class RutaEncuentro(
    val puntos: List<Ubicacion>,
    val distanciaMetros: Double,
    val porCalles: Boolean
)

/**
 * Encuentro en curso entre yo y una amistad, desde que lo pido hasta que termina.
 * - [EstadoEncuentro.PENDIENTE]: se envió la solicitud y la otra persona aún no responde; no se comparte ubicación.
 * - [EstadoEncuentro.ACTIVO]: la otra persona aceptó; ambas se ven en el mapa y hay [ruta].
 */
data class SesionEncuentro(
    val id: String,
    val amigoId: String,
    val amigoNombre: String,
    val amigoUbicacion: Ubicacion? = null,
    val estado: EstadoEncuentro = EstadoEncuentro.PENDIENTE,
    val modo: ModoRuta = ModoRuta.WALKING,
    /** true si yo pedí el encuentro; false si me invitaron y acepté. */
    val soySolicitante: Boolean = true,
    val ruta: RutaEncuentro? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/** Invitación a un encuentro que me enviaron y aún no respondí. */
data class SolicitudEncuentro(
    val id: String,
    val remitenteId: String,
    val remitenteNombre: String,
    val timestamp: Long
)

/** Fila de un encuentro tal como la guarda el servidor (sin nombres ni ubicaciones). */
data class EncuentroRemoto(
    val id: String,
    val solicitanteId: String,
    val invitadoId: String,
    val estado: EstadoEncuentro,
    /** Cuándo vence (milisegundos epoch). Una solicitud sin responder vence a los 10 min; un encuentro activo, 60 min después de aceptarlo. */
    val expiraEnMillis: Long,
    val creadoEnMillis: Long
)
