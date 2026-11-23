package com.renea.arete_social.feature.mapa.mimis

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Un Mimi paseando. Su posición es FICTICIA y está anclada al MAPA (en metros del plano local),
 * así que al mover el mapa el gato se queda "pisando" el suelo y se desplaza con él, como un
 * marcador. Nunca se calcula a partir del GPS de nadie: ver un Mimi en un parque no significa que
 * su dueño esté allí.
 */
class MimiPaseante internal constructor(
    val id: String,
    posX: Float,
    posY: Float,
    rumboInicial: Float,
    private val random: Random
) {
    var x: Float = posX
        private set
    var y: Float = posY
        private set

    /** Dirección en radianes sobre el plano del mapa: 0 = este, π/2 = norte. */
    var rumbo: Float = rumboInicial
        private set
    var caminando: Boolean = true
        private set

    /** Hacia qué lado mira EN PANTALLA (ya descontado el giro del mapa). */
    var mirandoDerecha: Boolean = true
        private set

    /** Segundos desde que apareció (o fue reubicado); sirve para el efecto de aparición suave. */
    var edadSegundos: Float = 0f
        private set

    private var rumboObjetivo: Float = rumboInicial
    private var velocidadRelativa: Float = entre(VEL_REL_MIN, VEL_REL_MAX)
    private var segundosParaCambio: Float = entre(1f, 4f)

    internal fun reubicar(punto: Punto) {
        x = punto.x
        y = punto.y
        edadSegundos = 0f
        caminando = true
        segundosParaCambio = entre(1f, 4f)
    }

    internal fun avanzar(dt: Float, entorno: EntornoPaseo) {
        edadSegundos += dt
        segundosParaCambio -= dt

        if (!entorno.estaEnZona(x, y)) {
            // Se salió de la zona (llegó al borde o el mapa se movió): vuelve hacia el centro.
            caminando = true
            rumboObjetivo = atan2(entorno.centro.y - y, entorno.centro.x - x)
            segundosParaCambio = max(segundosParaCambio, 0.5f)
        } else if (segundosParaCambio <= 0f) {
            decidirSiguienteAccion()
        }

        if (caminando) {
            // Gira poco a poco, como un animal y no como un robot.
            val giroMax = VELOCIDAD_GIRO * dt
            rumbo = normalizar(rumbo + normalizar(rumboObjetivo - rumbo).coerceIn(-giroMax, giroMax))
            val paso = entorno.velocidadBase * velocidadRelativa * dt
            x += cos(rumbo) * paso
            y += sin(rumbo) * paso
        }

        // Componente "hacia la derecha de la pantalla" del rumbo, con el mapa posiblemente girado.
        val haciaDerecha = cos(rumbo + entorno.bearingRad)
        if (haciaDerecha > UMBRAL_MIRADA) mirandoDerecha = true
        else if (haciaDerecha < -UMBRAL_MIRADA) mirandoDerecha = false
    }

    private fun decidirSiguienteAccion() {
        if (caminando && random.nextFloat() < PROB_PAUSA) {
            caminando = false
            segundosParaCambio = entre(1.5f, 4f)
        } else {
            caminando = true
            rumboObjetivo = rumbo + entre(-1.3f, 1.3f)
            velocidadRelativa = entre(VEL_REL_MIN, VEL_REL_MAX)
            segundosParaCambio = entre(2f, 6f)
        }
    }

    private fun entre(a: Float, b: Float): Float = a + random.nextFloat() * (b - a)

    private fun normalizar(angulo: Float): Float {
        var a = angulo
        val dosPi = (2 * PI).toFloat()
        while (a > PI) a -= dosPi
        while (a < -PI) a += dosPi
        return a
    }

    private companion object {
        const val VEL_REL_MIN = 0.6f
        const val VEL_REL_MAX = 1.3f
        const val VELOCIDAD_GIRO = 2.2f
        const val PROB_PAUSA = 0.35f
        const val UMBRAL_MIRADA = 0.25f
    }
}

/**
 * Mantiene a los Mimis paseando. No conoce Compose, Google Maps ni ubicaciones reales:
 * recibe los ids que deben estar visibles y los hace caminar por el [EntornoPaseo].
 *
 * Los Mimis nacen en un punto ficticio de la zona visible. Si el mapa se mueve y un Mimi queda
 * fuera de pantalla, se reubica en la nueva zona visible (sin que se note).
 */
class SimulacionMimis(private val random: Random = Random.Default) {
    private val mimis = LinkedHashMap<String, MimiPaseante>()
    private var idsObjetivo: List<String> = emptyList()

    val cantidad: Int get() = mimis.size

    fun mimi(id: String): MimiPaseante? = mimis[id]

    /** Indica qué Mimis deben existir. Los que sobran se quitan ya; los nuevos nacen en el próximo [avanzar]. */
    fun sincronizar(idsVisibles: List<String>) {
        idsObjetivo = idsVisibles.distinct()
        mimis.keys.retainAll(idsObjetivo.toSet())
    }

    fun avanzar(dtSegundos: Float, entorno: EntornoPaseo) {
        val dt = dtSegundos.coerceIn(0f, 0.1f)
        crearFaltantes(entorno)
        mimis.values.forEach { mimi ->
            if (!entorno.estaVisible(mimi.x, mimi.y)) {
                puntoLibre(entorno, mimi)?.let { mimi.reubicar(it) }
            }
            mimi.avanzar(dt, entorno)
        }
    }

    private fun crearFaltantes(entorno: EntornoPaseo) {
        for (id in idsObjetivo) {
            if (id in mimis) continue
            val punto = puntoLibre(entorno, excluyendo = null) ?: return
            val rumbo = (random.nextFloat() * 2f - 1f) * PI.toFloat()
            mimis[id] = MimiPaseante(id, punto.x, punto.y, rumbo, random)
        }
    }

    /** Punto al azar de la zona, intentando no caer encima de otro Mimi (unos cuantos intentos bastan). */
    private fun puntoLibre(entorno: EntornoPaseo, excluyendo: MimiPaseante?): Punto? {
        var ultimo: Punto? = null
        repeat(INTENTOS_SEPARACION) {
            val candidato = entorno.puntoEnZona(random) ?: return null
            ultimo = candidato
            val libre = mimis.values.none { otro ->
                otro !== excluyendo && distancia(otro.x, otro.y, candidato) < entorno.separacionMinima
            }
            if (libre) return candidato
        }
        return ultimo
    }

    private fun distancia(x: Float, y: Float, p: Punto): Float {
        val dx = x - p.x
        val dy = y - p.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private companion object {
        const val INTENTOS_SEPARACION = 8
    }
}
