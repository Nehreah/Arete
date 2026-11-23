package com.renea.arete_social.feature.personajes.model.escena

import kotlinx.serialization.Serializable

/**
 * Coordenada entera dentro de la cuadrícula de la escena (0..7).
 */
@Serializable
data class CoordenadaTile(val x: Int, val y: Int)

/**
 * Categoría funcional a la que pertenece un objeto de la escena.
 */
@Serializable
enum class CategoriaObjeto(val nombreVisible: String) {
    DESCANSO("Descanso"),
    ALIMENTACION("Alimentación"),
    HIGIENE("Higiene"),
    DIVERSION("Diversión"),
    MUEBLE("Muebles"),
    DECORACION("Decoración")
}

/**
 * Acciones interactivas que el personaje puede ejecutar sobre un objeto.
 */
@Serializable
enum class AccionObjeto(val titulo: String, val mensajeMichi: String) {
    DORMIR("Dormir en la cama", "¡Qué rico descanso! Energía recuperada."),
    COMER("Comer platillo", "¡Delicioso bocadillo! Hambre satisfecha."),
    ASEARSE("Aseo y espejo", "¡Impecable y reluciente! Higiene al máximo."),
    JUGAR("Jugar un rato", "¡Super divertido! Felicidad por las nubes."),
    LEER("Leer hololibro", "Aprendiendo cosas nuevas y relajándome."),
    ADMIRAR("Admirar y regar", "Un toque de calma y belleza para el hogar.")
}

/**
 * Definición estática de un objeto del catálogo oficial de la app.
 */
@Serializable
data class ObjetoCatalogo(
    val id: String,
    val nombre: String,
    val categoria: CategoriaObjeto,
    val anchoTiles: Int = 1,
    val altoTiles: Int = 1,
    val nivelMinimo: Int = 1,
    val acciones: List<AccionObjeto> = emptyList(),
    val descripcion: String = ""
)

/**
 * Objeto instanciado y posicionado dentro de la casa.
 *
 * @param instanciaId UUID único para diferenciar múltiples objetos idénticos.
 * @param catalogoId Referencia a [ObjetoCatalogo.id].
 * @param x Coordenada X superior izquierda (0..7).
 * @param y Coordenada Y superior izquierda (0..7).
 * @param rotacion Orientación en grados (0, 90, 180, 270).
 */
@Serializable
data class ObjetoColocado(
    val instanciaId: String,
    val catalogoId: String,
    val x: Int,
    val y: Int,
    val rotacion: Int = 0
) {
    /** Retorna todas las celdas (x, y) que ocupa este objeto en la cuadrícula. */
    fun celdasOcupadas(ancho: Int, alto: Int): List<CoordenadaTile> {
        val celdas = mutableListOf<CoordenadaTile>()
        for (dx in 0 until ancho) {
            for (dy in 0 until alto) {
                celdas.add(CoordenadaTile(x + dx, y + dy))
            }
        }
        return celdas
    }
}

/**
 * Instantánea persistente y compartible del estado de la casa de un personaje.
 *
 * Se firma digitalmente con la identidad ECDSA del dueño para prevenir trampas
 * al ser compartida con amigos durante las visitas.
 */
@Serializable
data class CasaSnapshot(
    val propietarioId: String,
    val personajeId: String,
    val version: Int = 1,
    val temaId: String = "tema_pixel_013",
    val objetos: List<ObjetoColocado> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val firmaPropietario: String? = null
) {
    /**
     * Cadena canónica para sellado con firma digital ECDSA.
     */
    fun datosFirmados(): String = buildString {
        append("antired-casa-v1|")
        append(propietarioId).append('|')
        append(personajeId).append('|')
        append(version).append('|')
        append(temaId).append('|')
        append(timestamp).append('|')
        // Lista ordenada canónica de objetos: id:catId:x:y:rot
        val objOrdenados = objetos.sortedBy { it.instanciaId }
        for (o in objOrdenados) {
            append(o.instanciaId).append(':')
                .append(o.catalogoId).append(':')
                .append(o.x).append(':')
                .append(o.y).append(':')
                .append(o.rotacion).append(';')
        }
    }

    companion object {
        const val MAX_OBJETOS = 16
        const val GRID_TAMANO = 8
    }
}
