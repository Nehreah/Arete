package com.renea.arete_social.feature.personajes

import com.renea.arete_social.feature.personajes.data.RepositorioCasa
import com.renea.arete_social.feature.personajes.data.RepositorioPersonajes
import com.renea.arete_social.feature.personajes.data.RepositorioPersonajesRemoto
import com.renea.arete_social.feature.personajes.logic.ResultadoSincronizacion
import com.renea.arete_social.feature.personajes.logic.SincronizadorPersonajes
import com.renea.arete_social.feature.personajes.model.Recuerdo
import com.renea.arete_social.feature.personajes.model.Relacion
import com.renea.arete_social.feature.personajes.model.TipoRecuerdo
import com.renea.arete_social.feature.personajes.model.escena.CasaSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

// ══════════════════════════════════════════════════════════════════════════════
// SincronizacionPersonajesRemotoTest
// Tests JVM puros para SincronizadorPersonajes usando fakes inyectados.
// No requieren Android SDK ni singleton AjustesPersonajesSociales.
// El feature flag se inyecta como lambda booleana.
// ══════════════════════════════════════════════════════════════════════════════

class SincronizacionPersonajesRemotoTest {

    // ── Fakes ─────────────────────────────────────────────────────────────────

    private class FakeRepoPersonajes : RepositorioPersonajes {
        val relaciones = mutableListOf<Relacion>()
        val recuerdos  = mutableListOf<Recuerdo>()

        override fun obtenerRelaciones() = relaciones.toList()
        override fun obtenerRelacion(otroOwnerId: String) = relaciones.find { it.otroOwnerId == otroOwnerId }
        override fun guardarRelacion(relacion: Relacion) {
            relaciones.removeAll { it.otroOwnerId == relacion.otroOwnerId }
            relaciones.add(relacion)
        }
        override fun obtenerRecuerdos() = recuerdos.sortedByDescending { it.fechaMillis }
        override fun guardarRecuerdo(recuerdo: Recuerdo) {
            recuerdos.removeAll { it.id == recuerdo.id }
            recuerdos.add(recuerdo)
        }
    }

    private class FakeRepoCasa(private val snapshotOverride: CasaSnapshot? = null) : RepositorioCasa {
        override fun obtenerCasa(propietarioId: String, personajeId: String): CasaSnapshot =
            snapshotOverride ?: CasaSnapshot(propietarioId = propietarioId, personajeId = personajeId)
        override fun guardarCasa(casa: CasaSnapshot) {}
        override fun casaPorDefecto(propietarioId: String, personajeId: String) =
            CasaSnapshot(propietarioId = propietarioId, personajeId = personajeId)
    }

    private class FakeRepoRemoto : RepositorioPersonajesRemoto {
        val relacionesEnviadas  = mutableListOf<Relacion>()
        val recuerdosEnviados   = mutableListOf<Recuerdo>()
        var casaEnviada: CasaSnapshot? = null
        var casasDeAmigos: Map<String, CasaSnapshot> = emptyMap()
        var relacionesRemotas: List<Relacion> = emptyList()
        var recuerdosRemotos:  List<Recuerdo> = emptyList()
        var fallarSiempre: Boolean = false

        override suspend fun sincronizarRelacion(r: Relacion): Result<Unit> {
            if (fallarSiempre) return Result.failure(RuntimeException("sin red"))
            relacionesEnviadas.add(r); return Result.success(Unit)
        }
        override suspend fun sincronizarRecuerdo(r: Recuerdo): Result<Unit> {
            if (fallarSiempre) return Result.failure(RuntimeException("sin red"))
            recuerdosEnviados.add(r); return Result.success(Unit)
        }
        override suspend fun sincronizarCasaSnapshot(s: CasaSnapshot): Result<Unit> {
            if (fallarSiempre) return Result.failure(RuntimeException("sin red"))
            casaEnviada = s; return Result.success(Unit)
        }
        override suspend fun obtenerCasaDeAmigo(amigoOwnerId: String) =
            Result.success(casasDeAmigos[amigoOwnerId])
        override suspend fun obtenerRelaciones(): Result<List<Relacion>> {
            if (fallarSiempre) return Result.failure(RuntimeException("sin red"))
            return Result.success(relacionesRemotas)
        }
        override suspend fun obtenerRecuerdos(): Result<List<Recuerdo>> {
            if (fallarSiempre) return Result.failure(RuntimeException("sin red"))
            return Result.success(recuerdosRemotos)
        }
    }

