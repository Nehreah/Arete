package com.renea.arete_social.feature.miausnake.data

import android.content.Context
import com.renea.arete_social.feature.miausnake.match.MatchLedger
import com.renea.arete_social.feature.miausnake.social.ChallengeBook
import com.renea.arete_social.feature.miausnake.social.DailyBook
import com.renea.arete_social.feature.miausnake.social.DeferredOutbox
import com.renea.arete_social.feature.miausnake.social.SeenIds

/** Persistencia social de Miau Snake (retos, reto diario, cola diferida, duelos cercanos y última medición de transporte). Interfaz para probar sin Android. */
interface SocialStore {
    fun loadChallenges(): ChallengeBook
    fun saveChallenges(book: ChallengeBook)
    fun loadDaily(): DailyBook
    fun saveDaily(book: DailyBook)
    fun loadOutbox(): DeferredOutbox
    fun saveOutbox(outbox: DeferredOutbox)
    fun loadSeen(): SeenIds
    fun saveSeen(seen: SeenIds)

    /** Fase 6: historial de duelos cercanos. */
    fun loadMatches(): MatchLedger
    fun saveMatches(ledger: MatchLedger)

    /** Fase 6: última medición del transporte (texto de `SpikeReport`), o null si nunca se midió. */
    fun loadSpike(): String?
    fun saveSpike(text: String)
}

class InMemorySocialStore : SocialStore {
    private var challenges = ChallengeBook.EMPTY
    private var daily = DailyBook.EMPTY
    private var outbox = DeferredOutbox.EMPTY
    private var seen = SeenIds.EMPTY
    private var matches = MatchLedger.EMPTY
    private var spike: String? = null
    override fun loadChallenges() = challenges
    override fun saveChallenges(book: ChallengeBook) { challenges = book }
    override fun loadDaily() = daily
    override fun saveDaily(book: DailyBook) { daily = book }
    override fun loadOutbox() = outbox
    override fun saveOutbox(outbox: DeferredOutbox) { this.outbox = outbox }
    override fun loadSeen() = seen
    override fun saveSeen(seen: SeenIds) { this.seen = seen }
    override fun loadMatches() = matches
    override fun saveMatches(ledger: MatchLedger) { matches = ledger }
    override fun loadSpike() = spike
    override fun saveSpike(text: String) { spike = text }
}

/** SharedPreferences propias de lo social (archivo aparte de los récords: no se pisan). */
class SharedPrefsSocialStore(context: Context) : SocialStore {
    private val sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun loadChallenges() = ChallengeBook.fromText(sp.getString(KEY_CHALLENGES, null))
    override fun saveChallenges(book: ChallengeBook) { sp.edit().putString(KEY_CHALLENGES, book.toText()).apply() }
    override fun loadDaily() = DailyBook.fromText(sp.getString(KEY_DAILY, null))
    override fun saveDaily(book: DailyBook) { sp.edit().putString(KEY_DAILY, book.toText()).apply() }
    override fun loadOutbox() = DeferredOutbox.fromText(sp.getString(KEY_OUTBOX, null))
    override fun saveOutbox(outbox: DeferredOutbox) { sp.edit().putString(KEY_OUTBOX, outbox.toText()).apply() }
    override fun loadSeen() = SeenIds.fromText(sp.getString(KEY_SEEN, null))
    override fun saveSeen(seen: SeenIds) { sp.edit().putString(KEY_SEEN, seen.toText()).apply() }
    override fun loadMatches() = MatchLedger.fromText(sp.getString(KEY_MATCHES, null))
    override fun saveMatches(ledger: MatchLedger) { sp.edit().putString(KEY_MATCHES, ledger.toText()).apply() }
    override fun loadSpike(): String? = sp.getString(KEY_SPIKE, null)
    override fun saveSpike(text: String) { sp.edit().putString(KEY_SPIKE, text).apply() }

    private companion object {
        const val FILE = "arete_miausnake_social"
        const val KEY_CHALLENGES = "challenges_v1"
        const val KEY_DAILY = "daily_v1"
        const val KEY_OUTBOX = "outbox_v1"
        const val KEY_SEEN = "seen_v1"
        const val KEY_MATCHES = "matches_v1"
        const val KEY_SPIKE = "spike_v1"
    }
}
