package com.renea.arete_social.domain.model

import com.renea.arete_social.data.local.perfil.aEstadosSnapshot
import com.renea.arete_social.data.local.perfil.aPublicaciones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocoloPerfilBleTest {

    private val ahora = System.currentTimeMillis()

    private fun perfil(
        privado: Boolean = true,
        estados: Int = 3,
        obras: Int = 2,
        largoObra: Int = 500
    ) = SnapshotPerfil(
        usuarioId = "dueno",
        nombre = "Ana",
        descripcion = "Hola",
        privado = privado,
        estados = (1..estados).map { EstadoPerfilSnapshot("e$it", "estado $it", ahora - it) },
        obras = (1..obras).map { ObraPerfilSnapshot("o$it", "CUENTO", "Obra $it", "x".repeat(largoObra), ts = ahora - it) }
    )

    @Test fun `pedir y parsear son inversos`() {
        assertEquals("abc-123", ProtocoloPerfilBle.parsearPedir(ProtocoloPerfilBle.pedir("abc-123")))
    }

    @Test fun `una peticion mal formada se rechaza`() {
        assertNull(ProtocoloPerfilBle.parsearPedir("hola"))
        assertNull(ProtocoloPerfilBle.parsearPedir("[PERFIL_PEDIR:v1:]"))
        assertNull(ProtocoloPerfilBle.parsearPedir("[PERFIL_PEDIR:v1:con espacio]"))
        assertNull(ProtocoloPerfilBle.parsearPedir("[PERFIL_PEDIR:v1:" + "a".repeat(100) + "]"))
    }

    @Test fun `ida y vuelta conserva el contenido`() {
        val decodificado = ProtocoloPerfilBle.decodificarDatos(ProtocoloPerfilBle.codificarDatos(perfil()))
        assertNotNull(decodificado)
        assertEquals("dueno", decodificado!!.usuarioId)
        assertEquals(3, decodificado.estados.size)
        assertEquals(2, decodificado.obras.size)
        assertEquals("Ana", decodificado.nombre)
    }

    @Test fun `un perfil publico tambien se acepta por Bluetooth`() {
        val d = ProtocoloPerfilBle.decodificarDatos(ProtocoloPerfilBle.codificarDatos(perfil(privado = false)))
        assertNotNull(d)
        assertFalse(d!!.privado)
    }

    @Test fun `la respuesta cabe siempre en un paquete mesh`() {
        val enorme = perfil(estados = 50, obras = 50, largoObra = 20_000)
        val texto = ProtocoloPerfilBle.codificarDatos(enorme)
        assertTrue(texto.length <= com.renea.arete_social.domain.mesh.PaqueteMesh.MAX_CONTENIDO_MESH_CHARS)
        val d = ProtocoloPerfilBle.decodificarDatos(texto)!!
        assertTrue(d.estados.size <= ProtocoloPerfilBle.MAX_ESTADOS)
        assertTrue(d.obras.all { it.contenido.length <= ProtocoloPerfilBle.MAX_CONTENIDO_OBRA })
        assertTrue(d.obras.all { it.recortada })
    }

    @Test fun `los estados mas nuevos van primero al recortar`() {
        val d = ProtocoloPerfilBle.decodificarDatos(ProtocoloPerfilBle.codificarDatos(perfil(estados = 30)))!!
        assertEquals("e1", d.estados.first().id)
    }

    @Test fun `datos corruptos o ajenos se descartan`() {
        assertNull(ProtocoloPerfilBle.decodificarDatos("hola"))
        assertNull(ProtocoloPerfilBle.decodificarDatos("[PERFIL_DATOS:v1:{no es json}]"))
        assertNull(ProtocoloPerfilBle.decodificarDatos("[PERFIL_DATOS:v1:" + "a".repeat(ProtocoloPerfilBle.MAX_JSON_CHARS + 1) + "]"))
    }

    @Test fun `version desconocida se descarta`() {
        val texto = ProtocoloPerfilBle.codificarDatos(perfil()).replace("\"v\":1", "\"v\":2")
        assertNull(ProtocoloPerfilBle.decodificarDatos(texto))
    }

    @Test fun `las fotos y los pdf nunca entran en la copia`() {
        val publicaciones = listOf(
            PublicacionPerfil(id = "t", usuarioId = "dueno", tipo = TipoPublicacionPerfil.TEXTO_RETRO, contenido = "hola", timestamp = ahora),
            PublicacionPerfil(id = "f", usuarioId = "dueno", tipo = TipoPublicacionPerfil.FOTO_MATRIX, contenido = "data:image/jpeg;base64,AAAA", timestamp = ahora)
        )
        assertEquals(listOf("t"), publicaciones.aEstadosSnapshot(ahora).map { it.id })

        val obras = listOf(
            ObraLibreria("a", "dueno", TipoObra.CUENTO, "Con texto", "había una vez", null, null, null, ahora),
            ObraLibreria("b", "dueno", TipoObra.POEMARIO, "Solo pdf", "", null, "dueno/b.pdf", "b.pdf", ahora)
        )
        assertEquals(listOf("a"), obras.aObrasSnapshot().map { it.id })
    }

    @Test fun `estados vencidos no se muestran`() {
        val dia = 24L * 60 * 60 * 1000
        val s = SnapshotPerfil(
            usuarioId = "dueno", privado = true,
            estados = listOf(EstadoPerfilSnapshot("viejo", "x", ahora - 8 * dia), EstadoPerfilSnapshot("nuevo", "y", ahora - dia))
        )
        assertEquals(listOf("nuevo"), s.aPublicaciones(ahora).map { it.id })
    }

    @Test fun `una obra recortada avisa que se ve completa con internet`() {
        val s = SnapshotPerfil(usuarioId = "d", privado = true, obras = listOf(ObraPerfilSnapshot("o", "POEMARIO", "T", "abc", recortada = true, ts = ahora)))
        val obra = s.aObras().single()
        assertTrue(obra.contenido.contains("internet"))
        assertFalse(obra.tienePdf)
        assertEquals(TipoObra.POEMARIO, obra.tipo)
    }
}
