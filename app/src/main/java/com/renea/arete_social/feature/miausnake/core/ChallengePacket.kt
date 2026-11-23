package com.renea.arete_social.feature.miausnake.core

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.zip.CRC32

/**
 * Resultado de un reto listo para compartir: qué reto se jugó, quién, qué puntuación DECLARA y la
 * repetición. La puntuación declarada nunca se cree: quien lo recibe re-simula ([verifyChallengeResult]).
 */
data class ChallengeResult(
    val challenge: Challenge,
    val levelVersion: Int,
    val player: String,
    val claimedScore: Int,
    val inputs: List<TurnInput>
)

enum class PacketError { NOT_A_PACKET, TOO_LONG, BAD_ALPHABET, TRUNCATED, BAD_CRC, UNSUPPORTED_FORMAT, BAD_FIELD, BAD_REPLAY }

sealed interface PacketDecode {
    data class Ok(val result: ChallengeResult) : PacketDecode
    data class Bad(val error: PacketError) : PacketDecode
}

/**
 * Base64 URL-safe sin relleno y CANÓNICO (los bits sobrantes deben ser 0, así un mismo contenido
 * tiene un único texto). Propio para no depender del nivel de API de Android.
 */
internal object Base64Url {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private val INDEX = IntArray(128) { -1 }.also { t -> for (i in ALPHABET.indices) t[ALPHABET[i].code] = i }

    fun isAlphabet(c: Char): Boolean = c.code < 128 && INDEX[c.code] >= 0

    fun encode(b: ByteArray): String {
        val sb = StringBuilder((b.size * 4 + 2) / 3)
        var i = 0
        while (i < b.size) {
            val n = b.size - i
            val b0 = b[i].toInt() and 0xFF
            val b1 = if (n > 1) b[i + 1].toInt() and 0xFF else 0
            val b2 = if (n > 2) b[i + 2].toInt() and 0xFF else 0
            val v = (b0 shl 16) or (b1 shl 8) or b2
            sb.append(ALPHABET[(v shr 18) and 63])
            sb.append(ALPHABET[(v shr 12) and 63])
            if (n > 1) sb.append(ALPHABET[(v shr 6) and 63])
            if (n > 2) sb.append(ALPHABET[v and 63])
            i += 3
        }
        return sb.toString()
    }

    /** null si hay un carácter fuera del alfabeto, una longitud imposible o bits sobrantes distintos de 0. */
    fun decode(s: String): ByteArray? {
        if (s.length % 4 == 1) return null
        val out = ByteArray(s.length * 3 / 4)
        var o = 0
        var i = 0
        while (i < s.length) {
            val n = minOf(4, s.length - i)
            var v = 0
            for (k in 0 until 4) {
                val d = if (k < n) {
                    val c = s[i + k].code
                    if (c < 128) INDEX[c] else -1
                } else {
                    0
                }
                if (d < 0) return null
                v = (v shl 6) or d
            }
            val b0 = (v shr 16) and 0xFF
            val b1 = (v shr 8) and 0xFF
            val b2 = v and 0xFF
            when (n) {
                4 -> {
                    out[o++] = b0.toByte(); out[o++] = b1.toByte(); out[o++] = b2.toByte()
                }
                3 -> {
                    if (b2 != 0) return null
                    out[o++] = b0.toByte(); out[o++] = b1.toByte()
                }
                else -> {
                    if (b1 != 0 || b2 != 0) return null
                    out[o++] = b0.toByte()
                }
            }
            i += 4
        }
        return out
    }
}

/**
 * Paquete de resultado de reto, en texto: `MIAUR1:` + base64url de
 *
 *     formato(1) · reglas(varint) · mapa(1) · modo(1: 0=I, 1=M) · semilla(3, big-endian)
 *     · versiónMapa(varint) · puntos(varint) · largoNombre(1) · nombre(UTF-8) · repetición · CRC32(4)
 *
 * El CRC32 solo detecta copias dañadas (chat, QR mal leído); NO es una firma. La autoridad es la
 * re-simulación. El decodificador no confía en nada: acota texto, bytes, nombre, giros y ticks, y
 * devuelve un motivo en lugar de lanzar excepciones.
 */
