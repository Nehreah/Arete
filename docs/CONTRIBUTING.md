# Guía de Contribución a Aréte

¡Gracias por tu interés en sumarte a **Aréte**! 

## ✊ Origen y Visión Emancipadora

Aréte nació como un proyecto independiente concebido y desarrollado por un **único contribuyente**, **estudiante de Tecnología en Desarrollo de Software**, quien construyó la plataforma en estrecha **colaboración con Inteligencia Artificial (IA)**.

Desde su génesis, el propósito es que Aréte trascienda la autoría individual y florezca como un **proyecto colectivo, emancipador y de Software Libre (*Free Software*)**, distribuido bajo la licencia **GNU General Public License v3.0 (GPL-3.0)**. 

Buscamos entregarle a la sociedad una red social y herramienta de comunicación táctica **100% al servicio de la comunidad**:
- **Sin extracción ni venta de datos:** Cero telemetría, cero servidores corporativos, cero rastreo comercial.
- **Sin algoritmos de manipulación:** Ningún feed opaco ni ganchos adictivos que dicten qué debes leer o sentir.
- **Soberanía y resiliencia:** Comunicación directa entre pares (*peer-to-peer*) mediante Bluetooth LE, Wi-Fi LAN y nodos puente que funciona incluso en apagones o sin conexión a internet.

---

## 🌟 Principios Fundamentales

1. **100% Local-First y Descentralizado**: Aréte no utiliza ni dependerá de servidores centrales, telemetría remota ni servicios en la nube privativos.
2. **Sin Rastreo ni Bibliotecas Propietarias**: Todo el código debe ser compatible con los estándares de [F-Droid](https://f-droid.org/). No se permiten dependencias de Google Play Services, Firebase ni SDKs propietarios.
3. **Privacidad y Criptografía Extremo a Extremo (E2EE)**: Las comunicaciones directas y privadas deben preservar la intimidad mediante cifrado robusto y firmas digitales (ECDH / AES-256-GCM / ECDSA).
4. **Copyleft Fuerte (GPLv3)**: Todo aporte es un bien común que queda liberado para siempre bajo la GPLv3.

---

## 🤝 Áreas Abiertas a la Colaboración Colectiva

Cualquier persona puede participar, no solo programadores:

- **Desarrollo Android & Kotlin:** Optimización de protocolos GATT BLE, enrutamiento mesh, concurrencia con corrutinas y mejoras a la UI en Jetpack Compose.
- **Criptografía y Auditoría de Seguridad:** Análisis de superficie de ataque, revisión del modelo de amenazas y robustecimiento de las pruebas criptográficas.
- **Diseño Visual y Pixel Art:** Nuevas animaciones de Michi en pixel art, elementos de interfaz táctica y diseño accesible.
- **Traducción y Accesibilidad:** Localización a lenguas originarias, dialectos regionales y otros idiomas, además de soporte para lectores de pantalla.
- **Pruebas de Campo:** Despliegue de nodos mesh en campus universitarios, zonas rurales, marchas pacíficas, festivales y comunidades desconectadas.

---

## 🚀 Cómo Empezar a Contribuir

### 1. Reportar Errores o Proponer Ideas
- Abre un *Issue* en GitHub explicando de forma detallada tu idea o error detectado.
- Si reportas un fallo técnico, especifica modelo de celular, versión de Android y cómo reproducirlo.

### 2. Enviar Cambios de Código (Pull Requests)
1. Haz un fork del repositorio en GitHub: [https://github.com/Nehreah/Arete](https://github.com/Nehreah/Arete).
2. Crea una rama descriptiva para tu aporte:
   ```bash
   git checkout -b feature/mejora-malla-ble
   ```
3. Verifica que la compilación y las pruebas unitarias pasen sin errores:
   ```bash
   ./gradlew testDebugUnitTest
   ./gradlew assembleDebug
   ```
4. Envía tu Pull Request detallando qué problema resuelve y cómo fue verificado.

---

## 📐 Estilo de Código y Directrices
- **Kotlin moderno:** Respetar las guías oficiales de Kotlin y Jetpack Compose.
- **Sin bloqueos de hilo:** Funciones suspendibles sin bloquear `Dispatchers.Main`.
- **Cero dependencias innecesarias:** Mantener el binario ligero y auditable.
