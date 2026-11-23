package com.renea.arete_social.feature.tamagotchi.data

import android.content.Context
import android.content.SharedPreferences
import com.renea.arete_social.data.local.ajustes.AjustesParche
import com.renea.arete_social.feature.tamagotchi.logic.AccionResultado
import com.renea.arete_social.feature.tamagotchi.logic.SesionesJuego
import com.renea.arete_social.feature.tamagotchi.logic.acariciar
import com.renea.arete_social.feature.tamagotchi.logic.alimentar
import com.renea.arete_social.feature.tamagotchi.logic.alternarSueno
import com.renea.arete_social.feature.tamagotchi.logic.applyTick
import com.renea.arete_social.feature.tamagotchi.logic.banar
import com.renea.arete_social.feature.tamagotchi.logic.curar
import com.renea.arete_social.feature.tamagotchi.logic.ganarChispaPorEncuentro
import com.renea.arete_social.feature.tamagotchi.logic.inicioDiaMillis
import com.renea.arete_social.feature.tamagotchi.logic.jugar
import com.renea.arete_social.feature.tamagotchi.logic.recibirChispas
import com.renea.arete_social.feature.tamagotchi.logic.recibirMedicina
import com.renea.arete_social.feature.tamagotchi.logic.regalarChispas
import com.renea.arete_social.feature.tamagotchi.logic.renombrar
import com.renea.arete_social.feature.tamagotchi.logic.reiniciar
import com.renea.arete_social.feature.personajes.model.Temperamento
import com.renea.arete_social.feature.tamagotchi.model.AperturaSesionJuego
import com.renea.arete_social.feature.tamagotchi.model.CierreSesionJuego
import com.renea.arete_social.feature.tamagotchi.model.FoodType
import com.renea.arete_social.feature.tamagotchi.model.LibroSesionesJuego
import com.renea.arete_social.feature.tamagotchi.model.PetBalance
import com.renea.arete_social.feature.tamagotchi.model.PetEntity
import com.renea.arete_social.feature.tamagotchi.model.PetStage
import com.renea.arete_social.feature.tamagotchi.model.PetType
import com.renea.arete_social.feature.tamagotchi.model.RecompensaJuego
import com.renea.arete_social.feature.tamagotchi.model.ResultadoCuidado
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementación 100 % offline, reactiva y persistente de [PetRepository]
 * (SharedPreferences + kotlinx.serialization). Todas las escrituras pasan por un [Mutex],
 * así dos acciones simultáneas nunca se pisan.
 */
class PetRepositoryLocal(context: Context) : PetRepository {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("antired_tamagotchi_db", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true   // JSON antiguo con campos que ya no existen (p. ej. cyberIq)
        encodeDefaults = true
    }

    private val petFlows = ConcurrentHashMap<String, MutableStateFlow<PetEntity?>>()
    private val mutex = Mutex()

    private fun getOrCreatePetFlow(ownerId: String): MutableStateFlow<PetEntity?> =
        petFlows.computeIfAbsent(ownerId) { id ->
            val raw = prefs.getString(clave(id), null)
            val pet = raw?.let { runCatching { json.decodeFromString<PetEntity>(it) }.getOrNull() }
                ?.let { migrarSiNecesario(it, id) }
            MutableStateFlow(pet)
        }

    /**
     * Sube mascotas guardadas con un esquema viejo al actual. Los campos nuevos ya toman
     * su valor por defecto al decodificar; aquí solo se re-guardan para no repetir el paso.
     * Nunca destruye datos y es idempotente.
     */
    private fun migrarSiNecesario(pet: PetEntity, ownerId: String): PetEntity {
        if (pet.schemaVersion >= SCHEMA_ACTUAL) return pet
        val migrada = pet.copy(
            schemaVersion = SCHEMA_ACTUAL,
            // v4: «Michi» es el nombre por defecto. Hasta ahora no se podía editar, así que un «Mimi»
            // guardado es el nombre antiguo por defecto y no uno elegido por la persona.
            name = if (pet.schemaVersion < 4 && pet.name == "Mimi") PetBalance.DEFAULT_NAME else pet.name,
            lastDayResetMillis = if (pet.lastDayResetMillis == 0L) inicioDiaMillis(System.currentTimeMillis()) else pet.lastDayResetMillis,
            lastTickMillis = if (pet.lastTickMillis == 0L) System.currentTimeMillis() else pet.lastTickMillis,
            // v7: Identidad pública única para interacciones sociales
            personajeId = if (pet.personajeId.isBlank()) UUID.randomUUID().toString() else pet.personajeId
        )
        guardarPet(ownerId, migrada)
        return migrada
    }

    override fun observePet(ownerId: String): Flow<PetEntity?> = getOrCreatePetFlow(ownerId).asStateFlow()

    override suspend fun ensurePetExists(ownerId: String, name: String, type: PetType) {
        mutex.withLock {
            val flow = getOrCreatePetFlow(ownerId)
            if (flow.value == null) {
                val ahora = System.currentTimeMillis()
                val pet = PetEntity(
                    ownerId = ownerId,
                    name = name,
                    petType = type,
                    personajeId = UUID.randomUUID().toString(),
                    temperamento = Temperamento.CURIOSO,
                    stage = PetStage.HUEVO,
                    birthMillis = ahora,
                    lastTickMillis = ahora,
                    lastDayResetMillis = inicioDiaMillis(ahora)
                )
                flow.value = pet
                guardarPet(ownerId, pet)
            }
        }
    }

    override suspend fun tick(ownerId: String) {
        mutate(ownerId) { AccionResultado(it, ResultadoCuidado.OK) }
    }

