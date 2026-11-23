package com.renea.arete_social.domain.util

import com.renea.arete_social.domain.model.ArchivoPdf
import com.renea.arete_social.domain.model.LimitesCarta
import com.renea.arete_social.domain.model.LimitesLibreria
import com.renea.arete_social.domain.model.NuevaObra
import com.renea.arete_social.domain.model.TipoObra
import java.net.URI

/** Resultado de revisar el enlace discreto que acompaña a una obra. */
sealed interface EnlaceValidado {
    data object Vacio : EnlaceValidado
    data class Valido(val url: String) : EnlaceValidado
    data object Invalido : EnlaceValidado
}

private val ESQUEMA_EXPLICITO = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

/**
 * Cualquier texto que tiene un esquema explícito seguido de ":" pero sin "://" también
 * se considera esquema explícito (p. ej. "mailto:", "javascript:", "intent:").
 * Esto evita que "mailto:a@b.co" se convierta en "https://mailto:a@b.co" y pase la validación.
 */
private val TIENE_ESQUEMA_NO_DOBLE_BARRA = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:[^/]")

/**
 * Acepta solo http/https con un dominio real. Si falta el esquema se asume https.
 * Esquemas como javascript:, intent:, mailto: o file: se rechazan para que un enlace guardado
 * nunca pueda ejecutar nada al abrirse.
 */
fun validarEnlace(entrada: String): EnlaceValidado {
    val limpio = entrada.trim()
    if (limpio.isEmpty()) return EnlaceValidado.Vacio
    if (limpio.length > LimitesLibreria.MAX_ENLACE) return EnlaceValidado.Invalido

    // Si tiene cualquier esquema explícito que NO sea http:// o https://, rechazarlo de inmediato.
    if (TIENE_ESQUEMA_NO_DOBLE_BARRA.containsMatchIn(limpio)) return EnlaceValidado.Invalido

    val conEsquema = if (ESQUEMA_EXPLICITO.containsMatchIn(limpio)) limpio else "https://$limpio"
    val uri = runCatching { URI(conEsquema) }.getOrNull() ?: return EnlaceValidado.Invalido
    val esquema = uri.scheme?.lowercase()
    val host = uri.host
    val esWeb = esquema == "http" || esquema == "https"
    return if (esWeb && !host.isNullOrBlank() && host.contains('.')) {
        EnlaceValidado.Valido(conEsquema)
    } else {
        EnlaceValidado.Invalido
    }
}

/** Resultado de validar el formulario de una obra. */
sealed interface ResultadoObra {
    data class Correcta(val obra: NuevaObra) : ResultadoObra
    data class Error(val mensaje: String) : ResultadoObra
}

fun validarNuevaObra(
    tipo: TipoObra,
    titulo: String,
    contenido: String,
    enlace: String,
    pdf: ArchivoPdf?
): ResultadoObra {
    val tituloLimpio = titulo.trim()
    val contenidoLimpio = contenido.trim()
    if (tituloLimpio.isEmpty()) return ResultadoObra.Error("Ponle un título a tu ${tipo.etiqueta.lowercase()}.")
    if (tituloLimpio.length > LimitesLibreria.MAX_TITULO) {
        return ResultadoObra.Error("El título admite hasta ${LimitesLibreria.MAX_TITULO} caracteres.")
    }
    if (contenidoLimpio.isEmpty() && pdf == null) {
        return ResultadoObra.Error("Escribe el texto o adjunta un PDF.")
    }
    if (contenidoLimpio.length > LimitesLibreria.MAX_CONTENIDO) {
        return ResultadoObra.Error("El texto admite hasta ${LimitesLibreria.MAX_CONTENIDO} caracteres.")
    }
    if (pdf != null && pdf.bytes.size > LimitesLibreria.MAX_PDF_BYTES) {
        return ResultadoObra.Error("El PDF supera los ${LimitesLibreria.MAX_PDF_BYTES / (1024 * 1024)} MB.")
    }
    val enlaceFinal = when (val e = validarEnlace(enlace)) {
        EnlaceValidado.Vacio -> null
        is EnlaceValidado.Valido -> e.url
        EnlaceValidado.Invalido -> return ResultadoObra.Error("El enlace no parece válido (usa http o https).")
    }
    return ResultadoObra.Correcta(NuevaObra(tipo, tituloLimpio, contenidoLimpio, enlaceFinal, pdf))
}

/** Devuelve el texto limpio de la carta o el motivo por el que no se puede enviar. */
fun validarTextoCarta(texto: String): Pair<String?, String?> {
    val limpio = texto.trim()
    return when {
        limpio.isEmpty() -> null to "Escribe algo antes de enviar la carta."
        limpio.length > LimitesCarta.MAX_CARACTERES ->
            null to "La carta admite hasta ${LimitesCarta.MAX_CARACTERES} caracteres."
        else -> limpio to null
    }
}
