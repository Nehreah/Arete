package com.renea.arete_social.feature.miausnake.social

import com.renea.arete_social.feature.miausnake.core.Challenge
import com.renea.arete_social.feature.miausnake.core.ChallengePacket
import com.renea.arete_social.feature.miausnake.core.ChallengeResult
import com.renea.arete_social.feature.miausnake.core.Level
import com.renea.arete_social.feature.miausnake.core.PacketDecode
import com.renea.arete_social.feature.miausnake.core.PacketError
import com.renea.arete_social.feature.miausnake.core.RULES_VERSION
import com.renea.arete_social.feature.miausnake.core.Rejection
import com.renea.arete_social.feature.miausnake.core.RunOutcome
import com.renea.arete_social.feature.miausnake.core.Verdict
import com.renea.arete_social.feature.miausnake.core.verifyChallengeResult

/** Convierte (número de mapa, modo 'I'/'M') en el [Level] del catálogo. null si no existe. */
fun interface LevelResolver {
    fun resolve(mapNo: Int, mode: Char): Level?
}

/** Qué salió de un texto recibido, venga de pegar, de un archivo, de un QR o de la malla. */
sealed interface ImportOutcome {
    /** Un código de reto sin resultado (`MIAU1-17-I-7F3A9C`): se puede jugar. */
    data class Invitation(val challenge: Challenge, val level: Level) : ImportOutcome

    /** Resultado de otra persona cuya repetición se re-simuló y es legal y coherente. */
    data class Verified(val result: ChallengeResult, val outcome: RunOutcome, val level: Level) : ImportOutcome

    /** Repetición ilegal o puntuación declarada falsa: se rechaza. */
    data class Invalid(val result: ChallengeResult, val reason: Rejection) : ImportOutcome

    /** El texto parece un paquete pero está dañado o no es de un formato conocido. */
    data class Unreadable(val error: PacketError) : ImportOutcome

    /** Es de otra versión de reglas de Miau Snake. */
    data class UnsupportedRules(val rules: Int) : ImportOutcome

    /** El mapa no existe en este teléfono. */
    data class UnknownMap(val mapNo: Int) : ImportOutcome

    /** No hay ningún código de reto en el texto. */
    data object NothingFound : ImportOutcome
}

/**
 * Punto único de entrada de retos recibidos. Nunca lanza por datos externos y nunca cree la
 * puntuación declarada: re-simula. CPU pesada en el peor caso: llamar fuera del hilo principal.
 */
object ChallengeImporter {
    private val CODE = Regex("MIAU\\d{1,3}-\\d{1,2}-[IM]-[0-9A-F]{6}")

    fun import(text: String, resolver: LevelResolver): ImportOutcome {
        if (ChallengePacket.looksLikePacket(text)) return importResult(text, resolver)
        if (text.length > ChallengePacket.MAX_INPUT_CHARS) return ImportOutcome.Unreadable(PacketError.TOO_LONG)
        val code = CODE.find(text)?.value ?: return ImportOutcome.NothingFound
        val c = Challenge.parse(code) ?: return ImportOutcome.NothingFound
        if (c.rules != RULES_VERSION) return ImportOutcome.UnsupportedRules(c.rules)
        val level = runCatching { resolver.resolve(c.mapNo, c.mode) }.getOrNull()
            ?: return ImportOutcome.UnknownMap(c.mapNo)
        return ImportOutcome.Invitation(c, level)
    }

    private fun importResult(text: String, resolver: LevelResolver): ImportOutcome {
        val r = when (val d = ChallengePacket.decode(text)) {
            is PacketDecode.Bad -> return ImportOutcome.Unreadable(d.error)
            is PacketDecode.Ok -> d.result
        }
        if (r.challenge.rules != RULES_VERSION) return ImportOutcome.UnsupportedRules(r.challenge.rules)
        val level = runCatching { resolver.resolve(r.challenge.mapNo, r.challenge.mode) }.getOrNull()
            ?: return ImportOutcome.UnknownMap(r.challenge.mapNo)
        return when (val v = verifyChallengeResult(r, level)) {
            is Verdict.Valid -> ImportOutcome.Verified(r, v.outcome, level)
            is Verdict.Rejected -> ImportOutcome.Invalid(r, v.reason)
        }
    }
}
