package com.renea.arete_social.data.crypto

import java.security.MessageDigest
import com.renea.arete_social.domain.util.toHex

/**
 * Utilidad para calcular y formatear huellas criptográficas verificables
 * a partir de claves públicas (ECDSA / E2EE) para comparación presencial.
 */
object HuellaIdentidadHelper {

    /**
     * Calcula una huella digital formateada en bloques de 4 caracteres de 128 bits (ej: "A1B2-C3D4-E5F6-7890-...")
     * a partir de los bytes reales de la clave pública.
     */
    fun calcularHuellaCorta(clavePublicaBase64: String): String {
        if (clavePublicaBase64.isBlank()) return "----"
        val rawBytes = runCatching { java.util.Base64.getDecoder().decode(clavePublicaBase64) }
            .getOrDefault(clavePublicaBase64.toByteArray(Charsets.UTF_8))
        val digest = MessageDigest.getInstance("SHA-256").digest(rawBytes)
        // 128 bits (16 bytes = 32 caracteres hexadecimales en 8 bloques de 4)
        val hex = digest.copyOf(16).toHex().uppercase()
        return hex.chunked(4).joinToString("-")
    }
}