object ChallengePacket {
    const val PREFIX = "MIAUR1:"
    const val FORMAT = 1
    const val MAX_NAME_BYTES = 24
    const val MAX_TEXT_CHARS = 90_000
    /** Entrada total aceptada (un mensaje puede traer texto alrededor del paquete). */
    const val MAX_INPUT_CHARS = MAX_TEXT_CHARS + 2_000
    const val MAX_LEVEL_VERSION = 10_000
    const val MAX_SCORE = 10_000_000
    private const val MIN_BYTES = 14

    fun encode(r: ChallengeResult): String {
        val c = r.challenge
        require(c.mapNo in 1..20) { "mapa fuera de rango" }
        require(c.mode == 'I' || c.mode == 'M') { "modo inválido" }
        require(c.rules in 0..999) { "reglas fuera de rango" }
        require(r.levelVersion in 1..MAX_LEVEL_VERSION) { "versión de mapa fuera de rango" }
        require(r.claimedScore in 0..MAX_SCORE) { "puntuación fuera de rango" }
        val name = sanitizeName(r.player).toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream()
        out.write(FORMAT)
        writeVarint(out, c.rules.toLong())
        out.write(c.mapNo)
        out.write(if (c.mode == 'I') 0 else 1)
        val seed = c.seed and 0xFFFFFFL
        out.write(((seed shr 16) and 0xFF).toInt())
        out.write(((seed shr 8) and 0xFF).toInt())
        out.write((seed and 0xFF).toInt())
        writeVarint(out, r.levelVersion.toLong())
        writeVarint(out, r.claimedScore.toLong())
        out.write(name.size)
        out.write(name)
        out.write(ReplayCodec.encode(r.inputs))
        val body = out.toByteArray()
        val crc = CRC32().also { it.update(body, 0, body.size) }.value
        val full = ByteArray(body.size + 4)
        System.arraycopy(body, 0, full, 0, body.size)
        for (k in 0 until 4) full[body.size + k] = ((crc shr (24 - 8 * k)) and 0xFF).toByte()
        return PREFIX + Base64Url.encode(full)
    }

    fun decode(text: String): PacketDecode {
        if (text.length > MAX_INPUT_CHARS) return PacketDecode.Bad(PacketError.TOO_LONG)
        val token = extract(text) ?: return PacketDecode.Bad(PacketError.NOT_A_PACKET)
        if (token.length > MAX_TEXT_CHARS) return PacketDecode.Bad(PacketError.TOO_LONG)
        val raw = Base64Url.decode(token.substring(PREFIX.length)) ?: return PacketDecode.Bad(PacketError.BAD_ALPHABET)
        if (raw.size < MIN_BYTES) return PacketDecode.Bad(PacketError.TRUNCATED)

        val end = raw.size - 4
        var stored = 0L
        for (k in 0 until 4) stored = (stored shl 8) or (raw[end + k].toLong() and 0xFF)
        val crc = CRC32().also { it.update(raw, 0, end) }.value
        if (crc != stored) return PacketDecode.Bad(PacketError.BAD_CRC)

        val rd = Reader(raw, end)
        if (rd.byte() != FORMAT) return PacketDecode.Bad(PacketError.UNSUPPORTED_FORMAT)
        val rules = rd.varint()
        if (rules !in 0L..999L) return bad()
        val mapNo = rd.byte()
        if (mapNo !in 1..20) return bad()
        val modeByte = rd.byte()
        if (modeByte != 0 && modeByte != 1) return bad()
        val s0 = rd.byte(); val s1 = rd.byte(); val s2 = rd.byte()
        if (s0 < 0 || s1 < 0 || s2 < 0) return bad()
        val seed = ((s0 shl 16) or (s1 shl 8) or s2).toLong()
        val levelVersion = rd.varint()
        if (levelVersion !in 1L..MAX_LEVEL_VERSION.toLong()) return bad()
        val score = rd.varint()
        if (score !in 0L..MAX_SCORE.toLong()) return bad()
        val nameLen = rd.byte()
        if (nameLen !in 0..MAX_NAME_BYTES || rd.remaining() < nameLen) return bad()
        val name = decodeName(raw, rd.pos, nameLen) ?: return bad()
        rd.skip(nameLen)

        val inputs = ReplayCodec.decode(raw.copyOfRange(rd.pos, end))
            ?: return PacketDecode.Bad(PacketError.BAD_REPLAY)
        val challenge = Challenge(rules.toInt(), mapNo, if (modeByte == 0) 'I' else 'M', seed)
        return PacketDecode.Ok(ChallengeResult(challenge, levelVersion.toInt(), name, score.toInt(), inputs))
    }

