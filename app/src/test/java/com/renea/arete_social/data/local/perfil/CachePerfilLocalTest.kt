package com.renea.arete_social.data.local.perfil

import com.renea.arete_social.domain.model.EstadoPerfilSnapshot
import com.renea.arete_social.domain.model.ObraLibreria
import com.renea.arete_social.domain.model.PerfilIntereses
import com.renea.arete_social.domain.model.PublicacionPerfil
import com.renea.arete_social.domain.model.SeccionGustosDatos
import com.renea.arete_social.domain.model.SnapshotPerfil
import com.renea.arete_social.domain.model.TipoObra
import com.renea.arete_social.domain.model.TipoPublicacionPerfil
import com.renea.arete_social.domain.model.TipoSeccionPerfil
import com.renea.arete_social.testutils.FakeSharedPreferences
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CachePerfilLocalTest {

    private var ahora = 1_000_000_000_000L
    private val prefs = FakeSharedPreferences()
    private fun cache() = CachePerfilLocal(prefs, reloj = { ahora })

    private fun recibido(id: String) = SnapshotPerfil(
        usuarioId = id, nombre = id, privado = true,
        estados = listOf(EstadoPerfilSnapshot("e", "hola", ahora))
    )

    @Test fun `mi perfil nuevo es privado por defecto hasta conocer su privacidad`() {
        val c = cache()
        c.actualizarMisEstados("yo", emptyList())
        assertTrue(c.miSnapshot("yo")!!.privado)
    }

    @Test fun `mi snapshot junta perfil estados y obras`() {
        val c = cache()
        c.actualizarMiPerfil("yo", "Ana", "desc", privado = true)
        c.actualizarMisEstados(
            "yo",
            listOf(
                PublicacionPerfil(id = "t", usuarioId = "yo", tipo = TipoPublicacionPerfil.TEXTO_RETRO, contenido = "hola", timestamp = ahora),
                PublicacionPerfil(id = "f", usuarioId = "yo", tipo = TipoPublicacionPerfil.FOTO_MATRIX, contenido = "data:...", timestamp = ahora)
            )
        )
        c.actualizarMisObras("yo", listOf(ObraLibreria("o", "yo", TipoObra.CUENTO, "T", "texto", null, null, null, ahora)))
        val s = c.miSnapshot("yo")!!
        assertTrue(s.privado)
        assertEquals("Ana", s.nombre)
        assertEquals(listOf("t"), s.estados.map { it.id })
        assertEquals(listOf("o"), s.obras.map { it.id })
    }

    @Test fun `cambiar la privacidad se refleja de inmediato`() {
        val c = cache()
        c.actualizarMiPerfil("yo", "Ana", "", privado = true)
        c.actualizarMiPerfil("yo", "Ana", "", privado = false)
        assertFalse(c.miSnapshot("yo")!!.privado)
    }

    @Test fun `lo recibido se guarda con la hora local y sobrevive a reiniciar`() {
        cache().guardarRecibido(recibido("otra").copy(actualizadoEn = 5L))
        val reabierta = cache()
        assertEquals(ahora, reabierta.recibido("otra")!!.actualizadoEn)
    }

    @Test fun `lo recibido vence a los siete dias`() {
        val c = cache()
        c.guardarRecibido(recibido("otra"))
        ahora += CachePerfilLocal.VIGENCIA_RECIBIDO_MS + 1
        assertNull(c.recibido("otra"))
        assertNull(cache().recibido("otra"))
    }

    @Test fun `se conservan como maximo los mas recientes`() {
        val c = cache()
        for (i in 1..(CachePerfilLocal.MAX_RECIBIDOS + 5)) {
            ahora += 1
            c.guardarRecibido(recibido("p$i"))
        }
        assertNull(c.recibido("p1"))
        assertNotNull(c.recibido("p${CachePerfilLocal.MAX_RECIBIDOS + 5}"))
    }

    // ── Fase 4.1: Sección GUSTOS ───────────────────────────────────────────────

    @Test fun `guardarSeccionRecibida GUSTOS persiste los intereses en el snapshot`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val c = cache()
        c.guardarRecibido(recibido("otro").copy(privado = false))
        val gustosJson = json.encodeToString(
            SeccionGustosDatos.serializer(),
            SeccionGustosDatos(intereses = listOf("libros", "tecnologia"), categorias = listOf("libros"))
        )
        c.guardarSeccionRecibida("otro", TipoSeccionPerfil.GUSTOS, gustosJson)
        val s = c.recibido("otro")!!
        assertNotNull(s.intereses)
        assertTrue(s.intereses!!.contains("libros"))
        assertTrue(s.intereses!!.contains("tecnologia"))
    }

    @Test fun `obtenerDatosSeccion GUSTOS devuelve null si no hay proveedor`() {
        val c = cache() // sin interesesProveedor
        c.actualizarMiPerfil("yo", "Ana", "desc", privado = false)
        assertNull(c.obtenerDatosSeccion("yo", TipoSeccionPerfil.GUSTOS))
    }

    @Test fun `obtenerDatosSeccion GUSTOS usa el proveedor cuando esta disponible`() {
        val intereses = PerfilIntereses(
            categoriasSeleccionadas = setOf("libros"),
            gustosPersonalizados = listOf("poesia", "manga")
        )
        val c = CachePerfilLocal(prefs, { ahora }, interesesProveedor = { intereses })
        c.actualizarMiPerfil("yo", "Ana", "desc", privado = false)
        val jsonGustos = c.obtenerDatosSeccion("yo", TipoSeccionPerfil.GUSTOS)
        assertNotNull(jsonGustos)
        assertTrue(jsonGustos!!.contains("libros"))
        assertTrue(jsonGustos.contains("poesia"))
    }

    @Test fun `manifiesto amigo incluye seccion GUSTOS cuando hay intereses`() {
        val intereses = PerfilIntereses(categoriasSeleccionadas = setOf("arte"))
        val c = CachePerfilLocal(prefs, { ahora }, interesesProveedor = { intereses })
        c.actualizarMiPerfil("yo", "Ana", "desc artística", privado = false)
        val manifiesto = c.miManifiesto("yo", esAmigo = true)!!
        assertTrue(manifiesto.secciones.containsKey(TipoSeccionPerfil.GUSTOS.name))
    }

    @Test fun `manifiesto desconocido no incluye secciones`() {
        val intereses = PerfilIntereses(categoriasSeleccionadas = setOf("arte"))
        val c = CachePerfilLocal(prefs, { ahora }, interesesProveedor = { intereses })
        c.actualizarMiPerfil("yo", "Ana", "desc", privado = true)
        val manifiesto = c.miManifiesto("yo", esAmigo = false)!!
        assertTrue(manifiesto.secciones.isEmpty())
        assertTrue(manifiesto.esPrivado)
    }

    @Test fun `snapshot recibido preserva intereses previos al actualizar manifiesto`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val c = cache()
        // 1. Guardar snap con intereses
        c.guardarRecibido(recibido("otro").copy(privado = false, intereses = listOf("deporte")))
        // 2. Llega un manifiesto nuevo (sin sección GUSTOS en esta ronda)
        c.guardarManifiestoRecibido(
            com.renea.arete_social.domain.model.ManifiestoPerfil(
                usuarioId = "otro", nombre = "Otro", esPrivado = false
            )
        )
        // Los intereses previos se preservan (guardarManifiestoRecibido no los borra)
        val s = c.recibido("otro")!!
        assertNotNull(s.intereses)
    }
}
