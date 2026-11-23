package com.renea.arete_social.feature.miausnake.social

import java.security.MessageDigest

/** Identificadores deterministas: el mismo contenido da siempre el mismo id (base de la idempotencia). */
object Ids {
    private const val HEX = "0123456789abcdef"

    /** 32 caracteres hexadecimales (128 bits de SHA-256) de las partes unidas con un separador. */
    fun of(vararg parts: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        for ((i, p) in parts.withIndex()) {
            if (i > 0) md.update(0.toByte())
            md.update(p.toByteArray(Charsets.UTF_8))
        }
        val d = md.digest()
        val sb = StringBuilder(32)
        for (k in 0 until 16) {
            val v = d[k].toInt() and 0xFF
            sb.append(HEX[v shr 4]).append(HEX[v and 15])
        }
        return sb.toString()
    }

    fun isId(s: String): Boolean = s.length == 32 && s.all { it in '0'..'9' || it in 'a'..'f' }
}
