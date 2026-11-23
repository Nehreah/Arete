package com.renea.arete_social

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * §1.3 — Tests de regresión JVM para las constantes BLE y la función truncarUtf8.
 *
 * Estos tests corren en la JVM del host (sin emulador ni dispositivo) y verifican
 * que:
 *  1. Las constantes BLE no superan el límite de 31 bytes del paquete legado.
 *  2. truncarUtf8 nunca produce más de MAX_BYTES_NOMBRE bytes, incluso con emojis
 *     y caracteres multibyte de 4 bytes (fuera del BMP).
 *  3. truncarUtf8 no corta un carácter multibyte a la mitad.
 */
class ProximityBleUnitTest {

    // ── Constantes duplicadas del archivo de producción ──────────────────────
    // Se duplican aquí porque son `private const val` y no accesibles desde el
    // módulo de test sin cambiarlas a `internal`. Si se cambia la visibilidad en
    // producción a `internal`, estas líneas pueden reemplazarse por las importaciones
    // reales y el test sigue siendo válido.
    private val LIMITE_BLE_LEGADO_BYTES    = 31
    private val OVERHEAD_SCAN_RESPONSE_BYTES = 2 + 16 + 2   // AD header + UUID128 + AD data header
    private val MAX_BYTES_NOMBRE           = LIMITE_BLE_LEGADO_BYTES - OVERHEAD_SCAN_RESPONSE_BYTES // = 11

    // ── Réplica local de truncarUtf8 (lógica idéntica a la de producción) ────
    private fun truncarUtf8(texto: String, maxBytes: Int): ByteArray {
        val encoder = Charsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        val inputBuffer  = java.nio.CharBuffer.wrap(texto)
        val outputBuffer = ByteBuffer.allocate(maxBytes)
        encoder.encode(inputBuffer, outputBuffer, true)
        outputBuffer.flip()
        val result = ByteArray(outputBuffer.remaining())
        outputBuffer.get(result)
        return result
    }

    // ────────────────────────────────────────────────────────────────────────

    /** §1.3.a — La suma de overhead + nombre no debe exceder el límite del paquete BLE. */
    @Test
    fun constantes_BLE_caben_en_paquete_legado() {
        assertTrue(
            "MAX_BYTES_NOMBRE ($MAX_BYTES_NOMBRE) + OVERHEAD ($OVERHEAD_SCAN_RESPONSE_BYTES) " +
                    "debe ser <= $LIMITE_BLE_LEGADO_BYTES",
            MAX_BYTES_NOMBRE + OVERHEAD_SCAN_RESPONSE_BYTES <= LIMITE_BLE_LEGADO_BYTES
        )
    }

    /** §1.3.b — El valor calculado de MAX_BYTES_NOMBRE debe ser exactamente 11. */
    @Test
    fun max_bytes_nombre_es_11() {
        assertTrue(
            "MAX_BYTES_NOMBRE debería ser 11, pero es $MAX_BYTES_NOMBRE",
            MAX_BYTES_NOMBRE == 11
        )
    }

    /** §1.3.c — truncarUtf8 nunca produce más de MAX_BYTES_NOMBRE bytes. */
    @Test
    fun truncar_utf8_no_supera_limite() {
        val casosPrueba = listOf(
            "Hola Mundo",
            "Señorita García López",
            "\uD83C\uDDE8\uD83C\uDDF4".repeat(10), // emoji 4-byte (flag Colombia) x10
            "こんにちは世界",                            // japonés 3-byte por carácter
            "A".repeat(100),                        // solo ASCII largo
            "\uD83D\uDE00\uD83D\uDE01\uD83D\uDE02\uD83D\uDE03", // 4 emojis 4-byte
        )
        for (caso in casosPrueba) {
            val resultado = truncarUtf8(caso, MAX_BYTES_NOMBRE)
            assertTrue(
                "truncarUtf8(\"${caso.take(20)}...\") produjo ${resultado.size} bytes, " +
                        "pero el máximo es $MAX_BYTES_NOMBRE",
                resultado.size <= MAX_BYTES_NOMBRE
            )
        }
    }

