# Registro de Cambios (Changelog)

Todas las modificaciones notables a este proyecto se documentarán en este archivo.

El formato se basa en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/),
y este proyecto se adhiere a [Semantic Versioning](https://semver.org/lang/es/).

## [1.1.1] - 2026-11-26

### Corregido
- **Crear comunidad**: El campo `tipo` se guardaba vacío en Room cuando el usuario no seleccionaba categoría; ahora se aplica `"ABIERTA"` como valor por defecto antes de persistir.
- **Miembros de eventos**: Al unirse o salir de un evento, la lista de participantes no se actualizaba visualmente porque `cargar()` lanzaba una corutina independiente que podía ejecutarse antes de que Room confirmara la escritura. Ahora `cargar()` es `suspend` y se ejecuta secuencialmente dentro de la misma corutina, garantizando que los datos leídos son los ya escritos.

## [1.1.0] - 2026-11-25


### Añadido
- Manifiesto de autoría y visión emancipadora comunitaria en `README.md`, `docs/index.html`, `docs/CONTRIBUTING.md`, `docs/EULA.md` y `docs/INDICE.md`.
- Documento de especificación `docs/COMUNICACION_MESH.md` que detalla la red de malla híbrida (BLE, Wi-Fi LAN, pasarelas de enlace y mulas de datos).
- Nueva compilación de instalación directa `docs/arete.apk` generada desde código fuente limpio.

### Modificado
- `README.md` e `index.html`: Inclusión de secciones de autoría (estudiante de Tecnología en Desarrollo de Software en colaboración con IA), proyecto colectivo de Software Libre al servicio de la comunidad sin algoritmos ni recolección de datos.
- `EULA.md` y `eula.html`: Actualización de términos reafirmando la naturaleza de software libre emancipador y no comercial.
- `GUIA_FUNCIONAL.md` y `ARQUITECTURA.md`: Ajustes de alcance de radar de cercanía y enrutamiento con amigos a distancia.

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
