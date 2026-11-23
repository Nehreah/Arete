package com.renea.arete_social.data.remote

/**
 * Representa un sobre cifrado ciego almacenado o recuperado de un relé.
 * El relé solo conoce la etiqueta del buzón, el timestamp y el payload cifrado (con padding).
 * No conoce autores, identidades ni contenido en claro.
 */
data class RelayEnvelope(
    val id: String,
    val mailboxTagHex: String,
    val payloadBytes: ByteArray,
    val timestampMs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RelayEnvelope

        if (id != other.id) return false
        if (mailboxTagHex != other.mailboxTagHex) return false
        if (!payloadBytes.contentEquals(other.payloadBytes)) return false
        if (timestampMs != other.timestampMs) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + mailboxTagHex.hashCode()
        result = 31 * result + payloadBytes.contentHashCode()
        result = 31 * result + timestampMs.hashCode()
        return result
    }
}

/**
 * Interfaz de transporte de relé ciego según las secciones 4.2 y 10.6 de la auditoría v3.
 *
 * Principio rector: El relé es un buzón no confiable (untrusted mailbox store-and-forward).
 * Toda operación contenida en un sobre se valida exclusivamente en el dispositivo receptor.
 */
interface RelayTransport {

    /**
     * Deposita un sobre en el buzón indicado.
     *
     * @param mailboxTagHex Etiqueta del buzón (derivada de la clave de admisión o época).
     * @param envelopeBytes Bytes del sobre canónico cifrado.
     * @param ttlSeconds Tiempo de vida en segundos (default 30 días = 2,592,000 s).
     * @return true si el depósito fue aceptado por el relé.
     */
    suspend fun deposit(
        mailboxTagHex: String,
        envelopeBytes: ByteArray,
        ttlSeconds: Long = 2_592_000L
    ): Boolean

    /**
     * Consulta y recupera los sobres pendientes en un buzón desde una marca de tiempo dada.
     *
     * @param mailboxTagHex Etiqueta del buzón a consultar.
     * @param afterTimestampMs Marca de tiempo mínima (exclusiva) en milisegundos.
     * @return Lista de sobres cifrados recuperados.
     */
    suspend fun fetch(
        mailboxTagHex: String,
        afterTimestampMs: Long = 0L
    ): List<RelayEnvelope>

    /**
     * Confirma la recepción y procesamiento de sobres para permitir su depuración en el servidor.
     *
     * @param mailboxTagHex Etiqueta del buzón.
     * @param envelopeIds Lista de identificadores de sobre a confirmar.
     * @return true si la confirmación fue registrada.
     */
    suspend fun ack(
        mailboxTagHex: String,
        envelopeIds: List<String>
    ): Boolean
}
