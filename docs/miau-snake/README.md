# Miau Snake — Minijuego Táctico Descentralizado

**Miau Snake** es un minijuego táctico integrado en **Aréte**, diseñado bajo filosofía 100% local-first, determinista y sin servidores. Conecta de forma opcional con la mascota virtual (Michi) para otorgar experiencia y recompensas estéticas sin alterar su estado crítico.

---

## 1. Arquitectura del Motor (Core Engine)

El núcleo del juego (`feature/miausnake/core/`) es completamente independiente de Android, Compose o frameworks de red, permitiendo ejecución determinista tanto en la app como en pruebas unitarias puras en la JVM:

- **Bucle de Simulación de Paso Fijo (Fixed-Step Loop):**
  - La lógica se ejecuta a pasos discretos basados en tics temporales (`tick()`), desacoplada de la tasa de refresco visual (FPS).
  - Curva de aceleración calibrada progresivamente según los puntos obtenidos (`SpeedCurve.kt`).
- **Generador Pseudoaleatorio Determinista (PRNG):**
  - Emplea un generador congruencial lineal de 64 bits inicializado mediante semilla (`seed: Long`).
  - Dada una semilla idéntica y la misma secuencia de entradas, la partida se reproduce exactamente igual en cualquier dispositivo.
- **Validación y Resimulación:**
  - El motor permite reproducir y verificar partidas completas (`Resimulate.kt`) a partir del registro comprimido de giros del jugador (`Replay.kt`), garantizando que la puntuación final sea matemáticamente legítima.

---

## 2. Protocolo de Retos P2P

Los jugadores pueden desafiar a amigos cercanos o lejanos mediante paquetes codificados autónomos (`MIAU1-…` y `MIAUR1:…`):

1. **Estructura del Paquete:**
   - Modo de juego (Clásico, Laberinto, Portal).
   - Semilla inicial del PRNG.
   - Puntuación objetivo y tiempo.
   - Secuencia comprimida de inputs / giros.
   - Checksum CRC32 para detección de corrupción de datos.
2. **Canales de Transporte Soportados:**
   - **Malla Mesh de Aréte:** Envío directo mediante paquetes `PaqueteMesh` cifrados E2EE o por canales de chat.
   - **Exportación Local:** Archivos `.miau` compartibles mediante el selector del sistema Android.
   - **Códigos QR:** Representación visual escaneable para desafíos cara a cara sin conexión previa.

---

## 3. Integración con Michi (Puente Seguro)

La comunicación entre el minijuego y la mascota virtual se realiza mediante `MichiPlayBridge.kt`, aplicando un diseño no destructivo:
- **Lectura Pasiva:** Lee la especie, etapa de crecimiento y estado anímico para personalizar el sprite de la serpiente (aspecto temático de Michi).
- **Recompensas Limitadas:** Al finalizar una partida válida, Michi recibe pequeñas cantidades de diversión/experiencia. El puente nunca reduce salud ni modifica estadísticas críticas si la partida se interrumpe.
