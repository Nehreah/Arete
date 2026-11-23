package com.renea.arete_social.domain.crypto

import com.renea.arete_social.data.crypto.CifradorE2eeAndroid
import com.renea.arete_social.testutils.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pruebas unitarias de Cifrado Extremo a Extremo (E2EE) con ECDH + AES-256-GCM + HKDF-SHA256.
 *
 * Versión v3 del protocolo:
 * - HKDF con salt = SHA-256(ordered public keys) e info = "antired-e2ee-v3".
 * - AAD = "remitenteId|destinatarioId" vincula criptográficamente los IDs al ciphertext.
 * - Solo acepta prefijo ENC:v3: — v1 y v2 rechazados.
 *
 * Ejecuta sobre JVM pura utilizando [FakeSharedPreferences] y [java.util.Base64].
 */
class CifradorE2eeTest {

    private lateinit var prefsAlice: FakeSharedPreferences
    private lateinit var prefsBob: FakeSharedPreferences
    private lateinit var prefsEve: FakeSharedPreferences

    private lateinit var cifradorAlice: CifradorE2ee
    private lateinit var cifradorBob: CifradorE2ee
    private lateinit var cifradorEve: CifradorE2ee

    private val idAlice = "alice-uuid-001"
    private val idBob   = "bob-uuid-002"
    private val idEve   = "eve-uuid-003"

    @Before
    fun setUp() {
        prefsAlice = FakeSharedPreferences()
        prefsBob   = FakeSharedPreferences()
        prefsEve   = FakeSharedPreferences()

        cifradorAlice = CifradorE2eeAndroid(prefsOverride = prefsAlice)
        cifradorBob   = CifradorE2eeAndroid(prefsOverride = prefsBob)
        cifradorEve   = CifradorE2eeAndroid(prefsOverride = prefsEve)
    }

    // ── Generación de claves ────────────────────────────────────────────────

    @Test
    fun `obtenerClavePublicaPropia genera clave valida y es idempotente`() {
        val pub1 = cifradorAlice.obtenerClavePublicaPropia(idAlice)
        assertNotNull(pub1)
        assertTrue(pub1.isNotBlank())

        val pub2 = cifradorAlice.obtenerClavePublicaPropia(idAlice)
        assertEquals("Llamadas subsecuentes deben devolver la misma clave pública persistida", pub1, pub2)
    }

    @Test
    fun `claves de dos usuarios diferentes son distintas`() {
        val pubAlice = cifradorAlice.obtenerClavePublicaPropia(idAlice)
        val pubBob   = cifradorBob.obtenerClavePublicaPropia(idBob)

        assertNotEquals(pubAlice, pubBob)
    }

    // ── esCifrado — v3 válido, v1/v2 rechazados ────────────────────────────

    @Test
    fun `esCifrado acepta prefijos v3 y v4 con formato base64 correcto`() {
        // v3 válido: IV:ciphertext (2 segmentos tras "ENC:v3:")
        assertTrue(cifradorAlice.esCifrado("ENC:v3:dGVzdElWMTI=:dGVzdENpcGhlclRleHQ="))

        // v4 válido: chatId:IV:ciphertext (3 segmentos tras "ENC:v4:")
        assertTrue(cifradorAlice.esCifrado("ENC:v4:chat123:dGVzdElWMTI=:dGVzdENpcGhlclRleHQ="))

        // v1 y v2 ya no son formatos válidos en esta versión del protocolo
        assertFalse("v1 debe ser rechazado", cifradorAlice.esCifrado("ENC:v1:dGVzdElWMTI=:dGVzdENpcGhlclRleHQ="))
        assertFalse("v2 debe ser rechazado", cifradorAlice.esCifrado("ENC:v2:dGVzdElWMTI=:dGVzdENpcGhlclRleHQ="))

        // Texto plano normal
        assertFalse(cifradorAlice.esCifrado("Hola mundo"))
        // Prefijo sin partes
        assertFalse(cifradorAlice.esCifrado("ENC:v3:"))
        assertFalse(cifradorAlice.esCifrado("ENC:v4:"))
        // Solo una parte
        assertFalse(cifradorAlice.esCifrado("ENC:v3:soloUnaParte"))
        assertFalse(cifradorAlice.esCifrado("ENC:v4:soloUnaParte"))
        // Caracteres inválidos en Base64
        assertFalse(cifradorAlice.esCifrado("ENC:v3:iv Con Espacio:cipher!@#"))
        assertFalse(cifradorAlice.esCifrado("ENC:v4:chat:iv Con Espacio:cipher!@#"))
    }

