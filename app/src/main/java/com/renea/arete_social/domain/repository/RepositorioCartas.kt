package com.renea.arete_social.domain.repository

import com.renea.arete_social.domain.model.Carta
import com.renea.arete_social.domain.model.NuevaCarta

interface RepositorioCartas {
    suspend fun enviar(remitenteId: String, remitenteNombre: String, nueva: NuevaCarta): Carta
    suspend fun recibidas(usuarioId: String): List<Carta>
    suspend fun enviadas(usuarioId: String): List<Carta>

    /**
     * Cartas intercambiadas SOLO entre [usuarioId] y [otroId] (en ambos sentidos), de la más
     * reciente a la más antigua. Es la "correspondencia" que se muestra en el perfil de [otroId].
     * Las cartas de terceros nunca se exponen: el servidor (RLS) solo entrega las propias.
     */
    suspend fun conversacion(usuarioId: String, otroId: String): List<Carta> =
        (recibidas(usuarioId) + enviadas(usuarioId))
            .filter { it.remitenteId == otroId || it.destinatarioId == otroId }
            .distinctBy { it.id }
            .sortedByDescending { it.creadaEn }

    suspend fun marcarLeida(cartaId: String)
    suspend fun eliminar(cartaId: String)
}
