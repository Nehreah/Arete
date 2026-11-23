package com.renea.arete_social.domain.repository

import com.renea.arete_social.domain.model.Descubrimiento

/**
 * Dónde se recuerdan los lugares y eventos descubiertos. Vive en el almacenamiento del dispositivo:
 * así funciona sin internet y respeta el espíritu mesh. Cada persona tiene los suyos.
 * Las operaciones son bloqueantes; quien llama decide el hilo (ver ServicioDescubrimiento).
 */
interface AlmacenDescubrimientos {
    fun cargar(usuarioId: String): List<Descubrimiento>
    fun guardar(usuarioId: String, descubrimiento: Descubrimiento)
    fun eliminar(usuarioId: String, clave: String)

    /** Borra lo de [usuarioId], o todo si es null. */
    fun limpiar(usuarioId: String? = null)
}