    // ── Ida y vuelta con AAD ────────────────────────────────────────────────

    @Test
    fun `cifrado v3 y descifrado ida y vuelta recupera el texto plano original`() {
        val pubBob   = cifradorBob.obtenerClavePublicaPropia(idBob)
        val pubAlice = cifradorAlice.obtenerClavePublicaPropia(idAlice)

        val mensajeOriginal = "¡Hola Bob! Mensaje ultra secreto transmitido por BLE Mesh."

        // Alice cifra para Bob, con AAD que incluye ambos IDs
        val mensajeCifrado = cifradorAlice.cifrar(
            textoPlano = mensajeOriginal,
            clavePublicaDestinatarioBase64 = pubBob,
            miUsuarioId = idAlice,
            destinatarioId = idBob
        )

        assertTrue("El payload debe estar marcado como cifrado", cifradorAlice.esCifrado(mensajeCifrado))
        assertTrue("Debe usar prefijo v3 con HKDF+AAD", mensajeCifrado.startsWith("ENC:v3:"))
        assertNotEquals(mensajeOriginal, mensajeCifrado)

        // Bob descifra el mensaje de Alice, pasando el remitenteOriginalId para verificar AAD
        val mensajeDescifrado = cifradorBob.descifrar(
            textoCifrado = mensajeCifrado,
            clavePublicaContraparteBase64 = pubAlice,
            miUsuarioId = idBob,
            remitenteOriginalId = idAlice
        )

        assertEquals(
            "El texto descifrado debe coincidir exactamente con el original",
            mensajeOriginal,
            mensajeDescifrado
        )
    }

    // ── AAD mismatch ────────────────────────────────────────────────────────

    @Test
    fun `descifrado con AAD incorrecto falla por autenticacion GCM`() {
        val pubBob   = cifradorBob.obtenerClavePublicaPropia(idBob)
        val pubAlice = cifradorAlice.obtenerClavePublicaPropia(idAlice)

        val mensajeOriginal = "Datos sensibles con AAD"

        // Alice cifra para Bob con remitenteId = idAlice
        val mensajeCifrado = cifradorAlice.cifrar(
            textoPlano = mensajeOriginal,
            clavePublicaDestinatarioBase64 = pubBob,
            miUsuarioId = idAlice,
            destinatarioId = idBob
        )

        // Bob intenta descifrar pero indica un remitenteId equivocado (ataque de suplantación)
        val resultadoAadIncorrecto = cifradorBob.descifrar(
            textoCifrado = mensajeCifrado,
            clavePublicaContraparteBase64 = pubAlice,
            miUsuarioId = idBob,
            remitenteOriginalId = idEve // AAD diferente → GCM tag falla
        )

        assertNotEquals(
            "AAD incorrecto debe causar fallo de autenticación GCM y NO devolver el plaintext",
            mensajeOriginal,
            resultadoAadIncorrecto
        )
    }

    @Test
    fun `cifrado v4 y descifrado ida y vuelta con chatId contextual recupera el texto plano original`() {
        val pubBob   = cifradorBob.obtenerClavePublicaPropia(idBob)
        val pubAlice = cifradorAlice.obtenerClavePublicaPropia(idAlice)
        val testChatId = "chat-dm-alice-bob-999"

        val mensajeOriginal = "Mensaje secreto vinculado al chatId específico."

        val mensajeCifrado = cifradorAlice.cifrar(
            textoPlano = mensajeOriginal,
            clavePublicaDestinatarioBase64 = pubBob,
            miUsuarioId = idAlice,
            destinatarioId = idBob,
            chatId = testChatId
        )

        assertTrue(cifradorAlice.esCifrado(mensajeCifrado))
        assertTrue("Debe usar prefijo v4 con chatId en payload", mensajeCifrado.startsWith("ENC:v4:$testChatId:"))

        val mensajeDescifrado = cifradorBob.descifrar(
            textoCifrado = mensajeCifrado,
            clavePublicaContraparteBase64 = pubAlice,
            miUsuarioId = idBob,
            remitenteOriginalId = idAlice
        )

        assertEquals(mensajeOriginal, mensajeDescifrado)
    }