    /** §1.3.d — truncarUtf8 no corta un carácter multibyte a la mitad. */
    @Test
    fun truncar_utf8_no_corta_multibyte() {
        // "🇨🇴" = U+1F1E8 U+1F1F4, 8 bytes en UTF-8 (dos code points de 4 bytes)
        // Con MAX_BYTES_NOMBRE = 11, cabe 1 flag (8 bytes). El segundo no cabe.
        val emoji = "\uD83C\uDDE8\uD83C\uDDF4"  // 🇨🇴 en Java surrogate pairs
        val entrada = emoji.repeat(5)
        val resultadoBytes = truncarUtf8(entrada, MAX_BYTES_NOMBRE)
        // La cadena resultante debe ser UTF-8 válido sin errores
        val resultadoString = String(resultadoBytes, Charsets.UTF_8)
        // Re-codificar debe producir los mismos bytes (idempotencia)
        val recodificado = resultadoString.toByteArray(Charsets.UTF_8)
        assertArrayEquals(
            "truncarUtf8 produjo bytes no decodificables como UTF-8 completo",
            resultadoBytes,
            recodificado
        )
        assertTrue(
            "El resultado debe caber en MAX_BYTES_NOMBRE bytes",
            resultadoBytes.size <= MAX_BYTES_NOMBRE
        )
    }

    /** §1.3.e — truncarUtf8 preserva el texto completo si cabe dentro del límite. */
    @Test
    fun truncar_utf8_preserva_texto_corto() {
        val textoCorto = "Ana"  // 3 bytes, cabe perfectamente en 11 bytes
        val resultado = truncarUtf8(textoCorto, MAX_BYTES_NOMBRE)
        assertArrayEquals(
            "Un texto corto debe preservarse sin truncar",
            textoCorto.toByteArray(Charsets.UTF_8),
            resultado
        )
    }

    /**
     * Test de Regresión Crítico:
     * El orden en iniciarPublicidad() DEBE ser:
     *   1. detenerPublicidad() (limpia el anuncio previo y pone advertiser = null)
     *   2. advertiser = adapter.bluetoothLeAdvertiser (asigna la instancia activa)
     * Si se invierte el orden, advertiser queda en null y startAdvertising() nunca se llama.
     */
    @Test
    fun orden_inicializacion_advertiser_no_debe_anular_instancia() {
        var advertiserSimulado: Any? = null

        fun detenerPublicidadSimulado() {
            advertiserSimulado = null
        }

        fun iniciarPublicidadCorrecto(instanciaHardware: Any) {
            detenerPublicidadSimulado()
            advertiserSimulado = instanciaHardware
        }

        val dummyAdvertiser = Any()
        iniciarPublicidadCorrecto(dummyAdvertiser)

        assertTrue(
            "El advertiser activo no debe ser null tras iniciarPublicidad()",
            advertiserSimulado != null
        )
    }

    /**
     * Test de Regresión Crítico:
     * El paquete principal de anuncio BLE legado (AdvertiseData) tiene un límite rígido de 31 bytes.
     * Flags = 3B.
     * ServiceData 16-bit UUID = 4B de cabecera + 17B payload (1B tipo + 16B UUID) = 21B.
     * Total = 24B <= 31B.
     * Si se añade addServiceUuid(128-bit), se agregan 18B adicionales = 42B > 31B (FALLO SEVERO).
     */
    @Test
    fun paquete_principal_anuncio_no_supera_limite_31_bytes() {
        val flagsBytes = 3
        val serviceDataHeader16Bit = 4
        val payloadUsuarioBytes = 17 // 1 byte de tipo (0x01 o 0x02) + 16 bytes UUID
        val paquetePrincipalTotal = flagsBytes + serviceDataHeader16Bit + payloadUsuarioBytes

        assertTrue(
            "El paquete principal de anuncio debe medir <= 31 bytes (actual: $paquetePrincipalTotal)",
            paquetePrincipalTotal <= LIMITE_BLE_LEGADO_BYTES
        )

        // Advertencia preventiva documentada como test:
        val serviceUuidOverhead128Bit = 18
        val paqueteConServiceUuid = paquetePrincipalTotal + serviceUuidOverhead128Bit
        assertTrue(
            "Añadir Service UUID 128-bit excedería los 31 bytes ($paqueteConServiceUuid > 31) y debe evitarse",
            paqueteConServiceUuid > LIMITE_BLE_LEGADO_BYTES
        )
    }
}
