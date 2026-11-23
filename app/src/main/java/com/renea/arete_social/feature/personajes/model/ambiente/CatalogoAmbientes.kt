package com.renea.arete_social.feature.personajes.model.ambiente

import com.renea.arete_social.feature.personajes.model.escena.ObjetoColocado

/**
 * Catálogo central de ambientes compartidos definidos puramente por datos.
 * Nuevos ambientes (como bibliotecas, playas o discotecas) se agregan aquí como datos
 * sin necesidad de alterar el motor de lógica ni de render.
 */
object CatalogoAmbientes {

    val PARQUE = AmbienteDefinicion(
        tipo = TipoAmbiente.PARQUE,
        nombre = "Parque del Lago",
        anchoTiles = 10,
        altoTiles = 10,
        aforoMaximo = 6,
        objetosFijos = listOf(
            // Bancas perimetrales
            ObjetoColocado("banca_norte", "mueble_banca", x = 2, y = 1),
            ObjetoColocado("banca_sur", "mueble_banca", x = 6, y = 8),
            // Árboles y sombra
            ObjetoColocado("arbol_izq", "dec_arbol", x = 1, y = 5),
            ObjetoColocado("arbol_der", "dec_arbol", x = 8, y = 3),
            // Fuente decorativa
            ObjetoColocado("fuente_centro", "dec_fuente", x = 4, y = 4)
        ),
        accionesDisponibles = listOf(
            AccionAmbiente.PASEAR,
            AccionAmbiente.JUGAR_PELOTA,
            AccionAmbiente.DESCANSAR_BANCA,
            AccionAmbiente.EXPLORAR_NATURALEZA
        )
    )

    val GIMNASIO = AmbienteDefinicion(
        tipo = TipoAmbiente.GIMNASIO,
        nombre = "Gimnasio Michi-Fit",
        anchoTiles = 8,
        altoTiles = 8,
        aforoMaximo = 6,
        objetosFijos = listOf(
            ObjetoColocado("cinta_1", "gym_cinta", x = 1, y = 1),
            ObjetoColocado("cinta_2", "gym_cinta", x = 3, y = 1),
            ObjetoColocado("pesas_rack", "gym_pesas", x = 6, y = 2),
            ObjetoColocado("colchoneta", "gym_colchoneta", x = 2, y = 5)
        ),
        accionesDisponibles = listOf(
            AccionAmbiente.ENTRENAR_CINTA,
            AccionAmbiente.LEVANTAR_PESAS,
            AccionAmbiente.ESTIRAMIENTOS
        )
    )

    val CAFETERIA = AmbienteDefinicion(
        tipo = TipoAmbiente.CAFETERIA,
        nombre = "Café Pixel Chix",
        anchoTiles = 8,
        altoTiles = 8,
        aforoMaximo = 6,
        objetosFijos = listOf(
            ObjetoColocado("barra_cafe", "cafe_barra", x = 1, y = 1),
            ObjetoColocado("mesa_1", "cafe_mesa", x = 2, y = 5),
            ObjetoColocado("mesa_2", "cafe_mesa", x = 5, y = 5),
            ObjetoColocado("maquina_espresso", "cafe_maquina", x = 5, y = 1)
        ),
        accionesDisponibles = listOf(
            AccionAmbiente.TOMAR_CAFE,
            AccionAmbiente.MERENDAR,
            AccionAmbiente.CHARLAR_MESA,
            AccionAmbiente.DIBUJAR_BOCETO
        )
    )

    val TODOS: List<AmbienteDefinicion> = listOf(PARQUE, GIMNASIO, CAFETERIA)

    fun obtener(tipo: TipoAmbiente): AmbienteDefinicion = when (tipo) {
        TipoAmbiente.PARQUE -> PARQUE
        TipoAmbiente.GIMNASIO -> GIMNASIO
        TipoAmbiente.CAFETERIA -> CAFETERIA
    }
}
