package com.renea.arete_social.gui.navigation

import kotlinx.serialization.Serializable

// Rutas sin datos.
@Serializable object Bienvenida
@Serializable object Login
@Serializable object Registro

// Mapa es ahora el único destino "principal": Perfil y Comunidades ya no son
// rutas de navegación propias, sino contenido que se muestra dentro del mismo
// módulo superior de MapaScreen (ver MapaScreen.ModuloExplorador). Por eso ya
// no existen los objetos Perfil ni Comunidades.
@Serializable object Mapa
@Serializable object CrearComunidad
@Serializable object Identidad

// Rutas con datos — Navigation Compose 2.9 las serializa/deserializa solo
// (kotlinx.serialization), sin codificar nada a mano.
@Serializable data class LugarDetalle(val lugarId: String)
@Serializable data class CrearLugar(val latitud: Double, val longitud: Double, val timestamp: Long = System.currentTimeMillis())
@Serializable data class EventoDetalle(val eventoId: String)
@Serializable data class CrearEvento(val lugarId: String)
@Serializable data class ComunidadDetalle(val comunidadId: String)
@Serializable data class Chat(val chatId: String)