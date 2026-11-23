# Registro de Cambios (Changelog)

Todas las modificaciones notables a este proyecto se documentarán en este archivo.

El formato se basa en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/),
y este proyecto se adhiere a [Semantic Versioning](https://semver.org/lang/es/).

## [1.0.0] - 2026-11-20

### Añadido
- Licencia oficial GNU General Public License v3.0 (GPL-3.0-or-later).
- Metadatos Fastlane para publicación en F-Droid (`fastlane/metadata/android/es-ES` y `en-US`).
- Implementación de `ModuloLocal` para soporte 100% autónomo y offline sin servidores centrales.
- Repositorios locales en memoria y almacenamiento protegido para salas, lugares, regiones, amistades, encuentros, moderación y autenticación.
- Posicionamiento GPS nativo mediante Android `LocationManager` respetuoso con la privacidad sin depender de Google Play Services.
- Señalización de llamadas y audio P2P directo a través de la malla local.
- Guías de contribución (`CONTRIBUTING.md`) y documentación técnica actualizada.

### Modificado
- `RepositorioMensajesHibrido`: Adaptado a arquitectura local-first con desacoplamiento total de backend remoto.
- `GestorPerfilBle`: Soporte para perfiles públicos y privados en descubrimiento y entrega modular v2.
- `CartasViewModel`: Envío de cartas a perfiles públicos sin necesidad de amistad previa, manteniendo privacidad en perfiles privados.
- `MessageRouterMesh`: Verificación TOFU robusta que preserva estados verificados y admite reconexión de pares locales tras reinstalación.

### Eliminado
- Eliminadas todas las dependencias y código de Supabase (`io.github.jan-tennert.supabase:*`).
- Eliminadas dependencias de Google Play Services (`play-services-location`, `play-app-update`, `play-services-code-scanner`).
- Eliminadas dependencias de red externa innecesarias (`ktor-client-android`).
- Eliminadas credenciales y variables de entorno de servidores remotos en `build.gradle.kts`.
