package com.renea.arete_social.domain.model

import kotlinx.serialization.Serializable

/**
 * Subcategoría específica dentro de un interés.
 * Diseñado de forma abierta para que el usuario pueda especificar qué tipos le gustan
 * (ej: tipos de fiestas, tipos de deportes, géneros de libros, etc.).
 */
@Serializable
data class SubcategoriaInteres(
    val id: String,
    val nombre: String,
    val emoji: String = ""
)

/**
 * Categoría principal de interés o gusto en el perfil del usuario.
 */
@Serializable
data class CategoriaInteres(
    val id: String,
    val titulo: String,
    val emoji: String,
    val descripcion: String,
    val subcategorias: List<SubcategoriaInteres> = emptyList()
)

/**
 * Encuesta y selección de gustos del usuario.
 */
@Serializable
data class PerfilIntereses(
    val categoriasSeleccionadas: Set<String> = setOf("fiestas", "tecnologia"),
    val subcategoriasSeleccionadas: Set<String> = emptySet(),
    val gustosPersonalizados: List<String> = emptyList()
) {
    /**
     * Evalúa si un evento o texto coincide con las preferencias del usuario.
     */
    fun coincideCon(texto: String): Boolean {
        val lower = texto.lowercase()

        // 1. Revisar categorías activas y sus términos asociados
        for (catId in categoriasSeleccionadas) {
            val cat = CatalogoIntereses.buscarPorId(catId) ?: continue
            if (lower.contains(cat.id.lowercase()) || lower.contains(cat.titulo.lowercase())) {
                return true
            }
            for (sub in cat.subcategorias) {
                if (lower.contains(sub.id.lowercase()) || lower.contains(sub.nombre.lowercase())) {
                    return true
                }
            }
        }

        // 2. Revisar subcategorías seleccionadas específicamente
        for (subId in subcategoriasSeleccionadas) {
            val sub = CatalogoIntereses.buscarSubcategoriaPorId(subId) ?: continue
            if (lower.contains(sub.nombre.lowercase())) {
                return true
            }
        }

        // 3. Revisar gustos personalizados abiertos
        for (custom in gustosPersonalizados) {
            if (lower.contains(custom.lowercase())) {
                return true
            }
        }

        return false
    }

    /**
     * Retorna los nombres legibles de las categorías activas.
     */
    fun obtenerTitulosActivos(): List<String> {
        return categoriasSeleccionadas.mapNotNull { id ->
            CatalogoIntereses.buscarPorId(id)?.titulo
        }
    }
}

/**
 * Catálogo modular y extensible de gustos e intereses para la comunidad AntiRed.
 * Código completamente abierto a agregar nuevas categorías o tipos.
 */
object CatalogoIntereses {
    val categorias: List<CategoriaInteres> = listOf(
        CategoriaInteres(
            id = "fiestas",
            titulo = "Fiestas y Vida Nocturna",
            emoji = "🎉",
            descripcion = "Rumbas, festivales, música en vivo y encuentros nocturnos",
            subcategorias = listOf(
                SubcategoriaInteres("fiesta_electronica", "Música Electrónica / Rave", "🎧"),
                SubcategoriaInteres("fiesta_salsa", "Salsa y Ritmos Latinos", "💃"),
                SubcategoriaInteres("fiesta_universitaria", "Integraciones Universitarias", "🍻"),
                SubcategoriaInteres("fiesta_acustica", "Noches Acústicas y Karaoke", "🎸"),
                SubcategoriaInteres("fiesta_rock", "Rock y Conciertos", "🤘")
            )
        ),
        CategoriaInteres(
            id = "libros",
            titulo = "Libros y Literatura",
            emoji = "📚",
            descripcion = "Lectura, debates, clubes de lectura, poesía y ferias de libros",
            subcategorias = listOf(
                SubcategoriaInteres("libros_ficcion", "Ciencia Ficción y Fantasía", "🚀"),
                SubcategoriaInteres("libros_club", "Clubes de Lectura y Debates", "📖"),
                SubcategoriaInteres("libros_poesia", "Poesía y Microrrelatos", "✍️"),
                SubcategoriaInteres("libros_filosofia", "Filosofía y Ensayo", "💡"),
                SubcategoriaInteres("libros_manga", "Manga, Cómics y Novela Gráfica", "🎨")
            )
        ),
        CategoriaInteres(
            id = "deporte",
            titulo = "Deporte y Bienestar",
            emoji = "⚽",
            descripcion = "Actividades físicas, partidos, entrenamientos y vida al aire libre",
            subcategorias = listOf(
                SubcategoriaInteres("deporte_futbol", "Fútbol y Microfútbol", "⚽"),
                SubcategoriaInteres("deporte_running", "Running y Atletismo", "🏃"),
                SubcategoriaInteres("deporte_ciclismo", "Ciclismo y Rutas", "🚴"),
                SubcategoriaInteres("deporte_gym", "Calistenia y Gimnasio", "💪"),
                SubcategoriaInteres("deporte_yoga", "Yoga, Meditación y Senderismo", "🧘")
            )
        ),
        CategoriaInteres(
            id = "tecnologia",
            titulo = "Tecnología y Hackers",
            emoji = "💻",
            descripcion = "Programación, redes mesh, hardware libre, IA y ciberseguridad",
            subcategorias = listOf(
                SubcategoriaInteres("tec_hackathon", "Hackatones y Game Jams", "⚡"),
                SubcategoriaInteres("tec_opensource", "Software Libre y Linux", "🐧"),
                SubcategoriaInteres("tec_ia", "Inteligencia Artificial y Robótica", "🧠"),
                SubcategoriaInteres("tec_mesh", "Redes Mesh P2P y Criptografía", "📡")
            )
        ),
        CategoriaInteres(
            id = "arte",
            titulo = "Arte, Cine y Cultura",
            emoji = "🎨",
            descripcion = "Exposiciones, cineclubes, teatro, fotografía y talleres",
            subcategorias = listOf(
                SubcategoriaInteres("arte_cine", "Cineclub y Festivales de Cine", "🎬"),
                SubcategoriaInteres("arte_fotografia", "Fotografía Urbana y Retrato", "📷"),
                SubcategoriaInteres("arte_pintura", "Pintura, Ilustración y Murales", "🖌️"),
                SubcategoriaInteres("arte_teatro", "Teatro y Stand-up Comedy", "🎭")
            )
        ),
        CategoriaInteres(
            id = "gastronomia",
            titulo = "Gastronomía y Café",
            emoji = "☕",
            descripcion = "Cafés especiales, catas, gastronomía típica y cocina alternativa",
            subcategorias = listOf(
                SubcategoriaInteres("gastro_cafe", "Café de Especialidad y Tertulias", "☕"),
                SubcategoriaInteres("gastro_vegano", "Comida Vegana y Saludable", "🥗"),
                SubcategoriaInteres("gastro_callejera", "Comida Callejera y Tradición Caleña", "🌮"),
                SubcategoriaInteres("gastro_reposteria", "Repostería y Panadería Artesanal", "🥐")
            )
        )
    )

    fun buscarPorId(id: String): CategoriaInteres? = categorias.find { it.id == id }

    fun buscarSubcategoriaPorId(id: String): SubcategoriaInteres? {
        for (cat in categorias) {
            val sub = cat.subcategorias.find { it.id == id }
            if (sub != null) return sub
        }
        return null
    }
}
