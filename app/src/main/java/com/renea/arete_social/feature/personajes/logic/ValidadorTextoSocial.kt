package com.renea.arete_social.feature.personajes.logic

/**
 * Resultado de validar un texto destinado a recuerdos, notas o descripciones sociales.
 */
data class ResultadoValidacionTexto(
    val esValido: Boolean,
    val mensajeError: String? = null,
    val textoLimpio: String = ""
)

/**
 * Validador y sanitizador de textos para interacciones de personajes.
 *
 * Previene:
 * 1. Fuga de URLs o vectores de phishing/malware mediante enlaces no autorizados.
 * 2. Cargas útiles de texto gigantescas (máximo 120 caracteres para notas y recuerdos).
 * 3. Caracteres de control u ofuscación unicode dañinos.
 */
object ValidadorTextoSocial {

    const val MAX_LONGITUD = 120

    // Patrón estricto para detectar cualquier intento de URL, esquema de red o dominio
    private val REGEX_URL = Regex(
        "(?i)\\b(?:https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]" +
            "|\\bwww\\.[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]" +
            "|\\b[a-zA-Z0-9.-]+\\.(?:com|org|net|io|co|me|ly|link|cc|gg|to|app|dev|xyz|info|edu|gov|site|online|tk)\\b"
    )

    // Caracteres de control invisibles o no imprimibles
    private val REGEX_CONTROL = Regex("[\\p{Cntrl}&&[^\r\n\t]]")

    /**
     * Valida si un texto cumple con las directrices de seguridad social.
     */
    fun validar(texto: String?): ResultadoValidacionTexto {
        if (texto.isNullOrBlank()) {
            return ResultadoValidacionTexto(
                esValido = false,
                mensajeError = "El texto no puede estar vacío."
            )
        }

        val recortado = texto.trim()

        if (recortado.length > MAX_LONGITUD) {
            return ResultadoValidacionTexto(
                esValido = false,
                mensajeError = "El texto no puede exceder los $MAX_LONGITUD caracteres (actual: ${recortado.length})."
            )
        }

        if (REGEX_URL.containsMatchIn(recortado)) {
            return ResultadoValidacionTexto(
                esValido = false,
                mensajeError = "Por seguridad no se permiten enlaces ni URLs en mensajes sociales."
            )
        }

        val limpio = sanitizar(recortado)
        return ResultadoValidacionTexto(
            esValido = true,
            textoLimpio = limpio
        )
    }

    /**
     * Sanitiza el texto eliminando caracteres de control y normalizando espacios.
     */
    fun sanitizar(texto: String): String {
        return texto
            .replace(REGEX_CONTROL, "")
            .replace("\\s+".toRegex(), " ")
            .trim()
            .take(MAX_LONGITUD)
    }
}
