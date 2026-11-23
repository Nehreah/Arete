package com.renea.arete_social.feature.personajes.logic

import com.renea.arete_social.data.crypto.GestorIdentidadEcdsa
import com.renea.arete_social.feature.personajes.model.escena.AccionObjeto
import com.renea.arete_social.feature.personajes.model.escena.CasaSnapshot
import com.renea.arete_social.feature.personajes.model.escena.CatalogoObjetosCasa
import com.renea.arete_social.feature.personajes.model.escena.CoordenadaTile
import com.renea.arete_social.feature.personajes.model.escena.ObjetoColocado
import com.renea.arete_social.feature.tamagotchi.model.PetEntity

/**
 * Resultado de validar la colocación o movimiento de un objeto en la casa.
 */
data class ResultadoColocacion(
    val exito: Boolean,
    val motivo: String? = null
)

/**
 * Motor central de la casa interactiva de Michi.
 *
 * Administra:
 * 1. Validación espacial de la cuadrícula (detección de colisiones y límites 8x8).
 * 2. Ejecución de acciones de objetos y su aplicación balanceada en [PetEntity].
 * 3. Firma criptográfica e integridad de [CasaSnapshot] para visitas seguras.
 */
object MotorCasa {

    const val GRID_TAMANO = CasaSnapshot.GRID_TAMANO
    const val MAX_OBJETOS = CasaSnapshot.MAX_OBJETOS

    /**
     * Valida si un objeto puede colocarse en la posición especificada sin salirse
     * de la cuadrícula ni colisionar con otros objetos ya existentes.
     */
    fun validarColocacion(
        objetosExistentes: List<ObjetoColocado>,
        nuevo: ObjetoColocado,
        ignorarInstanciaId: String? = null
    ): ResultadoColocacion {
        val def = CatalogoObjetosCasa.obtenerPorId(nuevo.catalogoId)
            ?: return ResultadoColocacion(false, "El objeto '${nuevo.catalogoId}' no existe en el catálogo.")

        // Validar límites de la cuadrícula 8x8
        if (nuevo.x < 0 || nuevo.y < 0) {
            return ResultadoColocacion(false, "Las coordenadas no pueden ser negativas.")
        }
        if (nuevo.x + def.anchoTiles > GRID_TAMANO || nuevo.y + def.altoTiles > GRID_TAMANO) {
            return ResultadoColocacion(false, "El objeto excede los límites de la habitación (8x8).")
        }

        // Validar límite total de objetos
        val cantidadActual = objetosExistentes.count { it.instanciaId != ignorarInstanciaId }
        if (cantidadActual >= MAX_OBJETOS) {
            return ResultadoColocacion(false, "Se ha alcanzado el límite máximo de $MAX_OBJETOS objetos por casa.")
        }

        // Validar colisiones espaciales
        val nuevasCeldas = nuevo.celdasOcupadas(def.anchoTiles, def.altoTiles).toSet()

        for (existente in objetosExistentes) {
            if (existente.instanciaId == ignorarInstanciaId || existente.instanciaId == nuevo.instanciaId) continue
            val defExistente = CatalogoObjetosCasa.obtenerPorId(existente.catalogoId) ?: continue
            val celdasExistente = existente.celdasOcupadas(defExistente.anchoTiles, defExistente.altoTiles)

            val celdaSolapada = celdasExistente.firstOrNull { it in nuevasCeldas }
            if (celdaSolapada != null) {
                return ResultadoColocacion(
                    exito = false,
                    motivo = "El objeto se solapa con '${defExistente.nombre}' en (${celdaSolapada.x}, ${celdaSolapada.y})."
                )
            }
        }

        return ResultadoColocacion(exito = true)
    }

    /**
     * Aplica la acción de un objeto colocado sobre las necesidades y felicidad de la mascota.
     * @return Par con la [PetEntity] actualizada y el mensaje descriptivo de Michi.
     */
    fun ejecutarAccionObjeto(
        pet: PetEntity,
        accion: AccionObjeto,
        ahora: Long = System.currentTimeMillis()
    ): Pair<PetEntity, String> {
        val actualizada = when (accion) {
            AccionObjeto.DORMIR -> {
                pet.copy(
                    energy = minOf(100, pet.energy + 25),
                    isSleeping = false,
                    duermeDesdeMillis = 0L,
                    lastTickMillis = ahora
                )
            }
            AccionObjeto.COMER -> {
                pet.copy(
                    hunger = minOf(100, pet.hunger + 20),
                    happiness = minOf(100, pet.happiness + 5),
                    lastTickMillis = ahora
                )
            }
            AccionObjeto.ASEARSE -> {
                pet.copy(
                    hygiene = minOf(100, pet.hygiene + 25),
                    happiness = minOf(100, pet.happiness + 5),
                    lastTickMillis = ahora
                )
            }
            AccionObjeto.JUGAR -> {
                pet.copy(
                    happiness = minOf(100, pet.happiness + 20),
                    energy = maxOf(0, pet.energy - 8),
                    experiencia = pet.experiencia + 15,
                    lastTickMillis = ahora
                )
            }
            AccionObjeto.LEER -> {
                pet.copy(
                    happiness = minOf(100, pet.happiness + 15),
                    experiencia = pet.experiencia + 20,
                    lastTickMillis = ahora
                )
            }
            AccionObjeto.ADMIRAR -> {
                pet.copy(
                    happiness = minOf(100, pet.happiness + 10),
                    lastTickMillis = ahora
                )
            }
        }

        return Pair(actualizada, accion.mensajeMichi)
    }

    /**
     * Sella la instantánea de la casa con la firma ECDSA del propietario.
     */
    fun firmarSnapshot(
        snapshot: CasaSnapshot,
        gestorIdentidad: GestorIdentidadEcdsa?
    ): CasaSnapshot {
        if (gestorIdentidad == null) return snapshot
        val firma = gestorIdentidad.firmar(snapshot.datosFirmados())
        return snapshot.copy(firmaPropietario = firma)
    }

    /**
     * Verifica la autenticidad e integridad de una instantánea de casa.
     */
    fun verificarFirmaSnapshot(
        snapshot: CasaSnapshot,
        clavePublicaPropietario: String,
        gestorIdentidad: GestorIdentidadEcdsa?
    ): Boolean {
        val firma = snapshot.firmaPropietario ?: return false
        if (gestorIdentidad == null) return false
        return gestorIdentidad.verificarFirma(
            datos = snapshot.datosFirmados(),
            firmaBase64 = firma,
            clavePublicaBase64 = clavePublicaPropietario
        )
    }
}
