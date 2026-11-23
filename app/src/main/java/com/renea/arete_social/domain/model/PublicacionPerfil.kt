package com.renea.arete_social.domain.model

enum class TipoPublicacionPerfil {
    TEXTO_RETRO,
    FOTO_MATRIX
}

data class PublicacionPerfil(
    val id: String = java.util.UUID.randomUUID().toString(),
    val usuarioId: String,
    val autorNombre: String = "",
    val tipo: TipoPublicacionPerfil,
    val contenido: String, // Texto escrito o Base64/URI de la imagen
    val timestamp: Long = System.currentTimeMillis(),
    val likes: Int = 0,
    val usuariosLike: Set<String> = emptySet()
) {
    /**
     * Comprueba si la publicación sigue vigente dentro del ciclo semanal (7 días = 604_800_000 ms).
     */
    fun estaVigenteSemanal(tiempoActual: Long = System.currentTimeMillis()): Boolean {
        val duracionSemana = 7L * 24 * 60 * 60 * 1000L
        return (tiempoActual - timestamp) < duracionSemana
    }

    /**
     * Devuelve los días restantes antes de que se limpie el perfil.
     */
    fun diasRestantes(tiempoActual: Long = System.currentTimeMillis()): Int {
        val duracionSemana = 7L * 24 * 60 * 60 * 1000L
        val restante = (timestamp + duracionSemana) - tiempoActual
        return (restante / (24 * 60 * 60 * 1000L)).toInt().coerceAtLeast(0)
    }

    /**
     * Formatea el tiempo transcurrido de forma corta y retro.
     */
    fun tiempoTranscurrido(): String {
        val diff = (System.currentTimeMillis() - timestamp).coerceAtLeast(0)
        val minutos = diff / (60 * 1000)
        val horas = diff / (60 * 60 * 1000)
        val dias = diff / (24 * 60 * 60 * 1000)
        return when {
            minutos < 1 -> "Ahora"
            minutos < 60 -> "${minutos}m"
            horas < 24 -> "${horas}h"
            else -> "${dias}d"
        }
    }
}


/** Copia de la lista con mi like puesto o quitado en la publicación [publicacionId]. */
fun List<PublicacionPerfil>.conLikeDe(publicacionId: String, usuarioId: String, darLike: Boolean): List<PublicacionPerfil> =
    map { pub ->
        if (pub.id != publicacionId) {
            pub
        } else {
            val quienes = if (darLike) pub.usuariosLike + usuarioId else pub.usuariosLike - usuarioId
            pub.copy(likes = quienes.size, usuariosLike = quienes)
        }
    }
