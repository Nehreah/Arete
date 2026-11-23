package com.renea.arete_social.testutils

import android.content.SharedPreferences

/**
 * Implementación en memoria ligera de [SharedPreferences] para pruebas unitarias en JVM
 * sin requerir dobles de Mockito ni el runtime de Android.
 */
class FakeSharedPreferences : SharedPreferences {

    private val storage = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = HashMap(storage)

    override fun getString(key: String?, defValue: String?): String? =
        (storage[key] as? String) ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (storage[key] as? MutableSet<String>) ?: defValues

    override fun getInt(key: String?, defValue: Int): Int =
        (storage[key] as? Int) ?: defValue

    override fun getLong(key: String?, defValue: Long): Long =
        (storage[key] as? Long) ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float =
        (storage[key] as? Float) ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        (storage[key] as? Boolean) ?: defValue

    override fun contains(key: String?): Boolean = storage.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    inner class FakeEditor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) {
                removals.remove(key)
                pending[key] = value
            }
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            if (key != null) {
                removals.remove(key)
                pending[key] = values
            }
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) {
                removals.remove(key)
                pending[key] = value
            }
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) {
                removals.remove(key)
                pending[key] = value
            }
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) {
                removals.remove(key)
                pending[key] = value
            }
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) {
                removals.remove(key)
                pending[key] = value
            }
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) {
                pending.remove(key)
                removals.add(key)
            }
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearRequested = true
            pending.clear()
            removals.clear()
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearRequested) {
                storage.clear()
            }
            removals.forEach { storage.remove(it) }
            storage.putAll(pending)
        }
    }
}
