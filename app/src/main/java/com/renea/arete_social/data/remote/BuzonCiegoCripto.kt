package com.renea.arete_social.data.remote

import java.security.MessageDigest
import java.util.Locale

/**
 * Utilidades criptográficas para la gestión de Buzones Ciegos (*Untrusted Blind Mailboxes*).
 *
 * ## Principios de Privacidad
 * 1. **Cero Conocimiento:** El servidor relay jamás conoce los identificadores reales de emisor ni receptor.
 *    Solo observa una etiqueta opaca de 64 caracteres hexadecimales derivada deterministamente.
 * 2. **Rotación Efímera:** Las etiquetas cambian según ventanas temporales rotativas de 24 horas,
 *    impidiendo la trazabilidad y correlación del tráfico a largo plazo.
 * 3. **Padding Anti-Análisis de Tráfico:** Aplica padding PKCS7 a bloques fijos para que el tamaño del
 *    paquete no filtre la longitud de los mensajes transmitidos.
 */
object BuzonCiegoCripto {

    /** Ventana temporal de rotación: 24 horas (en milisegundos). */
    const val VENTANA_ROTACION_MS = 24 * 3600 * 1000L

    /** Tamaño de bloque para padding constante (256 bytes). */
    const val TAMANO_BLOQUE_PADDING = 256

    private const val PREFIJO_USUARIO = "arete:blind_mailbox:user:v1:"
    private const val PREFIJO_COMUNIDAD = "arete:blind_mailbox:community:v1:"

    /**
     * Deriva la etiqueta de buzón hexadecimal para un usuario en una marca de tiempo dada.
     */
    fun derivarTagBuzonUsuario(usuarioId: String, timestampMs: Long = System.currentTimeMillis()): String {
        val ventana = timestampMs / VENTANA_ROTACION_MS
        val semilla = "$PREFIJO_USUARIO$usuarioId:$ventana"
        return sha256Hex(semilla.toByteArray(Charsets.UTF_8))
    }

    /**
     * Devuelve las etiquetas activas para un usuario: la ventana actual y la ventana inmediatamente anterior,
     * para asegurar que ningún mensaje enviado cerca del límite de medianoche se pierda.
     */
    fun tagsActivosParaUsuario(usuarioId: String, timestampMs: Long = System.currentTimeMillis()): List<String> {
        val tagActual = derivarTagBuzonUsuario(usuarioId, timestampMs)
        val tagAnterior = derivarTagBuzonUsuario(usuarioId, timestampMs - VENTANA_ROTACION_MS)
        return listOf(tagActual, tagAnterior).distinct()
    }

    /**
     * Deriva la etiqueta de buzón hexadecimal para una comunidad o canal temático.
     */
    fun derivarTagComunidad(comunidadIdHex: String, timestampMs: Long = System.currentTimeMillis()): String {
        val ventana = timestampMs / VENTANA_ROTACION_MS
        val semilla = "$PREFIJO_COMUNIDAD$comunidadIdHex:$ventana"
        return sha256Hex(semilla.toByteArray(Charsets.UTF_8))
    }

    /**
     * Devuelve las etiquetas activas para una comunidad (ventana actual y previa).
     */
    fun tagsActivosParaComunidad(comunidadIdHex: String, timestampMs: Long = System.currentTimeMillis()): List<String> {
        val tagActual = derivarTagComunidad(comunidadIdHex, timestampMs)
        val tagAnterior = derivarTagComunidad(comunidadIdHex, timestampMs - VENTANA_ROTACION_MS)
        return listOf(tagActual, tagAnterior).distinct()
    }

    /**
     * Aplica relleno (padding PKCS7) a los datos hasta el múltiplo más cercano de [bloqueBytes].
     */
    fun aplicarPadding(datos: ByteArray, bloqueBytes: Int = TAMANO_BLOQUE_PADDING): ByteArray {
        val resto = datos.size % bloqueBytes
        val padLen = if (resto == 0) bloqueBytes else bloqueBytes - resto
        val padByte = padLen.toByte()

        val resultado = ByteArray(datos.size + padLen)
        System.arraycopy(datos, 0, resultado, 0, datos.size)
        for (i in datos.size until resultado.size) {
            resultado[i] = padByte
        }
        return resultado
    }

    /**
     * Remueve el relleno PKCS7 de los datos recibidos.
     * Si el relleno no es válido o está corrupto, devuelve los datos intactos o falla de forma segura.
     */
    fun removerPadding(datos: ByteArray): ByteArray {
        if (datos.isEmpty()) return datos
        val padLen = datos.last().toInt() and 0xFF
        if (padLen <= 0 || padLen > TAMANO_BLOQUE_PADDING || padLen > datos.size) {
            return datos
        }
        // Validar integridad de todos los bytes de relleno
        for (i in (datos.size - padLen) until datos.size) {
            if ((datos[i].toInt() and 0xFF) != padLen) {
                return datos // Padding inválido: devolver sin recortar
            }
        }
        val limpio = ByteArray(datos.size - padLen)
        System.arraycopy(datos, 0, limpio, 0, limpio.size)
        return limpio
    }

    /**
     * Calcula el digest SHA-256 y lo retorna en formato hexadecimal de 64 caracteres en minúsculas.
     */
    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(String.format(Locale.US, "%02x", b))
        }
        return sb.toString()
    }
}
