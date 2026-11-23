package com.renea.arete_social.feature.tamagotchi.superpoderes

import com.renea.arete_social.domain.util.Reloj
import com.renea.arete_social.feature.tamagotchi.data.PetRepository
import com.renea.arete_social.feature.tamagotchi.logic.modoSuperActivo
import com.renea.arete_social.feature.tamagotchi.model.PetBalance
import com.renea.arete_social.feature.tamagotchi.model.PetEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * Dice en todo momento si el Michi de esta persona está en Modo Súper, para que el resto de la app
 * (mapa, lista de chats) reaccione sin conocer los detalles de la mascota.
 *
 * Observa a la mascota y, además, reevalúa la hora cada [intervaloReevaluacionMs] para que el Súper
 * se apague a tiempo aunque la mascota no cambie (su vencimiento depende solo del reloj).
 * Las reglas viven en feature/tamagotchi/logic/ModoSuper.kt; aquí solo se conectan con el resto.
 */
class ServicioModoSuper(
    private val repositorio: PetRepository,
    private val scope: CoroutineScope,
    private val reloj: Reloj = Reloj.Sistema,
    private val intervaloReevaluacionMs: Long = 30_000L,
    /**
     * Se llama cuando el Modo Súper se ACTIVA (no al cargar con un Súper que ya estaba en marcha).
     * ContenedorApp lo usa para avisar con una notificación si la app no está a la vista.
     */
    private val alActivarse: ((PetEntity) -> Unit)? = null
) {
    data class Estado(
        val activo: Boolean = false,
        /** Instante en que se acaba el Súper (null si no está activo). */
        val venceEn: Long? = null
    )

    private val _estado = MutableStateFlow(Estado())
    val estado: StateFlow<Estado> = _estado.asStateFlow()

    private var trabajo: Job? = null
    private var usuarioActual: String? = null

    /** Instante de activación visto en la emisión anterior (null hasta la primera: así no se avisa al arrancar). */
    private var superDesdeAnterior: Long? = null

    /** Empieza a observar la mascota de [usuarioId]. Es seguro llamarlo varias veces con el mismo usuario. */
    @Synchronized
    fun iniciar(usuarioId: String) {
        if (usuarioActual == usuarioId && trabajo?.isActive == true) return
        trabajo?.cancel()
        usuarioActual = usuarioId
        superDesdeAnterior = null
        trabajo = scope.launch {
            // El pulso solo sirve para volver a evaluar con el paso del tiempo; la hora que se usa para
            // decidir es SIEMPRE la actual (con la del pulso, una activación recién hecha parecería «futura»
            // y se perdería el aviso).
            val pulso = flow {
                while (true) {
                    emit(Unit)
                    delay(intervaloReevaluacionMs)
                }
            }
            combine(repositorio.observePet(usuarioId), pulso) { pet, _ ->
                val ahora = reloj.ahora()
                avisarSiSeActivo(pet, ahora)
                calcular(pet, ahora)
            }.collect { _estado.value = it }
        }
    }

    /** Deja de observar y apaga el Súper (cerrar sesión). */
    @Synchronized
    fun detener() {
        trabajo?.cancel()
        trabajo = null
        usuarioActual = null
        superDesdeAnterior = null
        _estado.value = Estado()
    }

    /** Avisa solo cuando `superDesdeMillis` avanza respecto a lo ya visto y el Súper está en marcha. */
    private fun avisarSiSeActivo(pet: PetEntity?, ahora: Long) {
        if (pet == null) return
        val antes = superDesdeAnterior
        superDesdeAnterior = pet.superDesdeMillis
        if (antes != null && pet.superDesdeMillis > antes && pet.modoSuperActivo(ahora)) {
            runCatching { alActivarse?.invoke(pet) }
        }
    }

    internal fun calcular(pet: PetEntity?, ahora: Long): Estado {
        if (pet == null || !pet.modoSuperActivo(ahora)) return Estado()
        return Estado(activo = true, venceEn = pet.superDesdeMillis + PetBalance.SUPER_DURATION_MILLIS)
    }
}
