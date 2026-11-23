package com.renea.arete_social.data.mesh.discovery

import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Descubrimiento BLE Privado de Comunidades según la sección 11 de la auditoría v3.
 *
 * Los anuncios BLE nunca transmiten el nombre ni el ID de la comunidad en texto claro.
 * En su lugar, transmiten un `EphemeralCommunityTag` de 16 bytes que rota cada 15 minutos (900,000 ms),
 * derivado de la clave de época actual Ke mediante HMAC-SHA256.
 *
 * Solo los dispositivos que poseen Ke de esa comunidad pueden verificar y correlacionar
 * el tag anunciado con la comunidad correspondiente.
 */
object BleCommunityDiscovery {

    private const val DOMAIN_PREFIX = "arete/ble/discovery/"
    const val ROTATION_INTERVAL_MS = 900_000L // 15 minutos
    const val TAG_SIZE_BYTES = 16

    /**
     * Genera el tag efímero actual para emitir en el payload de anuncio BLE.
     *
     * @param ke Clave de época actual (32 bytes).
     * @param timestampMs Tiempo actual en milisegundos.
     * @return Tag efímero de 16 bytes.
     */
    fun generateEphemeralTag(ke: ByteArray, timestampMs: Long = System.currentTimeMillis()): ByteArray {
        val windowIndex = timestampMs / ROTATION_INTERVAL_MS
        return computeTagForWindow(ke, windowIndex)
    }

    /**
     * Verifica si un tag BLE observado corresponde a una comunidad dada su clave Ke,
     * admitiendo una tolerancia de +/- 1 ventana (15 min antes o después) para compensar desvíos de reloj.
     *
     * @param observedTag Tag de 16 bytes recibido por escaneo BLE.
     * @param ke Clave de época de la comunidad a contrastar.
     * @param currentTimestampMs Tiempo actual del receptor.
     * @return true si el tag corresponde a esta comunidad en la ventana actual o adyacentes.
     */
    fun matchesCommunity(
        observedTag: ByteArray,
        ke: ByteArray,
        currentTimestampMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (observedTag.size != TAG_SIZE_BYTES) return false

        val currentWindow = currentTimestampMs / ROTATION_INTERVAL_MS

        // Tolerancia de ventana: actual, anterior y siguiente
        for (offset in -1..1) {
            val candidateTag = computeTagForWindow(ke, currentWindow + offset)
            if (observedTag.contentEquals(candidateTag)) {
                return true
            }
        }
        return false
    }

    private fun computeTagForWindow(ke: ByteArray, windowIndex: Long): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(ke, "HmacSHA256"))

        val domainBytes = DOMAIN_PREFIX.toByteArray(Charsets.UTF_8)
        val windowBytes = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(windowIndex).array()

        mac.update(domainBytes)
        val fullHash = mac.doFinal(windowBytes)

        // Truncar a los primeros 16 bytes
        return fullHash.copyOfRange(0, TAG_SIZE_BYTES)
    }
}
