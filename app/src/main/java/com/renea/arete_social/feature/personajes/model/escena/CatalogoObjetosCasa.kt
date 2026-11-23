package com.renea.arete_social.feature.personajes.model.escena

/**
 * Catálogo inmutable de objetos disponibles para la casa interactiva de Michi.
 *
 * Los objetos se referencian únicamente por ID, garantizando que el estado
 * y las instantáneas compartidas en la red sean compactas y seguras.
 */
object CatalogoObjetosCasa {

    private val CATALOGO = listOf(
        ObjetoCatalogo(
            id = "cama_futurista",
            nombre = "Cama Cápsula Cyber",
            categoria = CategoriaObjeto.DESCANSO,
            anchoTiles = 2,
            altoTiles = 1,
            nivelMinimo = 1,
            acciones = listOf(AccionObjeto.DORMIR),
            descripcion = "Cápsula acolchada con control de temperatura para descansar plácidamente."
        ),
        ObjetoCatalogo(
            id = "cama_cojin",
            nombre = "Cojín Redondo Neón",
            categoria = CategoriaObjeto.DESCANSO,
            anchoTiles = 1,
            altoTiles = 1,
            nivelMinimo = 1,
            acciones = listOf(AccionObjeto.DORMIR),
            descripcion = "Cojín suave ideal para siestas rápidas en cualquier rincón."
        ),
        ObjetoCatalogo(
            id = "comedero_automatico",
            nombre = "Comedero Automático",
            categoria = CategoriaObjeto.ALIMENTACION,
            anchoTiles = 1,
            altoTiles = 1,
            nivelMinimo = 1,
            acciones = listOf(AccionObjeto.COMER),
            descripcion = "Dispensador programable con croquetas premium recién servidas."
        ),
        ObjetoCatalogo(
            id = "dispensador_snacks",
            nombre = "Minibar de Snacks",
            categoria = CategoriaObjeto.ALIMENTACION,
            anchoTiles = 1,
            altoTiles = 1,
            nivelMinimo = 2,
            acciones = listOf(AccionObjeto.COMER),
            descripcion = "Refrigerador compacto lleno de premios y golosinas crujientes."
        ),
        ObjetoCatalogo(
            id = "espejo_neon",
            nombre = "Espejo LED Inteligente",
            categoria = CategoriaObjeto.HIGIENE,
            anchoTiles = 1,
            altoTiles = 1,
            nivelMinimo = 1,
            acciones = listOf(AccionObjeto.ASEARSE),
            descripcion = "Espejo interactivo con luces perimetrales para peinarse y acicalarse."
        ),
        ObjetoCatalogo(
            id = "cabina_ducha_cyber",
            nombre = "Cabina de Vapor Sónico",
            categoria = CategoriaObjeto.HIGIENE,
            anchoTiles = 1,
            altoTiles = 2,
            nivelMinimo = 3,
            acciones = listOf(AccionObjeto.ASEARSE),
            descripcion = "Ducha sónica que deja el pelaje impecable y desinfectado en segundos."
        ),
        ObjetoCatalogo(
            id = "rascador_poste",
            nombre = "Poste Rascador Neón",
            categoria = CategoriaObjeto.DIVERSION,
            anchoTiles = 1,
            altoTiles = 1,
            nivelMinimo = 1,
            acciones = listOf(AccionObjeto.JUGAR),
            descripcion = "Poste resistente para afilarse las garras y liberar energía."
        ),
        ObjetoCatalogo(
            id = "consola_arcade",
            nombre = "Arcade Retro Pixel",
            categoria = CategoriaObjeto.DIVERSION,
            anchoTiles = 1,
            altoTiles = 1,
            nivelMinimo = 2,
            acciones = listOf(AccionObjeto.JUGAR),
            descripcion = "Mini máquina arcade con juegos clásicos de 8 bits."
        ),
        ObjetoCatalogo(
            id = "estanteria_hololibros",
            nombre = "Estantería de Hololibros",
            categoria = CategoriaObjeto.DIVERSION,
            anchoTiles = 2,
            altoTiles = 1,
            nivelMinimo = 2,
            acciones = listOf(AccionObjeto.LEER),
            descripcion = "Colección de novelas y fábulas interactivas holográficas."
        ),
        ObjetoCatalogo(
            id = "sofa_holografico",
            nombre = "Sofá Modular Acolchado",
            categoria = CategoriaObjeto.MUEBLE,
            anchoTiles = 2,
            altoTiles = 1,
            nivelMinimo = 1,
            acciones = listOf(AccionObjeto.DORMIR),
            descripcion = "Sofá espacioso perfecto para recibir visitas o acurrucarse."
        ),
        ObjetoCatalogo(
            id = "mesa_centro",
            nombre = "Mesa de Centro de Cristal",
            categoria = CategoriaObjeto.MUEBLE,
            anchoTiles = 2,
            altoTiles = 2,
            nivelMinimo = 1,
            acciones = listOf(AccionObjeto.ADMIRAR),
            descripcion = "Mesa baja moderna donde colocar bebidas y bocadillos."
        ),
        ObjetoCatalogo(
            id = "planta_bonsai_led",
            nombre = "Bonsái Bioluminiscente",
            categoria = CategoriaObjeto.DECORACION,
            anchoTiles = 1,
            altoTiles = 1,
            nivelMinimo = 1,
            acciones = listOf(AccionObjeto.ADMIRAR),
            descripcion = "Planta enana que emite un suave brillo verde relajante."
        ),
        ObjetoCatalogo(
            id = "lampara_lava_neon",
            nombre = "Lámpara de Plasma Cian",
            categoria = CategoriaObjeto.DECORACION,
            anchoTiles = 1,
            altoTiles = 1,
            nivelMinimo = 1,
            acciones = listOf(AccionObjeto.ADMIRAR),
            descripcion = "Lámpara con esferas de energía flotantes que iluminan la habitación."
        ),
        ObjetoCatalogo(
            id = "alfombra_pixel_red",
            nombre = "Alfombra Geométrica Aréte",
            categoria = CategoriaObjeto.DECORACION,
            anchoTiles = 2,
            altoTiles = 2,
            nivelMinimo = 1,
            acciones = listOf(AccionObjeto.ADMIRAR),
            descripcion = "Alfombra tejida a mano con el emblema de la red peer-to-peer."
        ),
        // ── Objetos Estacionales y de Eventos (Fase 6) ──────────────────────────
        ObjetoCatalogo(
            id = "trofeo_torneo_gym",
            nombre = "Trofeo de Campeón Fit",
            categoria = CategoriaObjeto.MUEBLE,
            anchoTiles = 1,
            altoTiles = 1,
            nivelMinimo = 2,
            acciones = listOf(AccionObjeto.ADMIRAR),
            descripcion = "Copa dorada conmemorativa del Torneo Michi-Fit del Gimnasio."
        ),
        ObjetoCatalogo(
            id = "farol_festival_parque",
            nombre = "Farol de Flores de Primavera",
            categoria = CategoriaObjeto.DECORACION,
            anchoTiles = 1,
            altoTiles = 1,
            nivelMinimo = 1,
            acciones = listOf(AccionObjeto.ADMIRAR),
            descripcion = "Farol artesanal adornado con flores y luces cálidas del Festival."
        ),
        ObjetoCatalogo(
            id = "taza_dorada_cafe",
            nombre = "Taza Dorada de Tertulia",
            categoria = CategoriaObjeto.ALIMENTACION,
            anchoTiles = 1,
            altoTiles = 1,
            nivelMinimo = 2,
            acciones = listOf(AccionObjeto.COMER, AccionObjeto.ADMIRAR),
            descripcion = "Taza de porcelana con ribete dorado otorgada en la Tertulia Literaria."
        )
    )

    private val MAPA_POR_ID: Map<String, ObjetoCatalogo> = CATALOGO.associateBy { it.id }

    fun obtenerPorId(id: String): ObjetoCatalogo? = MAPA_POR_ID[id]

    fun todos(): List<ObjetoCatalogo> = CATALOGO

    fun porCategoria(categoria: CategoriaObjeto): List<ObjetoCatalogo> =
        CATALOGO.filter { it.categoria == categoria }
}
