# Política de Privacidad de Aréte

**Última actualización:** 22 de noviembre de 2026 · **Versión:** 3.0 (Arquitectura 100% Local-First / P2P Soberana)

---

## 1. Quiénes somos y alcance de esta política

**Aréte** es una aplicación móvil de red social, mensajería táctica y coordinación comunitaria libre y descentralizada para Android, licenciada bajo la **GNU General Public License v3.0 (GPL-3.0)**.

- **Proyecto:** Aréte Open Source Project (Comunidad de Software Libre)
- **Licencia:** GNU General Public License v3.0 (GPL-3.0) — Software 100% Libre
- **Canal oficial de distribución:** F-Droid / Repositorio libre de código abierto

Esta política explica el tratamiento de datos en una arquitectura **100% local-first y de red de malla (Mesh P2P)**: **No recolectamos, no almacenamos en servidores centrales, no rastreamos ni tenemos acceso a ninguno de tus datos personales, comunicaciones o ubicación.**

---

## 2. Principio Soberano: Cero Servidores Centrales, Cero Telemetría

1. **Almacenamiento 100% en tu Dispositivo:**
   Toda tu información (identidad criptográfica, perfil, mensajes, notas de voz, publicaciones locales, cartas, amistades y datos de Michi) se almacena exclusivamente en la memoria interna de tu dispositivo Android mediante bases de datos locales (SQLite/Room) y almacenamiento seguro protegido por Android Keystore.
2. **Comunicaciones Directas Peer-to-Peer (P2P):**
   Las interacciones se realizan directamente de teléfono a teléfono mediante Bluetooth Low Energy (BLE) y Wi-Fi LAN / Wi-Fi Direct. No existen servidores intermediarios ni nubes centrales.
3. **Cero Rastreo, Cero Analítica y Cero Publicidad:**
   Aréte no incluye bibliotecas ni SDKs de analítica, telemetría, publicidad ni servicios de seguimiento (cumplimiento estricto de las directrices de inclusión de F-Droid).
4. **Cifrado Extremo a Extremo (E2EE):**
   Todas las comunicaciones privadas están cifradas de extremo a extremo utilizando curvas elípticas ECDH P-256 y AES-256-GCM. Tu clave privada nunca sale de tu dispositivo.

---

## 3. Permisos del Dispositivo y Finalidad Estricta

Aréte solicita únicamente los permisos indispensables para la operación de la red de malla local:

- **Bluetooth (BLE - Escaneo, Anuncio y Conexión):** Requerido para descubrir otros dispositivos cercanos con Aréte y transmitir paquetes directamente a través de la red mesh sin necesidad de internet.
- **Ubicación (GPS Nativo del Sistema):** Utilizado de manera estrictamente local en tu dispositivo para renderizar el radar de cercanía y situarte en el mapa local (OpenStreetMap / osmdroid). Tu ubicación exacta **nunca se envía a servidores centrales** y solo se comparte de forma efímera y consentida con amistades explícitas durante un encuentro activo.
- **Wi-Fi / Red Local:** Necesario para el transporte LAN local de alta velocidad entre dispositivos conectados a la misma red local o punto de acceso móvil.
- **Cámara y Micrófono:** Requeridos exclusivamente cuando tú decides tomar fotos de perfil o participar en llamadas de audio/video y notas de voz P2P.
- **Servicio en Primer Plano:** Permite mantener viva la recepción de paquetes de malla BLE mientras la aplicación está en segundo plano.

---

## 4. Gestión de Identidad y Frase de Recuperación

1. **Generación Local Soberana:**
   Tu identidad no depende de correos electrónicos, números de teléfono ni identificadores de cuentas remotas. Se genera localmente mediante una frase mnemotécnica de 12 palabras (estándar BIP-39) y claves elípticas.
2. **Cifrado de la Frase Maestra:**
   Tu frase de recuperación se guarda en almacenamiento cifrado protegido por el hardware seguro de Android. Eres el único custodio de tu frase; si cambias de dispositivo o reinstalas la app, tu frase te permite restaurar tu identidad.

---

## 5. Control de Visibilidad y Privacidad

Aréte te otorga control absoluto sobre tu presencia:

- **Visible:** Permite a nodos cercanos y amigos detectar tu nodo en el radar táctico.
- **Aproximada:** Desenfoca tus coordenadas para mostrar solo un radio aproximado (~100 metros).
- **Invisible:** Apaga completamente la publicidad de radiofrecuencia BLE y oculta tu nodo en la red.

---

## 6. Eliminación y Portabilidad de Datos

Dado que todos los datos residen en tu teléfono:
- Puedes eliminar cualquier conversación, publicación, perfil o base de datos en cualquier momento desde **Perfil → Ajustes → Eliminar cuenta y datos**.
- La desinstalación de la aplicación o el borrado de datos de la app en Android elimina instantáneamente e irrevocablemente toda tu información.

---

## 7. Estándares de Seguridad Infantil y Convivencia Comunitaria

1. **Tolerancia Cero ante CSAM/CSAE:** Aréte prohíbe de forma absoluta e incondicional cualquier contenido de abuso o explotación infantil.
2. **Mecanismos de Bloqueo y Denuncia:** Dentro de la aplicación, cada usuario dispone de herramientas para bloquear de forma inmediata a cualquier par y reportar contenidos no deseados en la red local, o enviar denuncias a: **`politicaarete@gmail.com`** con el asunto `[DENUNCIA CSAM]`.

---

## 8. Licencia, Contacto y Código Abierto

Aréte es Software Libre disponible para auditoría, modificación y redistribución bajo los términos de la **GNU General Public License v3.0**.

- **Código fuente:** Repositorio de código abierto del proyecto Aréte para Android (F-Droid).
- **Contacto oficial para privacidad y solicitudes:** **`politicaarete@gmail.com`**
