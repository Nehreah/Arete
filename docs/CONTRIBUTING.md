# Guía de Contribución a Aréte

¡Gracias por tu interés en contribuir a Aréte! Este proyecto es **Software Libre** licenciado bajo la **GNU General Public License v3.0 (GPL-3.0)**.

## Principios del Proyecto

1. **100% Local y Descentralizado**: Aréte no utiliza ni dependerá de servidores centrales, telemetría remota ni servicios en la nube privativos.
2. **Sin Rastreo ni Bibliotecas Privativas**: Todo el código debe ser compatible con los estándares de [F-Droid](https://f-droid.org/). No se permiten dependencias de Google Play Services, Firebase ni SDKs propietarios.
3. **Privacidad y Criptografía Extremo a Extremo**: Las comunicaciones directas deben preservar la privacidad mediante cifrado de extremo a extremo (E2EE) y firmas digitales.
4. **Software Libre Copyleft**: Cualquier contribución queda licenciada bajo la GPLv3.

## Cómo Contribuir

### Reportar Errores o Sugerir Mejoras
- Abre un issue en el repositorio describiendo detalladamente el problema o propuesta.
- Incluye el modelo de dispositivo, versión de Android y pasos para reproducir.

### Enviar Cambios (Pull Requests)
1. Haz un fork del repositorio.
2. Crea una rama descriptiva para tu cambio: `git checkout -b feature/mi-mejora` o `git checkout -b fix/mi-arreglo`.
3. Asegúrate de que el código compile limpiamente y todas las pruebas unitarias pasen:
   ```bash
   ./gradlew testDebugUnitTest
   ./gradlew assembleDebug
   ```
4. Envía tu Pull Request con una descripción clara de los cambios realizados.

## Estilo de Código
- Usamos **Kotlin** moderno con las directrices oficiales de código de Jetpack Compose.
- Las funciones suspendibles no deben bloquear el hilo principal.
- No introduzcas dependencias externas innecesarias.
