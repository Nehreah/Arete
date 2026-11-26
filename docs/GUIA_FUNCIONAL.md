# Guía Funcional del Sistema — Aréte

Guía funcional consolidada de las características, dinámicas sociales y flujos de usuario en **Aréte**, una red social libre, local y descentralizada.

---

## 1. Regiones y Chats Regionales

- **Sala General (#RADAR):** Canal público accesible por defecto para toda la comunidad local en el radio de la malla.
- **Chats Regionales:** Soporte de seis regiones principales: Cali, Bogotá, Medellín, Barranquilla, Cartagena y Bucaramanga.
- **Región Actual:** La aplicación determina la región más próxima y la destaca automáticamente como **«Tu región»** con margen de histéresis geográfica.
- **Alcance Regional:** El contenido de una región se focaliza en un radio de hasta 100 km desde su punto de referencia.

---

## 2. Eventos y Chats de Lugares Cercanos

Un evento o lugar puede aparecer por distintas razones. La aplicación combina las fuentes de visibilidad y evita mostrar duplicados:

| Fuente | Cómo se obtiene | Vigencia prevista |
|---|---|---|
| **Cercanía física** | La persona está a **2.000 m (2 km) o menos** del lugar o evento. | Se conserva durante 24 horas desde la última vez que estuvo cerca. Volver a acercarse renueva el plazo. |
| **Descubrimiento reciente** | El lugar o evento se descubrió físicamente y aún no vence ese descubrimiento. | Hasta 24 horas desde el último contacto físico registrado. |
| **Pase compartido** | Otra persona comparte voluntariamente un descubrimiento y el receptor lo acepta/recibe. | 24 horas para el receptor. Repetir el pase mientras siga visible no reinicia el plazo. |
| **Modo Súper** | El Modo Súper permite descubrir temporalmente contenido público de la región correspondiente. | Solo mientras el Modo Súper esté activo; no prolonga por sí solo el descubrimiento normal. |

### Reglas de Vencimiento:
- Cuando una fuente vence, deja de aportar visibilidad.
- Si otra fuente válida sigue activa, el evento o chat continúa visible por esa otra razón.
- Cuando ya no queda ninguna fuente vigente, el evento/chat se retira de las listas de descubrimientos y del mapa.
- **Los mensajes guardados no se eliminan** por el solo hecho de que venza el descubrimiento.

---

## 3. Compartir Descubrimientos con Consentimiento

Compartir eventos y chats descubiertos por cercanía requiere una acción explícita de la persona:

### Flujo de «Activar parche»:
1. La persona pulsa **«Activar parche»** en la interfaz de Michi.
2. Mientras esté activo, Michi se desgasta más rápido y su avatar brilla con un color especial en el mapa.
3. Otra persona pulsa el avatar brillante.
4. La aplicación muestra la confirmación: **«Sincronizarás los eventos descubiertos por este usuario, ¿sí o no?»**
5. Solo si la persona confirma, se inicia el intercambio de los eventos/chats descubiertos que sean elegibles.
6. Si rechaza, no se sincronizan los descubrimientos de esa persona.

### Límites del Intercambio:
- El pase incluye únicamente lugares/eventos públicos elegibles.
- Un pase recibido tiene una vigencia de 24 horas para quien lo recibe.
- Los elementos recibidos mediante pase no se pueden volver a compartir como propios, evitando que se propaguen indefinidamente en cadena.

---

## 4. Modo Súper de Michi

El Modo Súper es una fuente temporal adicional de descubrimientos regionales:
- **Condición de Activación:** Comida, ánimo, higiene y salud al 100 %, y energía en al menos 97 %.
- **Duración:** Hasta 3 horas continuas.
- **Enfriamiento:** 20 horas entre activaciones.
- **Efecto:** Permite ver eventos y lugares públicos de la región más cercana, identificados con un marcador dorado.

---

## 5. Mensajería Directa (1-a-1) y Comunicación Híbrida

- **Cifrado E2EE v4:** Todos los mensajes directos se cifran con AES-256-GCM y ECDH P-256. La clave nunca viaja por la red.
- **Interacción con Amigos Sin Límite de Distancia:**
  - Si un usuario está agregado en tu lista de amigos confirmados, puedes comunicarte con él a través de Internet (Wi-Fi o Datos Móviles) a cualquier distancia del planeta.
  - Si ese mismo amigo está cerca físicamente, la conversación conmuta transparentemente a enlace directo Bluetooth LE / Wi-Fi LAN sin consumir datos móviles.
- **Comunidades, Murales y Gacetas a Distancia vs. Hiperlocales:**
  - **Comunidades Hiperlocales:** Sus eventos y conversaciones están acotados al radio físico local ($\le 2\text{ km}$).
  - **Comunidades y Gacetas a Distancia:** Foros y boletines temáticos con alcance global. Se sincronizan por internet hacia los nodos conectados y se propagan oportunistamente por Bluetooth hacia los celulares sin conexión mediante nodos que actúan como «mulas de datos».
- **Indicador E2EE:** Icono `🔒 E2EE` visible cuando las claves están negociadas; `⚠️ Sin clave` si aún no se ha completado el intercambio de claves públicas.
- **Mensajes de Voz (PTT):** Transmisión de notas de voz en tiempo real grabadas localmente.
- **Tarjetas Interactivas Anexadas:** Posibilidad de adjuntar comunidades locales o eventos públicos dentro de la conversación para unirse con un solo toque.
- **Eliminación Local Soberana:** Soporte para eliminar mensajes del almacenamiento local con confirmación explícita.

---

## 6. Bitácora Efímera y Perfil Táctico

- **Vigencia Semanal (7 días):** Las publicaciones de la bitácora (notas retro y fotos con filtro CRT Matrix) tienen una vigencia estricta de 7 días, tras lo cual se depuran automáticamente.
- **Privacidad del Perfil:**
  - **Público:** Quienes se crucen contigo por Bluetooth o malla pueden ver tu bitácora e intereses.
  - **Privado:** Solo los contactos confirmados como amigos tienen acceso a tu bitácora.
- **Selector de Visibilidad en Radar:**
  - `VISIBLE`: Transmite baliza de presencia a pares en rango.
  - `APROXIMADA`: Difumina las coordenadas en el mapa (50-100 m de margen) para impedir localización milimétrica.
  - `INVISIBLE`: Oculta completamente la presencia en el mapa táctico.

---

## 7. Minijuego Miau Snake

- Minijuego de habilidad táctica accesible desde el menú de Michi.
- Motor de simulación determinista que permite generar y validar retos P2P (`MIAU1-…`).
- Desafíos compartibles por chat mesh o código QR sin conexión a internet.
- Otorga puntos de experiencia y recompensas cosméticas para Michi al registrar partidas legales.
