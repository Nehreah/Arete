package com.renea.arete_social.gui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// PALETA "RADAR" — fuente única de verdad para el estilo visual
// de toda la app (nacido en MapaScreen, reutilizado por
// RadarComponents.kt en el resto de pantallas).
// ============================================================

val RadarBlack = Color(0xFF171717)
val RadarDark = Color(0xFF202020)
val RadarPanel = Color(0xFFFFFFFF)

// Tokens heredados: se conservan los nombres para evitar romper pantallas
// antiguas mientras se migra gradualmente al sistema de diseño nuevo.
val RadarGreen = Color(0xFFD92332)
val RadarGreenBright = Color(0xFFFF646E)
val RadarGreenDark = Color(0xFF9F1723)

val RadarText = Color(0xFF171717)
val RadarMuted = Color(0xFF5F5F5F)
val RadarError = Color(0xFFB3261E)

// Colores de visibilidad, usados por VisibilidadSelector (feature/auth).
val AntiredVerde = Color(0xFF2E7D32)   // Visibilidad.VISIBLE
val AntiredAmbar = Color(0xFFF9A825)   // Visibilidad.APROXIMADA
val AntiredRojo = Color(0xFFC62828)    // Visibilidad.INVISIBLE

// ============================================================
// PALETA M3 — Tokens semánticos del sistema de diseño moderno
//
// Modo oscuro: fondo grafito, texto blanco y acento rojo coral.
// Modo claro: fondo blanco, texto negro y acento rojo de marca.
//
// Nota de accesibilidad: los colores de borde aquí definidos (#D6D6D6 / #3A3A3A)
// son demasiado sutiles para usarse solos en campos de formulario (ratio ~1.35:1).
// En inputs usa siempre onSurfaceVariant / outline junto a etiqueta de texto visible.
// ============================================================

// Modo Oscuro (dark) — tokens nuevos
val AntiredDarkBackground    = Color(0xFF111111)
val AntiredDarkSurface       = Color(0xFF1C1C1C)
val AntiredDarkSurfaceBorder = Color(0xFF3A3A3A)
val AntiredDarkPrimary       = Color(0xFFFF646E)
val AntiredDarkPrimaryStrong = Color(0xFFD92332)
val AntiredDarkOnBackground  = Color(0xFFF5F7F5)
val AntiredDarkOnSurfaceMuted = Color(0xFFBDBDBD)
val AntiredDarkOnPrimary     = Color(0xFFFFFFFF)
val AntiredDarkError         = Color(0xFFFFB4AB)
val AntiredOnlineDot         = Color(0xFFFF646E)

// Modo Día / Claro (light) — blanco, texto negro y acento rojo
val AntiredLightBackground    = Color(0xFFFFFFFF)
val AntiredLightSurface       = Color(0xFFFFFFFF)
val AntiredLightSurfaceBorder = Color(0xFFD6D6D6)
val AntiredLightPrimary       = Color(0xFFD92332)
val AntiredLightPrimaryStrong = Color(0xFFB51D2A)
val AntiredLightOnBackground  = Color(0xFF171717)
val AntiredLightOnSurfaceMuted = Color(0xFF555555)
val AntiredLightOnPrimary     = Color(0xFFFFFFFF)
val AntiredLightError         = Color(0xFFB3261E)

// Alias de retrocompatibilidad (para componentes que aún usen los nombres anteriores)
val AntiredGreenAccent         = AntiredDarkPrimary // Alias heredado; el valor actual es el acento rojo.
val AntiredGreenStrong         = AntiredDarkPrimaryStrong // Alias heredado; el valor actual es rojo.
val AntiredNightBackground     = AntiredDarkBackground
val AntiredNightSurface        = AntiredDarkSurface
val AntiredNightSurfaceBorder  = AntiredDarkSurfaceBorder
val AntiredNightOnBackground   = AntiredDarkOnBackground
val AntiredNightOnSurfaceMuted = AntiredDarkOnSurfaceMuted
val AntiredDayBackground       = AntiredLightBackground
val AntiredDaySurface          = AntiredLightSurface
val AntiredDaySurfaceBorder    = AntiredLightSurfaceBorder
val AntiredDayPrimary          = AntiredLightPrimary
val AntiredDayPrimaryStrong    = AntiredLightPrimaryStrong
val AntiredDayOnBackground     = AntiredLightOnBackground
val AntiredDayOnSurfaceMuted   = AntiredLightOnSurfaceMuted

// Pines semánticos de mapa (fijos, no dependen del tema ni del ciclo)
val PinAvistamientoColor = Color(0xFFF2A649)
val PinEventoColor       = Color(0xFF7C6FD9)

// Colores de categoría de Comunidad: monocromía de marca.
// Se conservan los nombres públicos para compatibilidad con pantallas antiguas.
// Los componentes nuevos deben preferir MaterialTheme.colorScheme para adaptarse al tema.
val CategoriaPsicologiaBg        = Color(0xFFF5F5F5)
val CategoriaPsicologiaIcono     = Color(0xFFD92332)
val CategoriaBibliotecaBg        = Color(0xFFF5F5F5)
val CategoriaBibliotecaIcono     = Color(0xFFD92332)
val CategoriaUniversidadBg       = Color(0xFFF5F5F5)
val CategoriaUniversidadIcono    = Color(0xFFD92332)
val CategoriaSostenibilidadBg    = Color(0xFFF5F5F5)
val CategoriaSostenibilidadIcono = Color(0xFFD92332)
val CategoriaTecnologiaBg        = Color(0xFFF5F5F5)
val CategoriaTecnologiaIcono     = Color(0xFFD92332)

// Colores de marca de la barra de navegación inferior (fijos, no dependen del tema)
val TabPerfilColor    = Color(0xFFD92332)
val TabMascotaColor   = Color(0xFFD92332)
val TabMapaColor      = Color(0xFFD92332)
val TabComunidadColor = Color(0xFFD92332)
val TabChatColor      = Color(0xFFD92332)

// Paleta CyberSolarPunk de la mascota (referencia — los valores reales viven en
// CyberColors dentro de PetCanvas.kt, que ya fue realineado a estos hex exactos).
val CyberVisorMint        = Color(0xFF4ADE80)
val CyberCoreTeal         = Color(0xFF2DD4BF)
val CyberEarBlue          = Color(0xFF5B9FE0)
val CyberPawAmber         = Color(0xFFF0B94A)
val CyberFurAccentOrange  = Color(0xFFE8823C)
val CyberTailMagenta      = Color(0xFFE85FA0)
