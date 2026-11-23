package com.renea.arete_social.feature.miausnake.data

import android.content.Context
import com.renea.arete_social.feature.miausnake.levels.ProgressBook

/** Persistencia de Miau Snake. Una interfaz para poder probar la lógica sin Android. */
interface SnakeStore {
    fun loadProgress(): ProgressBook
    fun saveProgress(book: ProgressBook)
    fun loadPrefs(): SnakePrefs
    fun savePrefs(prefs: SnakePrefs)
}

class InMemorySnakeStore : SnakeStore {
    private var book = ProgressBook.EMPTY
    private var prefs = SnakePrefs()
    override fun loadProgress() = book
    override fun saveProgress(book: ProgressBook) { this.book = book }
    override fun loadPrefs() = prefs
    override fun savePrefs(prefs: SnakePrefs) { this.prefs = prefs }
}

/** SharedPreferences, igual que el resto de las preferencias locales de Aréte. */
class SharedPrefsSnakeStore(context: Context) : SnakeStore {
    private val sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun loadProgress(): ProgressBook = ProgressBook.fromText(sp.getString(KEY_PROGRESS, null))

    override fun saveProgress(book: ProgressBook) {
        sp.edit().putString(KEY_PROGRESS, book.toText()).apply()
    }

    override fun loadPrefs(): SnakePrefs = SnakePrefsCodec.decode(sp.getString(KEY_PREFS, null))

    override fun savePrefs(prefs: SnakePrefs) {
        sp.edit().putString(KEY_PREFS, SnakePrefsCodec.encode(prefs)).apply()
    }

    private companion object {
        const val FILE = "arete_miausnake"
        const val KEY_PROGRESS = "progress_v1"
        const val KEY_PREFS = "prefs_v1"
    }
}
