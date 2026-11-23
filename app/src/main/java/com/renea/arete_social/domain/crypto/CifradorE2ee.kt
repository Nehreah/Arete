package com.renea.arete_social.domain.crypto

/**
 * Contrato de dominio para operaciones de Cifrado de Extremo a Extremo (E2EE).
 * Permite que los ViewModels y la capa de dominio cifren y descifren mensajes
 * sin acoplarse a detalles de plataforma (Android Keystore / JCA / librerías nativas).
 */
interface CifradorE2ee {

    /**
     * Obtiene la clave pública local (formato estándar X.509 Base64) para [usuarioId].
     * Si aún no existe un par de claves en el almacenamiento local seguro del dispositivo,
     * lo genera de forma transparente.
     */
    fun obtenerClavePublicaPropia(usuarioId: String): String

    /**
     * Cifra [textoPlano] para el receptor identificado por [clavePublicaDestinatarioBase64]
     * utilizando la clave privada de [miUsuarioId].
     * Devuelve un payload con prefijo "ENC:v3:<ivBase64>:<ciphertextBase64>".
     *
     * @param destinatarioId Identificador del destinatario utilizado como AAD (Additional Authenticated Data).
     * @throws CifradoE2eeException si NO se pudo cifrar.
     */
    @Throws(CifradoE2eeException::class)
    fun cifrar(
        textoPlano: String,
        clavePublicaDestinatarioBase64: String,
        miUsuarioId: String,
        destinatarioId: String = "",
        chatId: String = ""
    ): String

    /**
     * Descifra un payload cifrado ("ENC:v3:...") generado entre [miUsuarioId] y la
     * contraparte identificada por [clavePublicaContraparteBase64].
     *
     * @param remitenteOriginalId Identificador del remitente que originó el mensaje para validación de AAD.
     * Si la clave o el AAD no coinciden, devuelve un mensaje de advertencia seguro.
     */
    fun descifrar(
        textoCifrado: String,
        clavePublicaContraparteBase64: String,
        miUsuarioId: String,
        remitenteOriginalId: String = "",
        destinatarioOriginalId: String = "",
        chatId: String = ""
    ): String

    /**
     * Determina si [contenido] corresponde a un mensaje cifrado E2EE v3.
     */
    fun esCifrado(contenido: String): Boolean

    /**
     * Clave pública E2EE de [usuarioId] guardada en el dispositivo la última vez que se pudo
     * descargar o recibir por mesh.
     */
    fun obtenerClavePublicaConocida(usuarioId: String): String? = null

    /**
     * Guarda la clave pública E2EE conocida junto con su etiqueta de origen ("verificado" o "mesh_no_verificada").
     */
    fun guardarClavePublicaConocida(
        usuarioId: String,
        clavePublicaBase64: String,
        origen: String = "mesh_no_verificada"
    ) = Unit

    /**
     * Devuelve el origen de la clave pública conocida ("verificado" o "mesh_no_verificada").
     */
    fun obtenerOrigenClavePublicaConocida(usuarioId: String): String? = null

    /**
     * Elimina la clave pública E2EE conocida y su etiqueta de origen para [usuarioId].
     */
    fun borrarClavePublicaConocida(usuarioId: String) = Unit
}

class CifradoE2eeException(mensaje: String, causa: Throwable? = null) : Exception(mensaje, causa)