    /** true si el texto contiene un paquete (aunque esté dañado). */
    fun looksLikePacket(text: String): Boolean = text.length <= MAX_INPUT_CHARS && text.contains(PREFIX)

    /**
     * Saca el paquete de un texto pegado. Si el texto ES el paquete, tolera saltos de línea
     * intermedios (algunas apps parten líneas largas); si va dentro de un mensaje, toma los
     * caracteres contiguos que siguen al prefijo.
     */
    fun extract(text: String): String? {
        if (text.length > MAX_INPUT_CHARS) return null
        val t = text.trim()
        val start = t.indexOf(PREFIX)
        if (start < 0) return null
        val tail = t.substring(start)
        if (start == 0 && tail.all { it.isWhitespace() || isTokenChar(it) }) {
            return tail.filterNot { it.isWhitespace() }
        }
        var end = PREFIX.length
        while (end < tail.length && isTokenChar(tail[end])) end++
        return tail.substring(0, end)
    }

    /** Nombre de jugador seguro para mostrar: sin controles ni caracteres invisibles, ≤ 24 bytes UTF-8. */
    fun sanitizeName(raw: String): String {
        val sb = StringBuilder()
        var bytes = 0
        var pendingSpace = false
        var i = 0
        while (i < raw.length) {
            val cp = raw.codePointAt(i)
            i += Character.charCount(cp)
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                pendingSpace = sb.isNotEmpty()
                continue
            }
            val type = Character.getType(cp)
            if (type == Character.CONTROL.toInt() || type == Character.FORMAT.toInt() ||
                type == Character.SURROGATE.toInt() || type == Character.UNASSIGNED.toInt() ||
                type == Character.PRIVATE_USE.toInt()
            ) continue
            val size = when {
                cp < 0x80 -> 1
                cp < 0x800 -> 2
                cp < 0x10000 -> 3
                else -> 4
            }
            val extra = if (pendingSpace) 1 else 0
            if (bytes + extra + size > MAX_NAME_BYTES) break
            if (pendingSpace) { sb.append(' '); bytes += 1; pendingSpace = false }
            sb.appendCodePoint(cp)
            bytes += size
        }
        return sb.toString()
    }

    private fun bad() = PacketDecode.Bad(PacketError.BAD_FIELD)

    private fun isTokenChar(c: Char): Boolean =
        c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_' || c == ':'

    /** UTF-8 estricto y en forma canónica (lo que produce [sanitizeName]); si no, null. */
    private fun decodeName(b: ByteArray, off: Int, len: Int): String? = try {
        val s = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(b, off, len))
            .toString()
        if (s == sanitizeName(s)) s else null
    } catch (e: CharacterCodingException) {
        null
    }

    private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while (v >= 0x80L) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write(v.toInt())
    }

    private class Reader(private val b: ByteArray, private val end: Int) {
        var pos = 0
            private set

        fun remaining(): Int = end - pos
        fun skip(n: Int) { pos += n }

        /** -1 si no quedan bytes. */
        fun byte(): Int = if (pos < end) b[pos++].toInt() and 0xFF else -1

        /** -1 si está truncado, es demasiado largo o desborda 63 bits. */
        fun varint(): Long {
            var v = 0L
            var shift = 0
            while (true) {
                val x = byte()
                if (x < 0 || shift > 56) return -1L
                v = v or ((x and 0x7F).toLong() shl shift)
                if (x < 0x80) return if (v < 0L) -1L else v
                shift += 7
            }
        }
    }
}
