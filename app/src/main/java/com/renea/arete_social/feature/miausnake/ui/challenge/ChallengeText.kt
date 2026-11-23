package com.renea.arete_social.feature.miausnake.ui.challenge

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.renea.arete_social.feature.miausnake.core.Challenge
import com.renea.arete_social.feature.miausnake.core.ChallengeResult
import com.renea.arete_social.feature.miausnake.core.PacketError
import com.renea.arete_social.feature.miausnake.core.Rejection
import com.renea.arete_social.feature.miausnake.core.RunOutcome
import com.renea.arete_social.feature.miausnake.core.ChallengePacket
import java.util.Locale

/** Textos de los retos (en español) y utilidades de compartir. Sin lógica de reglas. */
object ChallengeText {
    fun modeName(mode: Char): String = if (mode == 'M') "Meta" else "Infinito"

    /** «1:05.3» a partir de milisegundos virtuales. */
    fun time(ms: Long): String {
        val s = ms / 1000
        return String.format(Locale.ROOT, "%d:%02d.%d", s / 60, s % 60, (ms % 1000) / 100)
    }

    fun summary(c: Challenge, o: RunOutcome): String =
        "Mapa ${c.mapNo} · ${modeName(c.mode)} · ${o.score} pts" + if (o.won) " · meta en ${time(o.elapsedMs)}" else ""

    /** Texto para enviar por chat: una línea legible y, al final, el paquete (que es lo que se importa). */
    fun shareResult(r: ChallengeResult, o: RunOutcome, packet: String): String =
        "Reto de Miau Snake · ${summary(r.challenge, o)}\nÁbrelo en Miau Snake → Retos → Recibir un reto.\n$packet"

    fun shareInvitation(c: Challenge): String =
        "Te reto en Miau Snake · Mapa ${c.mapNo} · ${modeName(c.mode)}\nÁbrelo en Miau Snake → Retos → Recibir un reto.\n${c.toCode()}"

    fun rejection(reason: Rejection): String = when (reason) {
        Rejection.RULES_VERSION -> "Es de otra versión de las reglas del juego."
        Rejection.LEVEL_VERSION -> "Ese mapa cambió de versión: la repetición ya no sirve."
        Rejection.MODE_MISMATCH -> "El modo del reto no coincide con el mapa."
        Rejection.REPLAY_INVALID -> "La repetición no es legal en este mapa (giros imposibles o no termina)."
        Rejection.SCORE_MISMATCH -> "La puntuación declarada no coincide con lo que da la repetición."
    }

    fun packetError(e: PacketError): String = when (e) {
        PacketError.NOT_A_PACKET -> "Eso no parece un resultado de Miau Snake."
        PacketError.TOO_LONG -> "El texto es demasiado largo para ser un reto."
        PacketError.BAD_ALPHABET -> "El texto tiene caracteres que no son del reto. ¿Se copió completo?"
        PacketError.TRUNCATED -> "El texto está cortado. Pide que te lo envíen de nuevo."
        PacketError.BAD_CRC -> "El texto llegó dañado (no pasa la comprobación). Pide que te lo envíen de nuevo."
        PacketError.UNSUPPORTED_FORMAT -> "Es de un formato más nuevo. Actualiza la app."
        PacketError.BAD_FIELD -> "El reto trae datos no válidos."
        PacketError.BAD_REPLAY -> "La repetición del reto no es válida."
    }

    /** Nombre del jugador listo para el paquete. */
    fun playerName(raw: String): String = ChallengePacket.sanitizeName(raw)
}

/** Compartir como texto con el selector del sistema. true si se pudo abrir. */
fun shareText(context: Context, chooserTitle: String, text: String): Boolean {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(send, chooserTitle).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    return runCatching { context.startActivity(chooser) }.isSuccess
}

/** Lee un archivo de texto con tope de caracteres. null si falla o se pasa del tope (no carga archivos enormes). */
fun readTextBounded(context: Context, uri: Uri, maxChars: Int): String? = runCatching {
    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { r ->
        val buf = CharArray(4096)
        val sb = StringBuilder()
        while (true) {
            val n = r.read(buf)
            if (n < 0) break
            sb.append(buf, 0, n)
            if (sb.length > maxChars) return@runCatching null
        }
        sb.toString()
    }
}.getOrNull()

fun writeText(context: Context, uri: Uri, text: String): Boolean = runCatching {
    context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) } != null
}.getOrDefault(false)
