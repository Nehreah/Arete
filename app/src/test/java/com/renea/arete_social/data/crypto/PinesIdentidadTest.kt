package com.renea.arete_social.data.crypto

import com.renea.arete_social.testutils.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PinesIdentidadTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var pines: PinesIdentidad

    @Before
    fun setup() {
        fakePrefs = FakeSharedPreferences()
        pines = PinesIdentidad(context = null, prefsOverride = fakePrefs)
    }

    @Test
    fun fijarPinVerificado_noPuedeSerSobreescritoPorClaveNoVerificada() {
        val usuarioId = "contacto-01"
        val claveVerificada = "CLAVE_PUBLICA_ORIGINAL_VERIFICADA"

        // 1. Fijar como VERIFICADO (ej. verificado en persona vía QR o cruce físico)
        pines.fijar(usuarioId, claveVerificada, EstadoVerificacionIdentidad.VERIFICADO)
        assertEquals(EstadoVerificacionIdentidad.VERIFICADO, pines.obtenerEstado(usuarioId))
        assertEquals(claveVerificada, pines.obtener(usuarioId))

        // 2. Intentar degradar o cambiar clave con paquete no autenticado
        val claveFalsa = "CLAVE_MALICIOSA_ATACANTE"
        pines.fijar(usuarioId, claveFalsa, EstadoVerificacionIdentidad.NO_VERIFICADO)

        // El pin original VERIFICADO debe permanecer intacto
        assertEquals(EstadoVerificacionIdentidad.VERIFICADO, pines.obtenerEstado(usuarioId))
        assertEquals(claveVerificada, pines.obtener(usuarioId))
    }

    @Test
    fun pinNoVerificado_anteClaveDiscordante_emiteAlertaConflicto() {
        val usuarioId = "contacto-02"
        val clave1 = "CLAVE_INICIAL_PROVISIONAL"

        pines.fijar(usuarioId, clave1, EstadoVerificacionIdentidad.NO_VERIFICADO)
        assertEquals(EstadoVerificacionIdentidad.NO_VERIFICADO, pines.obtenerEstado(usuarioId))

        // Llega una clave distinta para el mismo usuario
        val clave2 = "CLAVE_DISCORDANTE"
        pines.fijar(usuarioId, clave2, EstadoVerificacionIdentidad.NO_VERIFICADO)

        // Se debe levantar alerta de conflicto sin destruir ciegamente el estado
        assertEquals(EstadoVerificacionIdentidad.ALERTA_CONFLICTO, pines.obtenerEstado(usuarioId))
    }
}