    override suspend fun feed(ownerId: String, food: FoodType) = mutate(ownerId) { it.alimentar(food) }
    override suspend fun play(ownerId: String) = mutate(ownerId) { it.jugar() }
    override suspend fun clean(ownerId: String) = mutate(ownerId) { it.banar() }
    override suspend fun heal(ownerId: String) = mutate(ownerId) { it.curar() }
    override suspend fun toggleSleep(ownerId: String) = mutate(ownerId) { it.alternarSueno() }
    override suspend fun cuddle(ownerId: String) = mutate(ownerId) { it.acariciar() }
    override suspend fun reboot(ownerId: String) = mutate(ownerId) { it.reiniciar() }
    override suspend fun earnSparkFromFriend(ownerId: String) = mutate(ownerId) { it.ganarChispaPorEncuentro() }
    override suspend fun giftSparks(ownerId: String, amount: Int) = mutate(ownerId) { it.regalarChispas(amount) }
    override suspend fun rename(ownerId: String, name: String) = mutate(ownerId) { it.renombrar(name) }
    override suspend fun receiveSparks(ownerId: String, amount: Int) = mutate(ownerId) { it.recibirChispas(amount) }
    override suspend fun receiveMedicine(ownerId: String) = mutate(ownerId) { it.recibirMedicina() }

    override suspend fun actualizarPet(ownerId: String, pet: PetEntity) {
        mutex.withLock {
            val flow = getOrCreatePetFlow(ownerId)
            flow.value = pet
            guardarPet(ownerId, pet)
        }
    }

    /**
     * Aplica una acción de forma atómica:
     * 1) lee la mascota, 2) la pone al día con el tiempo real, 3) ejecuta la acción,
     * 4) guarda y publica el resultado.
     * Se guarda siempre (aunque la acción se rechace) porque poner al día ya cambió valores.
     */
    private suspend fun mutate(ownerId: String, accion: (PetEntity) -> AccionResultado): ResultadoCuidado =
        mutex.withLock {
            val flow = getOrCreatePetFlow(ownerId)
            val actual = flow.value ?: return@withLock ResultadoCuidado.SIN_MASCOTA
            val alDia = actual.applyTick(factorDesgaste = if (AjustesParche.activado(appContext)) 1.6 else 1.0)
            val resultado = accion(alDia)
            flow.value = resultado.pet
            guardarPet(ownerId, resultado.pet)
            resultado.resultado
        }

    // ── Sesiones de juego externo (Miau Snake) ───────────────────────────────

    override suspend fun abrirSesionJuego(ownerId: String, sesionId: String): AperturaSesionJuego =
        transaccionSesion<AperturaSesionJuego>(ownerId, sinMascota = AperturaSesionJuego.Rechazada(ResultadoCuidado.SIN_MASCOTA)) { pet, libro, ahora ->
            val r = SesionesJuego.abrir(pet, libro, sesionId, ahora)
            Triple(pet, r.libro, r.resultado)
        }

    override suspend fun cerrarSesionJuego(ownerId: String, sesionId: String, recompensa: RecompensaJuego): CierreSesionJuego =
        transaccionSesion<CierreSesionJuego>(ownerId, sinMascota = CierreSesionJuego.Rechazado(ResultadoCuidado.SIN_MASCOTA)) { pet, libro, ahora ->
            val r = SesionesJuego.cerrar(pet, libro, sesionId, recompensa, ahora)
            Triple(r.pet, r.libro, r.resultado)
        }

    /**
     * Igual que [mutate] (mismo [Mutex], misma puesta al día) pero además lee y escribe el libro de
     * sesiones. Mascota y libro se guardan en UNA sola edición de SharedPreferences: no puede quedar
     * una recompensa aplicada con la sesión aún abierta (ni al revés).
     */
    private suspend fun <T> transaccionSesion(
        ownerId: String,
        sinMascota: T,
        bloque: (PetEntity, LibroSesionesJuego, Long) -> Triple<PetEntity, LibroSesionesJuego, T>
    ): T = mutex.withLock {
        val flow = getOrCreatePetFlow(ownerId)
        val actual = flow.value ?: return@withLock sinMascota
        val ahora = System.currentTimeMillis()
        val alDia = actual.applyTick(now = ahora, factorDesgaste = if (AjustesParche.activado(appContext)) 1.6 else 1.0)
        val (pet, libro, resultado) = bloque(alDia, leerLibro(ownerId), ahora)
        flow.value = pet
        guardarPetYLibro(ownerId, pet, libro)
        resultado
    }

    private fun leerLibro(ownerId: String): LibroSesionesJuego {
        val raw = prefs.getString(claveLibro(ownerId), null) ?: return LibroSesionesJuego()
        return runCatching { json.decodeFromString<LibroSesionesJuego>(raw) }.getOrDefault(LibroSesionesJuego())
    }

    private fun guardarPetYLibro(ownerId: String, pet: PetEntity, libro: LibroSesionesJuego) {
        runCatching {
            prefs.edit()
                .putString(clave(ownerId), json.encodeToString(pet))
                .putString(claveLibro(ownerId), json.encodeToString(libro))
                .apply()
        }
    }

    private fun guardarPet(ownerId: String, pet: PetEntity) {
        runCatching {
            prefs.edit().putString(clave(ownerId), json.encodeToString(pet)).apply()
        }
    }

    private fun clave(ownerId: String) = "pet_$ownerId"
    private fun claveLibro(ownerId: String) = "sesiones_juego_$ownerId"

    private companion object {
        const val SCHEMA_ACTUAL = 7
    }
}