    @Test
    fun `descifrado v4 transferido a otro chat falla por AAD mismatch`() {
        val pubBob   = cifradorBob.obtenerClavePublicaPropia(idBob)
        val pubAlice = cifradorAlice.obtenerClavePublicaPropia(idAlice)

        val mensajeOriginal = "Texto que no debe ser reutilizable en otro chat"

        val mensajeCifrado = cifradorAlice.cifrar(
            textoPlano = mensajeOriginal,
            clavePublicaDestinatarioBase64 = pubBob,
            miUsuarioId = idAlice,
            destinatarioId = idBob,
            chatId = "chat-original"
        )

        // Simular que el mensaje es inyectado o copiado en otro chat reemplazando el chatId en el payload
        val partes = mensajeCifrado.split(":")
        // partes = ["ENC", "v4", "chat-original", iv, ciphertext]
        val payloadReubicado = "ENC:v4:chat-falso-reubicado:${partes[3]}:${partes[4]}"

        val resultado = cifradorBob.descifrar(
            textoCifrado = payloadReubicado,
            clavePublicaContraparteBase64 = pubAlice,
            miUsuarioId = idBob,
            remitenteOriginalId = idAlice
        )

        assertNotEquals("Cambiar el chatId del payload debe hacer fallar el tag GCM", mensajeOriginal, resultado)
    }

    // ── Tercero no autorizado ───────────────────────────────────────────────

    @Test
    fun `tercero no autorizado Eve no puede descifrar el mensaje`() {
        val pubBob   = cifradorBob.obtenerClavePublicaPropia(idBob)
        val pubAlice = cifradorAlice.obtenerClavePublicaPropia(idAlice)

        val mensajeOriginal = "Coordenadas del punto de encuentro: 4.6097, -74.0817"
        val mensajeCifrado = cifradorAlice.cifrar(
            textoPlano = mensajeOriginal,
            clavePublicaDestinatarioBase64 = pubBob,
            miUsuarioId = idAlice,
            destinatarioId = idBob
        )

        // Eve intenta descifrar con su propia clave privada
        val descifradoPorEve = cifradorEve.descifrar(
            textoCifrado = mensajeCifrado,
            clavePublicaContraparteBase64 = pubAlice,
            miUsuarioId = idEve,
            remitenteOriginalId = idAlice
        )

        assertNotEquals("Eve no debe poder descifrar el mensaje destinado a Bob", mensajeOriginal, descifradoPorEve)
        assertEquals("[🔒 Mensaje cifrado E2EE]", descifradoPorEve)
    }

    // ── Integridad AES-GCM ──────────────────────────────────────────────────

    @Test
    fun `mensaje con texto cifrado alterado no descifra por fallo de integridad AES-GCM`() {
        val pubBob   = cifradorBob.obtenerClavePublicaPropia(idBob)
        val pubAlice = cifradorAlice.obtenerClavePublicaPropia(idAlice)

        val mensajeOriginal = "Transferencia de 100 créditos"
        val mensajeCifrado = cifradorAlice.cifrar(
            textoPlano = mensajeOriginal,
            clavePublicaDestinatarioBase64 = pubBob,
            miUsuarioId = idAlice,
            destinatarioId = idBob
        )

        // Alteramos un byte del ciphertext (ENC:v3:IV:CIPHERTEXT)
        val partes = mensajeCifrado.split(":")
        val payloadAlterado = partes[0] + ":" + partes[1] + ":" + partes[2] + ":" + partes[3] + "AA"

        val resultado = cifradorBob.descifrar(
            textoCifrado = payloadAlterado,
            clavePublicaContraparteBase64 = pubAlice,
            miUsuarioId = idBob,
            remitenteOriginalId = idAlice
        )
        assertNotEquals(mensajeOriginal, resultado)
    }

