# Índice General de la Documentación — Aréte

Bienvenido al repositorio de documentación técnica y funcional de **Aréte**, la red social táctica, libre y descentralizada (100% Local-First / Mesh P2P).

---

## 🏛️ 1. Arquitectura y Seguridad Central

Los documentos fundamentales que describen la ingeniería, protocolos y superficie de seguridad del sistema:

| Documento | Descripción |
|---|---|
| [**ARQUITECTURA.md**](ARQUITECTURA.md) | Arquitectura técnica 100% local, Clean Architecture, capas del sistema (Dominio puro, Datos y Presentación Compose), criptografía E2EE v4, protocolos BLE/GATT y Wi-Fi LAN. |
| [**seguridad/MODELO_DE_AMENAZAS.md**](seguridad/MODELO_DE_AMENAZAS.md) | Análisis de superficie de ataque, vectores de amenaza en redes ad-hoc, firmas ECDSA, mitigaciones TOFU, replay protection y privacidad en radar. |
| [**POLITICA_PRIVACIDAD.md**](POLITICA_PRIVACIDAD.md) | Política de privacidad integral: cero servidores centrales, cero telemetría, almacenamiento estrictamente local, conforme a directrices de F-Droid y software libre. |
| [**EULA.md**](EULA.md) | Términos de Servicio, Acuerdo de Licencia de Usuario Final (EULA) y Normas Comunitarias (+18, tolerancia cero CSAM/acoso, moderación humana y descargo de responsabilidad). |

---

## 🗺️ 2. Guías Funcionales y Dinámicas Sociales

Especificación de las características de usuario y dinámicas de interacción comunitaria:

| Documento | Descripción |
|---|---|
| [**GUIA_FUNCIONAL.md**](GUIA_FUNCIONAL.md) | Especificación de dinámicas sociales: radar de cercanía, salas regionales, eventos comunitarios, intercambio consentido de pases, mensajería E2EE y bitácora efímera. |
| [**miau-snake/README.md**](miau-snake/README.md) | Arquitectura del motor de Miau Snake: bucle de paso fijo, PRNG determinista, verificación de repeticiones y protocolo de retos P2P. |
| [**miau-snake/PRIVACIDAD.md**](miau-snake/PRIVACIDAD.md) | Garantías de privacidad del minijuego: almacenamiento local, paquetes de reto anónimos sin telemetría ni rastreo. |

---

## 🎨 3. Diseño Visual y Mundo Pixel Art

Especificaciones de diseño de interfaz de usuario y sistemas gráficos:

| Documento | Descripción |
|---|---|
| [**diseno/SISTEMA_DISENO.md**](diseno/SISTEMA_DISENO.md) | Sistema visual: tokens de color Material 3 (blanco/grafito/rojo), componentes tácticos (`TarjetaTacticalBox`), shaders CRT Matrix y desacoplamiento tema/ciclo solar. |
| [**diseno/SISTEMA_MICHI_PIXEL_ART.md**](diseno/SISTEMA_MICHI_PIXEL_ART.md) | Especificación de Michi: renderizado de sprites en pixel art con escala entera, ambientes sociales compartidos y mimis en el mapa. |
| [**diseno/diagrama-clases-app-campus.puml**](diseno/diagrama-clases-app-campus.puml) | Diagrama UML PlantUML de entidades y relaciones del sistema. |

---

## 🤝 4. Desarrollo y Comunidad

| Documento | Descripción |
|---|---|
| [**CONTRIBUTING.md**](CONTRIBUTING.md) | Guía de contribución para el desarrollo comunitario bajo licencia libre GNU GPL-3.0. |
| [**CHANGELOG.md**](CHANGELOG.md) | Registro de versiones y cambios del proyecto (Keep a Changelog / SemVer). |
| [**LICENCIA_RECURSOS.md**](LICENCIA_RECURSOS.md) | Inventario, atribución y licenciamiento libre (CC-BY-4.0 / GPL-3.0 / CC0) de todas las ilustraciones, fondos e iconos. |
| [**index.html**](index.html) | Landing page de presentación del proyecto con enlace directo de descarga del APK para GitHub Pages. |
