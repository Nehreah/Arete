package com.renea.arete_social.domain.util

/**
 * Extensiones utilitarias de alto rendimiento para codificación y decodificación
 * de arreglos de bytes en formato hexadecimal (evita duplicación de lógica en toda la app).
 */
fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

fun String.hexToByteArray(): ByteArray {
    val clean = if (length % 2 != 0) "0$this" else this
    return ByteArray(clean.length / 2) { i ->
        clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
}
