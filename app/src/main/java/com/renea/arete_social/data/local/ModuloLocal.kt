package com.renea.arete_social.data.local

import com.renea.arete_social.domain.repository.RepositorioAmistades
import com.renea.arete_social.domain.repository.RepositorioEncuentros
import com.renea.arete_social.domain.repository.RepositorioLibreria
import com.renea.arete_social.domain.repository.RepositorioLugares
import com.renea.arete_social.domain.repository.RepositorioMensajes
import com.renea.arete_social.domain.repository.RepositorioModeracion
import com.renea.arete_social.domain.repository.RepositorioPublicacionesPerfil
import com.renea.arete_social.domain.repository.RepositorioRegiones
import com.renea.arete_social.domain.repository.RepositorioSalas

/**
 * Módulo 100% local y descentralizado que expone los repositorios locales P2P
 * sin depender de servidores centrales externos.
 */
class ModuloLocal(
    val lugares: RepositorioLugares,
    val mensajes: RepositorioMensajes,
    val salas: RepositorioSalas,
    val libreria: RepositorioLibreria,
    val amistades: RepositorioAmistades,
    val encuentros: RepositorioEncuentros,
    val publicacionesPerfil: RepositorioPublicacionesPerfil,
    val regiones: RepositorioRegiones,
    val moderacion: RepositorioModeracion
)