    // ── Estado compartido entre tests ─────────────────────────────────────────

    private lateinit var repoPersonajes: FakeRepoPersonajes
    private lateinit var repoRemoto: FakeRepoRemoto
    private val propietarioId = "user-test-01"
    private val personajeId   = "personaje-test-01"

    @Before
    fun setUp() {
        repoPersonajes = FakeRepoPersonajes()
        repoRemoto     = FakeRepoRemoto()
    }

    private fun sincronizador(
        habilitado: Boolean = true,
        snapshot: CasaSnapshot? = null
    ) = SincronizadorPersonajes(
        esHabilitado  = { habilitado },   // inyección directa del lambda
        repoLocal     = repoPersonajes,
        repoCasa      = FakeRepoCasa(snapshot),
        repoRemoto    = repoRemoto,
        propietarioId = propietarioId,
        personajeId   = personajeId
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `feature flag apagado - sincronizarTodo devuelve resultado vacio`() = runTest {
        repoPersonajes.relaciones.add(relacionDePrueba())

        val resultado = sincronizador(habilitado = false).sincronizarTodo()

        assertEquals(0, resultado.relacionesEnviadas)
        assertEquals(0, resultado.recuerdosEnviados)
        assertFalse(resultado.casaEnviada)
        assertTrue(repoRemoto.relacionesEnviadas.isEmpty())
    }

    @Test
    fun `feature flag apagado - restaurarDesdeRemoto devuelve resultado vacio`() = runTest {
        repoRemoto.relacionesRemotas = listOf(relacionDePrueba("otro-owner"))

        val resultado = sincronizador(habilitado = false).restaurarDesdeRemoto()

        assertEquals(0, resultado.relacionesRestauradas)
        assertEquals(0, resultado.recuerdosRestaurados)
        assertTrue(repoPersonajes.relaciones.isEmpty())
    }

    @Test
    fun `sincronizarTodo envia todas las relaciones`() = runTest {
        repoPersonajes.relaciones.add(relacionDePrueba("owner-a"))
        repoPersonajes.relaciones.add(relacionDePrueba("owner-b"))

        val resultado = sincronizador().sincronizarTodo()

        assertEquals(2, resultado.relacionesEnviadas)
        assertEquals(2, repoRemoto.relacionesEnviadas.size)
        assertTrue(resultado.errores.isEmpty())
    }

    @Test
    fun `sincronizarTodo - solo envia recuerdos con firma mutua`() = runTest {
        val firmado   = recuerdoDePrueba(firmaMutua = "FIRMA_VALIDA_BASE64")
        val sinFirma  = recuerdoDePrueba(firmaMutua = null)
        val firmaVacia = recuerdoDePrueba(firmaMutua = "")
        repoPersonajes.recuerdos.addAll(listOf(firmado, sinFirma, firmaVacia))

        val resultado = sincronizador().sincronizarTodo()

        assertEquals(1, resultado.recuerdosEnviados)
        assertEquals(firmado.id, repoRemoto.recuerdosEnviados.first().id)
    }

    @Test
    fun `sincronizarTodo - snapshot firmada se envia correctamente`() = runTest {
        val snapshot = CasaSnapshot(
            propietarioId    = propietarioId,
            personajeId      = personajeId,
            firmaPropietario = "FIRMA_ECDSA_BASE64"
        )

        val resultado = sincronizador(snapshot = snapshot).sincronizarTodo()

        assertTrue(resultado.casaEnviada)
        assertEquals(snapshot.propietarioId, repoRemoto.casaEnviada?.propietarioId)
    }

    @Test
    fun `sincronizarTodo - snapshot sin firma no se envia`() = runTest {
        val snapshot = CasaSnapshot(
            propietarioId    = propietarioId,
            personajeId      = personajeId,
            firmaPropietario = null
        )

        val resultado = sincronizador(snapshot = snapshot).sincronizarTodo()

        assertFalse(resultado.casaEnviada)
        assertEquals(null, repoRemoto.casaEnviada)
    }

    @Test
    fun `error de red - no corrompe estado local y registra error`() = runTest {
        repoRemoto.fallarSiempre = true
        repoPersonajes.relaciones.add(relacionDePrueba("owner-a"))

        val resultado = sincronizador().sincronizarTodo()

        assertEquals(0, resultado.relacionesEnviadas)
        assertTrue(resultado.errores.any { it.startsWith("relacion:") })
        // Local intacto
        assertEquals(1, repoPersonajes.relaciones.size)
    }

    @Test
    fun `restaurarDesdeRemoto - solo agrega relaciones no presentes localmente`() = runTest {
        val local   = relacionDePrueba("owner-local")
        val remota1 = relacionDePrueba("owner-local")  // duplicado → no se agrega
        val remota2 = relacionDePrueba("owner-nuevo")  // nueva     → se agrega

        repoPersonajes.relaciones.add(local)
        repoRemoto.relacionesRemotas = listOf(remota1, remota2)

        val resultado = sincronizador().restaurarDesdeRemoto()

        assertEquals(1, resultado.relacionesRestauradas)
        assertEquals(2, repoPersonajes.relaciones.size)
        assertTrue(repoPersonajes.relaciones.any { it.otroOwnerId == "owner-local" })
        assertTrue(repoPersonajes.relaciones.any { it.otroOwnerId == "owner-nuevo" })
    }

    @Test
    fun `restaurarDesdeRemoto - no duplica recuerdos existentes localmente`() = runTest {
        val idExistente = UUID.randomUUID().toString()
        val localExistente  = recuerdoDePrueba(id = idExistente)
        val remotoExistente = recuerdoDePrueba(id = idExistente)
        val remotoNuevo     = recuerdoDePrueba()

        repoPersonajes.recuerdos.add(localExistente)
        repoRemoto.recuerdosRemotos = listOf(remotoExistente, remotoNuevo)

        val resultado = sincronizador().restaurarDesdeRemoto()

        assertEquals(1, resultado.recuerdosRestaurados)
        assertEquals(2, repoPersonajes.recuerdos.size)
    }

    @Test
    fun `restaurarDesdeRemoto - error de red registra en errores sin crashear`() = runTest {
        repoRemoto.fallarSiempre = true
        repoRemoto.relacionesRemotas = listOf(relacionDePrueba())

        val resultado = sincronizador().restaurarDesdeRemoto()

        assertTrue(resultado.errores.any { it.startsWith("restaurar_relaciones:") })
        assertTrue(repoPersonajes.relaciones.isEmpty())
    }

    @Test
    fun `obtenerCasaDeAmigo - feature flag apagado devuelve null`() = runTest {
        repoRemoto.casasDeAmigos = mapOf("owner-amigo" to CasaSnapshot(
            propietarioId = "owner-amigo", personajeId = "pet-amigo"
        ))

        val casa = sincronizador(habilitado = false).obtenerCasaDeAmigo("owner-amigo")

        assertEquals(null, casa)
    }

    @Test
    fun `obtenerCasaDeAmigo - feature flag activo devuelve la casa del amigo`() = runTest {
        val casaAmigo = CasaSnapshot(propietarioId = "owner-amigo", personajeId = "pet-amigo")
        repoRemoto.casasDeAmigos = mapOf("owner-amigo" to casaAmigo)

        val casa = sincronizador(habilitado = true).obtenerCasaDeAmigo("owner-amigo")

        assertEquals(casaAmigo, casa)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun relacionDePrueba(otroOwnerId: String = "owner-${UUID.randomUUID()}") = Relacion(
        otroPersonajeId = "personaje-01",
        otroOwnerId     = otroOwnerId,
        nombreOtro      = "Michi Amigo",
        confianza       = 25
    )

    private fun recuerdoDePrueba(
        id: String = UUID.randomUUID().toString(),
        firmaMutua: String? = "FIRMA_BASE64"
    ) = Recuerdo(
        id             = id,
        fechaMillis    = System.currentTimeMillis(),
        tipo           = TipoRecuerdo.VISITA_CASA,
        conQuienId     = "owner-${UUID.randomUUID()}",
        conQuienNombre = "Michi",
        lugar          = "Casa de Michi",
        textoCorto     = "Una visita genial",
        firmaMutua     = firmaMutua
    )
}
