package com.renea.arete_social.feature.miausnake.data

import com.renea.arete_social.feature.miausnake.levels.GameMode
import com.renea.arete_social.feature.miausnake.ui.play.ControlMode
import com.renea.arete_social.feature.miausnake.ui.play.PlaySettings

/** Lo que Miau Snake recuerda entre sesiones además de los récords: ajustes, último modo y mapa. */
data class SnakePrefs(
    val settings: PlaySettings = PlaySettings(),
    val mode: GameMode = GameMode.META,
    val lastMapId: String? = null
)

/**
 * Formato de texto `clave=valor`, una por línea. Tolerante: lo desconocido o roto se ignora y cae
 * al valor por defecto. `interpolate` y `showDebug` son de depuración y no se guardan.
 */
object SnakePrefsCodec {
    private val mapId = Regex("[a-z0-9_]{1,32}")

    fun encode(p: SnakePrefs): String = buildList {
        add("control=${p.settings.control.name}")
        add("left=${p.settings.leftHanded.bit()}")
        add("vibration=${p.settings.vibration.bit()}")
        add("contrast=${p.settings.highContrast.bit()}")
        add("relaxed=${p.settings.relaxed.bit()}")
        add("mode=${p.mode.code}")
        p.lastMapId?.let { add("map=$it") }
    }.joinToString("\n")

    fun decode(text: String?): SnakePrefs {
        if (text.isNullOrBlank()) return SnakePrefs()
        val kv = HashMap<String, String>()
        for (line in text.lineSequence()) {
            val i = line.indexOf('=')
            if (i > 0) kv[line.substring(0, i).trim()] = line.substring(i + 1).trim()
        }
        val base = PlaySettings()
        val settings = base.copy(
            control = ControlMode.entries.firstOrNull { it.name == kv["control"] } ?: base.control,
            leftHanded = kv["left"]?.let { it == "1" } ?: base.leftHanded,
            vibration = kv["vibration"]?.let { it == "1" } ?: base.vibration,
            highContrast = kv["contrast"]?.let { it == "1" } ?: base.highContrast,
            relaxed = kv["relaxed"]?.let { it == "1" } ?: base.relaxed
        )
        val mode = kv["mode"]?.singleOrNull()?.let { GameMode.fromCode(it) } ?: GameMode.META
        val map = kv["map"]?.takeIf { mapId.matches(it) }
        return SnakePrefs(settings, mode, map)
    }

    private fun Boolean.bit() = if (this) "1" else "0"
}
