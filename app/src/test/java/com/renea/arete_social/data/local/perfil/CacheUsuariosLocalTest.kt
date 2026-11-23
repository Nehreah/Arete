package com.renea.arete_social.data.local.perfil

import com.renea.arete_social.domain.model.Ubicacion
import com.renea.arete_social.domain.model.Usuario
import com.renea.arete_social.domain.model.Visibilidad
import com.renea.arete_social.testutils.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CacheUsuariosLocalTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var cache: CacheUsuariosLocal

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        cache = CacheUsuariosLocal(prefs)
    }

    private fun crearUsuario(id: String, nombre: String = "Test User") = Usuario(
        id = id,
        nombre = nombre,
        foto = "https://example.com/$id.jpg",
        descripcion = "Bio de $nombre",
        estadoVisibilidad = Visibilidad.VISIBLE,
        ubicacion = Ubicacion(4.6097, -74.0817, 123456789L),
        hobby = "Ajedrez",
        deporte = "Ciclismo",
        ritmoMusical = "Synthwave",
        serieFavorita = "Mr. Robot",
        libroFavorito = "1984",
        signoZodiacal = "Escorpio",
        perfilPrivado = false,
        portada = "https://example.com/portada.jpg"
    )

    @Test
    fun `guardar y obtener usuario persiste todos los campos del perfil`() {
        val original = crearUsuario("usr-1", "Alice")
        cache.guardar(original)

        val recuperado = cache.obtener("usr-1")
        assertNotNull(recuperado)
        assertEquals(original.id, recuperado?.id)
        assertEquals(original.nombre, recuperado?.nombre)
        assertEquals(original.foto, recuperado?.foto)
        assertEquals(original.descripcion, recuperado?.descripcion)
        assertEquals(original.hobby, recuperado?.hobby)
        assertEquals(original.deporte, recuperado?.deporte)
        assertEquals(original.ritmoMusical, recuperado?.ritmoMusical)
        assertEquals(original.serieFavorita, recuperado?.serieFavorita)
        assertEquals(original.libroFavorito, recuperado?.libroFavorito)
        assertEquals(original.signoZodiacal, recuperado?.signoZodiacal)
        assertEquals(original.perfilPrivado, recuperado?.perfilPrivado)
        assertEquals(original.portada, recuperado?.portada)
        assertEquals(original.ubicacion?.latitud, recuperado?.ubicacion?.latitud)
        assertEquals(original.ubicacion?.longitud, recuperado?.ubicacion?.longitud)
    }

    @Test
    fun `obtener usuario no existente devuelve null`() {
        assertNull(cache.obtener("no-existe"))
    }

    @Test
    fun `actualizar usuario sobrescribe la version anterior en persistencia`() {
        val u1 = crearUsuario("usr-1", "Alice Inicial")
        cache.guardar(u1)
        assertEquals("Alice Inicial", cache.obtener("usr-1")?.nombre)

        val u1Modificado = u1.copy(nombre = "Alice Actualizada", descripcion = "Nueva bio")
        cache.guardar(u1Modificado)

        val recuperado = cache.obtener("usr-1")
        assertEquals("Alice Actualizada", recuperado?.nombre)
        assertEquals("Nueva bio", recuperado?.descripcion)
    }

    @Test
    fun `guardar varios usuarios y eliminar uno especifico`() {
        cache.guardar(crearUsuario("usr-1", "Alice"))
        cache.guardar(crearUsuario("usr-2", "Bob"))

        assertEquals("Alice", cache.obtener("usr-1")?.nombre)
        assertEquals("Bob", cache.obtener("usr-2")?.nombre)

        cache.eliminar("usr-1")
        assertNull(cache.obtener("usr-1"))
        assertEquals("Bob", cache.obtener("usr-2")?.nombre)
    }

    @Test
    fun `limpiar borra todos los perfiles cacheados`() {
        cache.guardar(crearUsuario("usr-1", "Alice"))
        cache.guardar(crearUsuario("usr-2", "Bob"))

        cache.limpiar()
        assertNull(cache.obtener("usr-1"))
        assertNull(cache.obtener("usr-2"))
    }
}