    // ── Persistencia de claves conocidas ───────────────────────────────────

    @Test
    fun `guardar y obtener clave publica conocida persiste en almacenamiento`() {
        val claveConocida = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEtest123=="
        cifradorAlice.guardarClavePublicaConocida("peer-usuario-99", claveConocida, origen = "verificado")

        val recuperada = cifradorAlice.obtenerClavePublicaConocida("peer-usuario-99")
        assertEquals(claveConocida, recuperada)
    }

    @Test
    fun `no-overwrite - clave verificada no se reemplaza por clave mesh`() {
        val claveVerificada = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEverificada=="
        val claveMesh       = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEmesh=="

        cifradorAlice.guardarClavePublicaConocida("peer-99", claveVerificada, origen = "verificado")
        // Intento de sobreescritura por clave llegada via mesh (debe ser ignorado)
        cifradorAlice.guardarClavePublicaConocida("peer-99", claveMesh, origen = "mesh")

        val recuperada = cifradorAlice.obtenerClavePublicaConocida("peer-99")
        assertEquals("La clave verificada no debe ser sobreescrita por una de mesh", claveVerificada, recuperada)
    }

    // ── H-09: Validación de chatId en v4 y manejo de formato corrupto ───────

    @Test
    fun `descifrado v4 con chatId diferente es rechazado`() {
        val pubBob   = cifradorBob.obtenerClavePublicaPropia(idBob)
        val pubAlice = cifradorAlice.obtenerClavePublicaPropia(idAlice)

        val mensajeOriginal = "Mensaje confidencial de sala"
        val chatIdReal = "chat-1234"
        val chatIdFalso = "chat-9999"

        val cifrado = cifradorAlice.cifrar(
            textoPlano = mensajeOriginal,
            clavePublicaDestinatarioBase64 = pubBob,
            miUsuarioId = idAlice,
            destinatarioId = idBob,
            chatId = chatIdReal
        )

        assertTrue(cifrado.startsWith("ENC:v4:"))

        // Intento de descifrar indicando un chatId distinto al del AAD
        val resultadoFalso = cifradorBob.descifrar(
            textoCifrado = cifrado,
            clavePublicaContraparteBase64 = pubAlice,
            miUsuarioId = idBob,
            remitenteOriginalId = idAlice,
            destinatarioOriginalId = idBob,
            chatId = chatIdFalso
        )
        assertNotEquals("No debe descifrar si el chatId no coincide", mensajeOriginal, resultadoFalso)
        assertEquals("[🔒 Mensaje cifrado para otra conversación]", resultadoFalso)

        // Con el chatId correcto debe descifrar sin problemas
        val resultadoValido = cifradorBob.descifrar(
            textoCifrado = cifrado,
            clavePublicaContraparteBase64 = pubAlice,
            miUsuarioId = idBob,
            remitenteOriginalId = idAlice,
            destinatarioOriginalId = idBob,
            chatId = chatIdReal
        )
        assertEquals(mensajeOriginal, resultadoValido)
    }

    @Test
    fun `mensaje con formato v4 o v3 corrupto no retorna el texto cifrado crudo`() {
        val pubAlice = cifradorAlice.obtenerClavePublicaPropia(idAlice)

        val payloadCorruptoV4 = "ENC:v4:un_solo_bloque_sin_separadores"
        val resultadoV4 = cifradorBob.descifrar(
            textoCifrado = payloadCorruptoV4,
            clavePublicaContraparteBase64 = pubAlice,
            miUsuarioId = idBob
        )
        assertNotEquals("No debe fugar el payload corrupto como texto plano", payloadCorruptoV4, resultadoV4)
        assertEquals("[🔒 Mensaje cifrado - Formato inválido]", resultadoV4)
    }
}
