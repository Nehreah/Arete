package com.renea.arete_social.feature.personajes

import android.content.SharedPreferences
import com.renea.arete_social.feature.personajes.data.RepositorioPersonajes
import com.renea.arete_social.feature.personajes.logic.GestorEventosMundo
import com.renea.arete_social.feature.personajes.logic.GestorLogrosSociales
import com.renea.arete_social.feature.personajes.model.NivelVinculo
import com.renea.arete_social.feature.personajes.model.Recuerdo
import com.renea.arete_social.feature.personajes.model.Relacion
import com.renea.arete_social.feature.personajes.model.Temperamento
import com.renea.arete_social.feature.personajes.model.ambiente.AccionAmbiente
import com.renea.arete_social.feature.personajes.model.ambiente.TipoAmbiente
import com.renea.arete_social.feature.personajes.model.escena.CatalogoObjetosCasa
import com.renea.arete_social.feature.personajes.model.eventos.TipoEventoMundo
import com.renea.arete_social.feature.personajes.model.logros.TipoLogroSocial
import com.renea.arete_social.feature.personajes.model.TipoRecuerdo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Suite de pruebas unitarias automatizadas JVM puras para la Fase 6: Pulido y Vida del Mundo.
 */
class MundoSocialPulidoTest {

    // ── Fakes ─────────────────────────────────────────────────────────────────

    private class FakeSharedPreferences : SharedPreferences {
        val map = mutableMapOf<String, Any?>()

