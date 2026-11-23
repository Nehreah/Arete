package com.renea.arete_social.domain.model

/**
 * Tope de una zona regional: un lugar o evento solo cuenta como «de la región» si además de
 * estarle más cerca a su centro que a cualquier otra región, queda a menos de esta distancia.
 * Evita que una región se «coma» medio país.
 */
const val ZONA_REGIONAL_RADIO_MAX_M = 100_000.0

/**
 * Ciudad principal con su propio chat regional. El chat regional existe de verdad en la tabla
 * `chats` (tipo REGIONAL), porque `mensajes.chat_id` exige que el chat exista.
 */
data class Region(
    val id: String,
    val nombre: String,
    val ubicacion: Ubicacion,
    val chatId: String
)

/** Una región con el radio máximo de su zona. */
data class ZonaRegional(
    val region: Region,
    val radioMaxMetros: Double = ZONA_REGIONAL_RADIO_MAX_M
)

/**
 * Regiones embebidas en la app: catálogo predeterminado de regiones y zonas locales.
 * Los identificadores son deterministas y compartidos para operar de forma descentralizada.
 *
 * Coordenadas aproximadas del centro de cada ciudad.
 */
object RegionesPredeterminadas {
    private fun region(n: Int, nombre: String, lat: Double, lng: Double) = Region(
        id = "00000000-0000-4000-8000-00000000b10$n",
        nombre = nombre,
        ubicacion = Ubicacion(lat, lng, 0L),
        chatId = "00000000-0000-4000-8000-00000000b00$n"
    )

    val todas: List<Region> = listOf(
        region(1, "Cali", 3.4516, -76.5320),
        region(2, "Bogotá", 4.7110, -74.0721),
        region(3, "Medellín", 6.2442, -75.5812),
        region(4, "Barranquilla", 10.9685, -74.7813),
        region(5, "Cartagena", 10.3910, -75.4794),
        region(6, "Bucaramanga", 7.1193, -73.1227)
    )
}
