package com.renea.arete_social.data.remote

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

/**
 * Criptografía y serialización estándar para Nostr (NIP-01 y BIP-340).
 *
 * Implementación pura en Kotlin con aritmética de [BigInteger] sobre secp256k1,
 * compatible con cualquier versión de Android sin dependencias JNI externas.
 */
object NostrCrypto {

    // ─── Parámetros de la curva secp256k1 ──────────────────────────────────────
    private val P = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F", 16)
    private val N = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16)
    private val Gx = BigInteger("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798", 16)
    private val Gy = BigInteger("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8", 16)

    private val TWO = BigInteger.valueOf(2)
    private val THREE = BigInteger.valueOf(3)

    data class Punto(val x: BigInteger, val y: BigInteger) {
        companion object {
            val INFINITO = Punto(BigInteger.ZERO, BigInteger.ZERO)
        }
        val esInfinito: Boolean get() = this == INFINITO
    }

    private val G = Punto(Gx, Gy)

    // ─── Operaciones de Grupo sobre la Curva ───────────────────────────────────

    private fun puntoDoble(p: Punto): Punto {
        if (p.esInfinito || p.y == BigInteger.ZERO) return Punto.INFINITO
        // s = (3 * x^2) / (2 * y) mod P
        val num = THREE.multiply(p.x.modPow(TWO, P)).mod(P)
        val den = TWO.multiply(p.y).modInverse(P)
        val s = num.multiply(den).mod(P)

        // rx = s^2 - 2 * x mod P
        val rx = s.modPow(TWO, P).subtract(TWO.multiply(p.x)).mod(P)
        // ry = s * (x - rx) - y mod P
        val ry = s.multiply(p.x.subtract(rx)).subtract(p.y).mod(P)
        return Punto(rx, ry)
    }

    private fun puntoSuma(p1: Punto, p2: Punto): Punto {
        if (p1.esInfinito) return p2
        if (p2.esInfinito) return p1
        if (p1.x == p2.x) {
            return if (p1.y == p2.y) puntoDoble(p1) else Punto.INFINITO
        }

        // s = (y2 - y1) / (x2 - x1) mod P
        val num = p2.y.subtract(p1.y).mod(P)
        val den = p2.x.subtract(p1.x).modInverse(P)
        val s = num.multiply(den).mod(P)

        // rx = s^2 - x1 - x2 mod P
        val rx = s.modPow(TWO, P).subtract(p1.x).subtract(p2.x).mod(P)
        // ry = s * (x1 - rx) - y1 mod P
        val ry = s.multiply(p1.x.subtract(rx)).subtract(p1.y).mod(P)
        return Punto(rx, ry)
    }

    private fun multiplicarPunto(k: BigInteger, p: Punto): Punto {
        var r = Punto.INFINITO
        var actual = p
        var esc = k.mod(N)
        while (esc > BigInteger.ZERO) {
            if (esc.testBit(0)) {
                r = puntoSuma(r, actual)
            }
            actual = puntoDoble(actual)
            esc = esc.shiftRight(1)
        }
        return r
    }

    // ─── Generación de Claves ────────────────────────────────────────────────

    data class KeyPairSecp256k1(
        val privKeyHex: String,
        val pubKeyHex: String // Clave pública x-only de 32 bytes (64 hex chars)
    )

    /**
     * Genera un par de claves secp256k1 aleatorio compatible con BIP-340.
     */
    fun generarClaves(): KeyPairSecp256k1 {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        var d: BigInteger
        do {
            random.nextBytes(bytes)
            d = BigInteger(1, bytes)
        } while (d <= BigInteger.ZERO || d >= N)

        return derivarDesdeClavePrivada(d)
    }

    /**
     * Deriva un par de claves determinista a partir de una semilla arbitraria (ej. ID de usuario).
     */
    fun derivarClavesDesdeSemilla(semilla: String): KeyPairSecp256k1 {
        val hash = sha256Bytes(semilla.toByteArray(Charsets.UTF_8))
        var d = BigInteger(1, hash).mod(N)
        if (d == BigInteger.ZERO) d = BigInteger.ONE
        return derivarDesdeClavePrivada(d)
    }

    private fun derivarDesdeClavePrivada(privInicial: BigInteger): KeyPairSecp256k1 {
        val p = multiplicarPunto(privInicial, G)
        val privFinal = if (p.y.testBit(0)) N.subtract(privInicial) else privInicial
        val pubFinalX = p.x

        val privHex = bytesAHex(bigIntegerABytes32(privFinal))
        val pubHex = bytesAHex(bigIntegerABytes32(pubFinalX))
        return KeyPairSecp256k1(privHex, pubHex)
    }

    // ─── Firma BIP-340 Schnorr ────────────────────────────────────────────────

    /**
     * Firma un hash de mensaje (32 bytes) usando BIP-340 Schnorr.
     * Retorna la firma de 64 bytes en minúsculas hexadecimales (128 chars).
     */
    fun firmarBip340(msgHash32: ByteArray, privKeyHex: String): String {
        require(msgHash32.size == 32) { "El hash del mensaje debe ser exactamente de 32 bytes" }
        val d0 = BigInteger(privKeyHex, 16)
        require(d0 >= BigInteger.ONE && d0 < N) { "Clave privada fuera de rango" }

        val p = multiplicarPunto(d0, G)
        val d = if (p.y.testBit(0)) N.subtract(d0) else d0
        val pxBytes = bigIntegerABytes32(p.x)

        // Nonce determinista: k' = sha256(bytes(d) + m) mod N
        val dBytes = bigIntegerABytes32(d)
        val kSeed = ByteArray(64)
        System.arraycopy(dBytes, 0, kSeed, 0, 32)
        System.arraycopy(msgHash32, 0, kSeed, 32, 32)
        val k0 = BigInteger(1, sha256Bytes(kSeed)).mod(N)
        val kPrima = if (k0 == BigInteger.ZERO) BigInteger.ONE else k0

        val rPoint = multiplicarPunto(kPrima, G)
        val k = if (rPoint.y.testBit(0)) N.subtract(kPrima) else kPrima
        val rxBytes = bigIntegerABytes32(rPoint.x)

        // e = sha256(rx + px + m) mod N
        val eBytes = ByteArray(32 + 32 + 32)
        System.arraycopy(rxBytes, 0, eBytes, 0, 32)
        System.arraycopy(pxBytes, 0, eBytes, 32, 32)
        System.arraycopy(msgHash32, 0, eBytes, 64, 32)
        val e = BigInteger(1, sha256Bytes(eBytes)).mod(N)

        // s = (k + e * d) mod N
        val s = k.add(e.multiply(d)).mod(N)
        val sBytes = bigIntegerABytes32(s)

        val sig = ByteArray(64)
        System.arraycopy(rxBytes, 0, sig, 0, 32)
        System.arraycopy(sBytes, 0, sig, 32, 32)
        return bytesAHex(sig)
    }

    /**
     * Valida una firma BIP-340 Schnorr.
     */
    fun verificarBip340(msgHash32: ByteArray, pubKeyHex: String, sigHex: String): Boolean {
        if (msgHash32.size != 32 || pubKeyHex.length != 64 || sigHex.length != 128) return false
        return runCatching {
            val px = BigInteger(pubKeyHex, 16)
            if (px >= P) return false

            val rx = BigInteger(sigHex.substring(0, 64), 16)
            val s = BigInteger(sigHex.substring(64, 128), 16)
            if (rx >= P || s >= N) return false

            // Calcular y de P a partir de y^2 = x^3 + 7 mod P
            val yCuadrado = px.modPow(THREE, P).add(BigInteger.valueOf(7)).mod(P)
            val y = yCuadrado.modPow(P.add(BigInteger.ONE).divide(BigInteger.valueOf(4)), P)
            if (y.modPow(TWO, P) != yCuadrado) return false
            val py = if (y.testBit(0)) P.subtract(y) else y
            val pPoint = Punto(px, py)

            // e = sha256(rx + px + m) mod N
            val rxBytes = bigIntegerABytes32(rx)
            val pxBytes = bigIntegerABytes32(px)
            val eBytes = ByteArray(96)
            System.arraycopy(rxBytes, 0, eBytes, 0, 32)
            System.arraycopy(pxBytes, 0, eBytes, 32, 32)
            System.arraycopy(msgHash32, 0, eBytes, 64, 32)
            val e = BigInteger(1, sha256Bytes(eBytes)).mod(N)

            // R = s*G - e*P
            val sg = multiplicarPunto(s, G)
            val ep = multiplicarPunto(e, pPoint)
            val negEp = Punto(ep.x, P.subtract(ep.y).mod(P))
            val rEsperado = puntoSuma(sg, negEp)

            !rEsperado.esInfinito && !rEsperado.y.testBit(0) && rEsperado.x == rx
        }.getOrDefault(false)
    }

    // ─── Serialización Canónica NIP-01 ────────────────────────────────────────

    /**
     * Construye la serialización canónica NIP-01:
     * `[0, pubkey, created_at, kind, tags, content]`
     */
    fun serializarEventoCanónico(
        pubkey: String,
        createdAt: Long,
        kind: Int,
        tags: List<List<String>>,
        content: String
    ): String {
        val sb = StringBuilder()
        sb.append("[0,")
        sb.append('"').append(pubkey).append("\",")
        sb.append(createdAt).append(',')
        sb.append(kind).append(',')

        // tags
        sb.append('[')
        tags.forEachIndexed { i, tag ->
            if (i > 0) sb.append(',')
            sb.append('[')
            tag.forEachIndexed { j, elem ->
                if (j > 0) sb.append(',')
                sb.append('"').append(escaparJsonString(elem)).append('"')
            }
            sb.append(']')
        }
        sb.append("],")

        // content
        sb.append('"').append(escaparJsonString(content)).append('"')
        sb.append(']')
        return sb.toString()
    }

    /**
     * Calcula el Event ID de Nostr como SHA256 de la serialización canónica NIP-01.
     */
    fun calcularEventId(
        pubkey: String,
        createdAt: Long,
        kind: Int,
        tags: List<List<String>>,
        content: String
    ): String {
        val canonico = serializarEventoCanónico(pubkey, createdAt, kind, tags, content)
        return bytesAHex(sha256Bytes(canonico.toByteArray(Charsets.UTF_8)))
    }

    // ─── Utilidades Criptográficas y Formato ──────────────────────────────────

    fun sha256Bytes(bytes: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(bytes)
    }

    fun bytesAHex(bytes: ByteArray): String {
        val sb = java.lang.StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format(Locale.US, "%02x", b))
        }
        return sb.toString()
    }

    fun bigIntegerABytes32(bi: BigInteger): ByteArray {
        val src = bi.toByteArray()
        val dest = ByteArray(32)
        if (src.size > 32) {
            System.arraycopy(src, src.size - 32, dest, 0, 32)
        } else {
            System.arraycopy(src, 0, dest, 32 - src.size, src.size)
        }
        return dest
    }

    private fun escaparJsonString(s: String): String {
        val sb = StringBuilder(s.length + 16)
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (c.code in 0x00..0x1F) {
                        sb.append(String.format(Locale.US, "\\u%04x", c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        return sb.toString()
    }
}