        override fun getBoolean(key: String, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun getLong(key: String, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getString(key: String, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getInt(key: String, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = defValues
        override fun getAll(): Map<String, Any?> = map
        override fun contains(key: String): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(map)
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    private class FakeEditor(private val map: MutableMap<String, Any?>) : SharedPreferences.Editor {
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor { map[key] = value; return this }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor { map[key] = value; return this }
        override fun putString(key: String, value: String?): SharedPreferences.Editor { map[key] = value; return this }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor { map[key] = value; return this }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor { map[key] = value; return this }
        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor { map[key] = values; return this }
        override fun remove(key: String): SharedPreferences.Editor { map.remove(key); return this }
        override fun clear(): SharedPreferences.Editor { map.clear(); return this }
        override fun commit(): Boolean = true
        override fun apply() {}
    }

    private class FakeRepoPersonajes : RepositorioPersonajes {
        val relaciones = mutableListOf<Relacion>()
        val recuerdos = mutableListOf<Recuerdo>()

        override fun obtenerRelaciones(): List<Relacion> = relaciones.toList()
        override fun obtenerRelacion(otroOwnerId: String): Relacion? = relaciones.find { it.otroOwnerId == otroOwnerId }
        override fun guardarRelacion(relacion: Relacion) {
            relaciones.removeAll { it.otroOwnerId == relacion.otroOwnerId }
            relaciones.add(relacion)
        }
        override fun obtenerRecuerdos(): List<Recuerdo> = recuerdos.toList()
        override fun guardarRecuerdo(recuerdo: Recuerdo) {
            recuerdos.removeAll { it.id == recuerdo.id }
            recuerdos.add(recuerdo)
        }
    }

    private lateinit var repoPersonajes: FakeRepoPersonajes
    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var gestorLogros: GestorLogrosSociales

    @Before
    fun setUp() {
        repoPersonajes = FakeRepoPersonajes()
        fakePrefs = FakeSharedPreferences()
        gestorLogros = GestorLogrosSociales(
            context = null,
            prefsOverride = fakePrefs,
            repoPersonajes = repoPersonajes
        )
    }

    // ── Tests de Eventos Temporales ───────────────────────────────────────────

    @Test
    fun `eventos temporales - calendario determinista activo retorna evento valido`() {
        val evento = GestorEventosMundo.eventoActivo()
        assertNotNull(evento)
        assertTrue(evento.titulo.isNotBlank())
        assertTrue(evento.multiplicadorConfianza >= 1.2f)
        assertNotNull(evento.objetoRecompensaId)
    }

    @Test
    fun `eventos temporales - multiplicador bonifica si coincide temperamento`() {
        val eventoGym = GestorEventosMundo.EVENTOS_CATALOGO.first { it.tipo == TipoEventoMundo.TORNEO_FITNESS_GYM }

        // Temperamento ENERGETICO coincide con el torneo de Gym -> 1.5x
        val multiAfin = GestorEventosMundo.calcularMultiplicador(
            evento = eventoGym,
            temperamento = Temperamento.ENERGETICO,
            accion = AccionAmbiente.ENTRENAR_CINTA
        )
        assertEquals(1.5f, multiAfin, 0.01f)

        // Temperamento TRANQUILO no coincide -> 1.0x (a menos que la acción lo sea)
        val multiNoAfin = GestorEventosMundo.calcularMultiplicador(
            evento = eventoGym,
            temperamento = Temperamento.TRANQUILO,
            accion = AccionAmbiente.DESCANSAR_BANCA
        )
        assertEquals(1.0f, multiNoAfin, 0.01f)
    }

    // ── Tests de Catálogo Estacional ──────────────────────────────────────────

    @Test
    fun `catalogo estacional - objetos de recompensa existen y son configurables`() {
        val trofeoGym = CatalogoObjetosCasa.obtenerPorId("trofeo_torneo_gym")
        assertNotNull("Trofeo de gimnasio debe existir en catálogo", trofeoGym)
        assertEquals("Trofeo de Campeón Fit", trofeoGym?.nombre)

        val farolParque = CatalogoObjetosCasa.obtenerPorId("farol_festival_parque")
        assertNotNull("Farol de primavera debe existir en catálogo", farolParque)

        val tazaCafe = CatalogoObjetosCasa.obtenerPorId("taza_dorada_cafe")
        assertNotNull("Taza de tertulia debe existir en catálogo", tazaCafe)
    }

    // ── Tests de Logros Sociales ──────────────────────────────────────────────

    @Test
    fun `logros sociales - se desbloquean progresivamente segun relaciones y recuerdos`() {
        // Inicialmente sin progreso
        val logrosIniciales = gestorLogros.evaluarLogros()
        assertTrue(logrosIniciales.none { it.desbloqueado })

        // 1. Agregar primera relación -> Desbloquea PRIMER_VINCULO
        repoPersonajes.guardarRelacion(
            Relacion(
                otroPersonajeId = "p-01",
                otroOwnerId = "usr-01",
                nombreOtro = "Michi Amigo",
                confianza = 15
            )
        )
        val logrosConAmigo = gestorLogros.evaluarLogros()
        val logroPrimerAmigo = logrosConAmigo.first { it.tipo == TipoLogroSocial.PRIMER_VINCULO }
        assertTrue("Primer Amigo debe estar desbloqueado", logroPrimerAmigo.desbloqueado)
        assertTrue(fakePrefs.getBoolean("logro_PRIMER_VINCULO", false))

        // 2. Elevar confianza a 95 -> Desbloquea VINCULO_INSEPARABLE
        repoPersonajes.guardarRelacion(
            Relacion(
                otroPersonajeId = "p-01",
                otroOwnerId = "usr-01",
                nombreOtro = "Michi Amigo",
                confianza = 95
            )
        )
        val logrosInseparable = gestorLogros.evaluarLogros()
        val logroInseparable = logrosInseparable.first { it.tipo == TipoLogroSocial.VINCULO_INSEPARABLE }
        assertTrue("Vínculo Inseparable debe estar desbloqueado", logroInseparable.desbloqueado)

        // 3. Agregar 5 recuerdos -> Desbloquea COLECCIONISTA_RECUERDOS
        for (i in 1..5) {
            repoPersonajes.guardarRecuerdo(
                Recuerdo(
                    id = "rec-$i",
                    fechaMillis = System.currentTimeMillis(),
                    tipo = TipoRecuerdo.ACTIVIDAD_PARQUE,
                    conQuienId = "usr-01",
                    conQuienNombre = "Michi Amigo",
                    lugar = "Parque",
                    textoCorto = "Paseo divertido $i"
                )
            )
        }
        val logrosColeccionista = gestorLogros.evaluarLogros()
        val logroColeccionista = logrosColeccionista.first { it.tipo == TipoLogroSocial.COLECCIONISTA_RECUERDOS }
        assertTrue("Coleccionista de recuerdos debe estar desbloqueado", logroColeccionista.desbloqueado)
    }

    // ── Tests de Enriquecimiento de Ficha Social (Cero GPS) ───────────────────

    @Test
    fun `ficha social de amigo en mapa - proporciona datos sin requerir coordenadas GPS`() {
        val amigoId = "usr-amigo-radar"
        val relacion = Relacion(
            otroPersonajeId = "pet-amigo",
            otroOwnerId = amigoId,
            nombreOtro = "Gatito Táctico",
            confianza = 85
        )
        repoPersonajes.guardarRelacion(relacion)

        // Verificamos que la relación esté lista para presentarse en DialogoMimi
        val consultada = repoPersonajes.obtenerRelacion(amigoId)
        assertNotNull(consultada)
        assertEquals("Gatito Táctico", consultada?.nombreOtro)
        assertEquals(NivelVinculo.MEJOR_AMIGO, consultada?.nivelVinculo)
        assertEquals(85, consultada?.confianza)
        // No hay atributos GPS en la relación; la privacidad de ubicación física está garantizada.
    }
}
