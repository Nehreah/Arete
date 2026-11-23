package com.renea.arete_social.feature.miausnake.core

/**
 * Re-simula una repetición y devuelve el estado FINAL, o null si no es legal o no terminó.
 * Misma lógica que [verify], pero sin exigir una puntuación: sirve para sacar el tiempo virtual
 * (`elapsedMs`) con el que se otorgan las medallas. Nunca usa el reloj de pared.
 *
 * [onState] (opcional) recibe el estado inicial y el de cada tick jugado (el fantasma de los retos
 * se construye con él). Si la repetición resulta ilegal, ya habrá recibido estados parciales: quien
 * lo use debe descartarlos cuando el resultado sea null.
 */
fun resimulate(
    r: Replay,
    lv: Level,
    maxTicks: Long = ReplayCodec.MAX_TICK,
    onState: ((GameState) -> Unit)? = null
): GameState? {
    if (r.levelId != lv.id || r.levelVersion != lv.version || r.rulesVersion != RULES_VERSION) return null
    var s = GameEngine.initial(lv, r.seed)
    onState?.invoke(s)
    val it = r.inputs.iterator()
    var turn = if (it.hasNext()) it.next() else null
    while (s.status == GameStatus.Running && s.tick < maxTicks) {
        val t = turn
        if (t != null) {
            if (t.tick < s.tick) return null
            if (t.tick == s.tick) {
                if (t.dir == s.direction.opposite || t.dir == s.direction) return null
                s = s.copy(queue = listOf(t.dir))
                turn = if (it.hasNext()) it.next() else null
            }
        }
        s = s.applyTick(lv)
        // El tick de la muerte no mueve a la serpiente ni suma tiempo: no es un estado nuevo.
        if (onState != null && s.status != GameStatus.GameOver) onState(s)
    }
    return if (turn == null && s.isFinished) s else null
}
