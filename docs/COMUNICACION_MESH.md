# Guía Integral de Comunicaciones y Red Mallada Híbrida — Aréte

**Estado:** Canónico / Arquitectura v2  
**Licencia:** GNU General Public License v3.0  
**Stack de Red:** Kotlin puro (Dominio) + Android BLE GATT + Android NSD/mDNS + Sockets TCP/HTTP + Enlace Pasarela Internet (Wi-Fi / Datos Móviles)

---

## 1. Visión General: La Red Mallada Híbrida (Hybrid Mesh Network)

Aréte fue concebida bajo la filosofía **Local-First**, es decir, priorizando la soberanía de los datos, la ausencia de servidores centrales y la resiliencia absoluta ante apagones, censura o caída de infraestructura de telecomunicaciones.

Sin embargo, para garantizar una experiencia de comunicación fluida y ubicua, Aréte no se limita a un único medio físico, sino que opera como una **Red Mallada Híbrida Multi-Portadora**. Cada teléfono celular con la aplicación instalada actúa como un **nodo autónomo** capaz de enrutar, retransmitir y sincronizar información utilizando cualquier medio disponible en tiempo real:

```
                  ┌──────────────────────────────────────────────┐
                  │          SOBRE DE OPERACIÓN UNIFICADO        │
                  │   (Firmado y cifrado E2EE: agnóstico a red)   │
                  └──────────────────────┬───────────────────────┘
                                         │
        ┌────────────────────────────────┼────────────────────────────────┐
        ▼                                ▼                                ▼
┌───────────────┐              ┌──────────────────┐             ┌──────────────────┐
│ Bluetooth LE  │              │    Wi-Fi LAN     │             │     Internet     │
│  (GATT Mesh)  │              │ (NSD / Sockets)  │             │ (Wi-Fi / Datos)  │
│  10 a 80 m    │              │  Hasta cientos m │             │  Sin límite km   │
└───────┬───────┘              └────────┬─────────┘             └────────┬─────────┘
        │                               │                                │
        └───────────────────────┬───────┴────────────────────────────────┘
                                ▼
                 ┌─────────────────────────────┐
                 │    MessageRouter Central    │
                 │ (Deduplicación SHA-256/TTL) │
                 └─────────────────────────────┘
```

---

## 2. Tecnologías de Transporte Físico

### 2.1. Bluetooth Low Energy (BLE) y GATT Mesh

El transporte por Bluetooth Low Energy es la columna vertebral offline de Aréte. Permite la comunicación directa de dispositivo a dispositivo sin necesidad de emparejamiento (*pairing*), sin router y sin consumir saldo ni batería excesiva.

#### A. Publicidad y Descubrimiento Pasivo (`ProximityManagerBle`)
- **Balizas Efímeras (*Beacons*):** El dispositivo emite paquetes de anuncio BLE (`ScanRecord`) en los canales de publicidad estándar (37, 38 y 39).
- **Prevención de desbordamiento:** Se empaqueta un UUID de servicio único (`0000FD6F-...`) y una carga útil compacta (hash truncado de identidad de usuario y banderas de visibilidad). Esto previene errores del chip Bluetooth como `ADVERTISE_FAILED_DATA_TOO_LARGE`.
- **Escaneo Compartido:** Se utiliza un flujo frío compartido (`shareIn` / `WhileSubscribed`) para evitar el error `ADVERTISE_FAILED_TOO_MANY_ADVERTISERS` y los estrangulamientos de Android (máximo 5 reinicios de escaneo cada 30 segundos).

#### B. Servidor y Cliente GATT (`GattMensajeriaManager`)
- **Conexiones Punto a Punto:** Al detectar la presencia de otro dispositivo Aréte, se establece una conexión cliente-servidor GATT directa mediante su dirección MAC Bluetooth.
- **Fragmentación y Reensamblado (`GattFramer`):**
  - Dado que el MTU (Maximum Transmission Unit) en BLE varía típicamente entre 23 y 517 bytes, los mensajes más largos se fragmentan.
  - Cada fragmento lleva una cabecera binaria canónica de 7 bytes:
    $$\text{Cabecera} = [\text{msgId} : 3\text{B} \mid \text{totalFragmentos} : 2\text{B} \mid \text{indiceFragmento} : 2\text{B}] + \text{Payload}$$
  - El receptor valida la cuota de memoria ($\le 64\text{ KB}$ por mensaje), descarta fragmentos corruptos o duplicados, y limpia automáticamente sesiones incompletas tras 12 segundos sin actividad.

---

### 2.2. Wi-Fi de Red Local (Wi-Fi LAN)

Cuando dos o más dispositivos se encuentran conectados a la misma red Wi-Fi (en un campus universitario, una oficina, un hogar o un router comunitario sin acceso a internet):

#### A. Descubrimiento Zero-Config (`WifiLanTransport`)
- **mDNS / DNS-SD:** Emplea Android Network Service Discovery (NSD) para registrar y resolver el servicio `_arete-mesh._tcp`.
- **Apertura de Sockets TCP:** Cada nodo abre un `ServerSocket` local protegido por timeout (`soTimeout = 15000 ms`).
- **Autenticación Mutua:** Al abrir la conexión TCP se realiza un intercambio de claves públicas con firma digital ECDSA y protección contra repetición (*anti-replay window* de 60 segundos). Paquetes con firmas inválidas se descartan de inmediato sin contaminar las tablas de confianza.

#### B. Transferencia Rápida de Archivos (`GestorArchivosLan`)
- Las fotos de perfil, imágenes de publicaciones y archivos de comunidad se sirven localmente mediante endpoints HTTP seguros en el puerto local, transfiriéndose a máxima velocidad de red Wi-Fi sin tocar internet.

---

### 2.3. Wi-Fi Direct (P2P de Alta Velocidad)

Para intercambios masivos de datos en el campo o en áreas sin router:
- `WifiDirectController` permite negociar un grupo P2P Wi-Fi Direct.
- Uno de los teléfonos asume el rol de *Group Owner* (punto de acceso virtual) y los demás se conectan como clientes, habilitando un ancho de banda de decenas de megabits por segundo sin infraestructura externa.

---

### 2.4. Internet (Wi-Fi y Datos Móviles)

El adaptador `InternetMeshBridge` permite que cualquier teléfono que disponga de conectividad a internet (bien sea por un router Wi-Fi con salida al exterior o mediante su plan de datos móviles 4G/5G) funcione como nodo de enlace global.

#### A. Superación de CGNAT (Carrier-Grade NAT)
- En redes móviles o redes residenciales, los teléfonos no tienen direcciones IP públicas estáticas y las operadoras bloquean las conexiones entrantes directas.
- Aréte utiliza **Buzones Ciegos / Relays Ultralivianos**: servidores de relevo de código abierto donde los nodos se suscriben a canales temáticos o de pares mediante WebSockets o HTTP seguro.
- **Privacidad estricta:** Los servidores relay son «ciegos»; solo ven hashes opacos de canal (`opId` o `communityId`) y texto cifrado de punto a punto (E2EE). No pueden leer mensajes, ni identificar los perfiles, ni alterar el contenido sin invalidar la firma criptográfica.

#### B. Protección de Saldo y Batería
- La aplicación permite definir políticas de uso en datos móviles:
  * *Modo Ahorro:* En datos móviles solo se sincronizan notificaciones y mensajes de texto ligeros ($\le 8\text{ KB}$).
  * *Modo Multimedia:* Las fotos pesadas y descargas de gacetas completas se postergan hasta conectarse a una red Wi-Fi.

---

## 3. El Rol de los Nodos Pasarela (*Gateways*) y Mulas de Datos (*Data Mules*)

Uno de los mayores poderes de la arquitectura híbrida de Aréte es la **sincronización asimétrica** entre celulares desconectados y la red global:

```
[ Celular A: Sin Internet ]  
         │  (BLE)
         ▼
[ Celular B: NODO PASARELA / MULA DE DATOS ]  (Tiene BLE + Datos Móviles / Wi-Fi)
         │  (Internet)
         ▼
[ Red Global / Buzón Relay ]  
         │  (Internet)
         ▼
[ Celular C: En otra ciudad ]
```

### ¿Cómo opera el flujo?

1. **De Offline a Online (Carga Oportunista):**
   - El *Usuario A* está en una zona rural o en un túnel sin señal ni saldo. Escribe una carta o mensaje firmado y su teléfono lo emite por Bluetooth.
   - El *Usuario B* camina cerca de A. Su teléfono recibe el paquete cifrado por BLE y lo almacena en su cola local (`GestorOutboxLocal`).
   - Al llegar a una zona con cobertura de datos o Wi-Fi, el nodo B (actuando como **Mula de Datos**) retransmite el sobre hacia Internet de forma transparente. El mensaje llega a su destinatario al otro lado del planeta.

2. **De Online a Offline (Descarga y Derramamiento Local):**
   - El *Usuario B* descarga a través de Internet la nueva edición de la **Gaceta de una Comunidad** o los mensajes de un mural temático.
   - Cuando B se cruza más tarde con el *Usuario A* (que solo tiene Bluetooth), el nodo B detecta que A está suscrito a esa comunidad y le entrega por el aire vía BLE los paquetes faltantes.
   - ¡El *Usuario A* puede leer información de internet sin haber tenido conexión nunca!

---

## 4. Diferenciación de los Tres Ámbitos de Comunicación

Para evitar la saturación de la malla y garantizar privacidad y orden, Aréte separa el flujo de información en **tres ámbitos de alcance claramente diferenciados**:

| Ámbito | Criterio de Enrutamiento | Límite Físico / Distancia | Transporte Típico | Privacidad / Cifrado |
|---|---|---|---|---|
| **1. Radar de Cercanos** | Proximidad Geográfica | **Estrictamente $\le 2\text{ km}$** | BLE + Celdas Geográficas en Internet | Coordenadas difusas / `APROXIMADA` (50-100 m) o `INVISIBLE` |
| **2. Amistades (1-a-1)** | Clave Pública de Identidad (`IK`) | **Sin límite (Global)** | BLE si están cerca; Internet si están lejos | Cifrado E2EE v4 (ECDH P-256 + AES-256-GCM + AAD) |
| **3. Comunidades y Gacetas** | Identificador de Comunidad (`communityId`) | **Global + Derramamiento Local** | Internet (Relay) + Mulas de Datos por BLE | Firmas de Autoridad en Ledger (`SPEC.md`) |

---

### 4.1. El Radar de Cercanos ($\le 2\text{ km}$)

- **Propósito:** Conocer personas del entorno físico inmediato, interactuar con mascotas Michi vecinas y descubrir eventos hiperlocales.
- **Filtro estricto de 2 km:**
  - Todo dispositivo detectado por GPS o anunciado por internet es evaluado mediante la fórmula matemática de la distancia ortodrómica (**Haversine**):
    $$d = 2R \arcsin\left(\sqrt{\sin^2\left(\frac{\Delta \varphi}{2}\right) + \cos(\varphi_1)\cos(\varphi_2)\sin^2\left(\frac{\Delta \lambda}{2}\right)}\right)$$
  - Si $d > 2.000\text{ metros}$, el dispositivo, evento o lugar es **tajantemente ignorado** y no aparece en la pantalla de radar.
- **Protección contra rastreo:**
  - El usuario puede elegir su modo de visibilidad:
    * `VISIBLE`: Coordenadas emitidas normalmente.
    * `APROXIMADA`: Se añade un sesgo estocástico (redondeo de latitud/longitud a 3 decimales $\approx 100\text{ m}$) para impedir la triangulación exacta de su domicilio.
    * `INVISIBLE`: Apaga la baliza por completo; el usuario puede ver el mapa pero nadie lo ve a él.

---

### 4.2. Amistades a Distancia (1 a 1)

- **Propósito:** Mantener conversaciones directas privadas con amigos de confianza sin importar en qué parte del mundo se encuentren.
- **Blindaje de Privacidad Táctica (Cero Ubicación en Internet):**
  - **Regla estricta:** La ubicación física de un usuario **NUNCA** se transmite por Internet (Wi-Fi o Datos Móviles).
  - Si dos amigos conversan a través de Internet (estén lejos o dentro de los 2 km pero comunicándose por datos), el `InternetMeshBridge` filtra y descarta cualquier paquete que contenga coordenadas de latitud, longitud o balizas de radar.
  - La cercanía física solo se determina de forma local en el dispositivo (cruce físico BLE o GPS local soberano), jamás enviando la posición a través de la red IP pública.
- **Enrutamiento por Identidad:**
  - La conversación no está vinculada a una coordenada geográfica sino al par de identidades:
    $$\text{Canal} = \text{SHA-256}(\min(IK_A, IK_B) \mathbin{\Vert} \max(IK_A, IK_B))$$
  - Si ambos amigos tienen internet, sus teléfonos se conectan a ese canal privado.
  - Si se encuentran físicamente en el mismo vagón de metro o auditorio, el sistema conmuta instantáneamente a enlace BLE directo, ahorrando datos móviles sin que la conversación se interrumpa.

---

### 4.3. Comunidades, Murales y Gacetas a Distancia

- **Comunidades Hiperlocales vs. Remotas:**
  - Existen comunidades circunscritas a un barrio o campus (cuyos anuncios solo viajan en el radio de 2 km).
  - Existen comunidades temáticas globales (cultura, filosofía, tecnología, desarrollo libre de Aréte) donde los libros mayores de operaciones (`CommunityLedger`) y las publicaciones del mural se sincronizan por Internet.
- **Gacetas:**
  - Boletines o publicaciones compuestas distribuidas de forma periódica.
  - Quien crea una gaceta la firma digitalmente con su clave de autoridad. Cualquier nodo conectado a internet puede descargarla y los nodos itinerantes la diseminan localmente por Bluetooth entre sus vecinos desconectados.

---

## 5. Control de Inundación y Mitigación de Ataques en la Malla

Para evitar bucles infinitos (*broadcast storms*) y proteger a los dispositivos de ataques de denegación de servicio (DoS):

1. **Deduplicación por Hash Único (`opId`):**
   - Todo paquete recibido tiene un identificador inmutable:
     $$\text{opId} = \text{SHA-256}(\text{bytesDelSobre} \mathbin{\Vert} \text{bytesDeLaFirma})$$
   - El enrutador (`MessageRouterMesh`) mantiene una caché de deduplicación en memoria (5 minutos) y un registro persistente en SQLite (24 horas). Paquetes ya vistos se descartan en $\mathcal{O}(1)$.
2. **Límite de Saltos (TTL):**
   - Todo paquete comienza con un $\text{TTL} = 7$.
   - Cada nodo intermediario decrementa el TTL en 1 antes de retransmitir. Si llega a 0, el paquete muere y no se reenvía.
3. **Flooding Probabilístico y Jitter:**
   - La retransmisión no es inmediata: se introduce un retardo aleatorio (*jitter*) de entre 50 y 300 ms para desincronizar los chips de radio y prevenir colisiones electromagnéticas en el aire.
4. **Rate-Limiting por Token Bucket:**
   - Cada emisor tiene asignado un cubo de fichas (*Token Bucket*) con tasa de reposición de 10 tokens/segundo y ráfaga máxima de 30 tokens. Nodos maliciosos que intenten saturar la malla son silenciados automáticamente.
5. **Mitigación TOFU (Trust On First Use):**
   - La primera vez que se contacta a un nodo, se registra su clave pública asociada a su identificador. Si un atacante intenta suplantar al nodo presentando otra clave en la malla, el router emite una alerta `alertaClaveTofu` bloqueando el mensaje hasta que el usuario confirme si el contacto reinstaló su aplicación legítimamente.

---

## 6. Resumen de Flujo de Datos

```
[ Mensaje emitido ]
         │
         ▼
[ ¿Destinatario es Amigo? ] ─── SÍ ───> [ ¿Hay Internet? ] ─── SÍ ───> Enviar vía Internet (E2EE)
         │                                       │
         NO                                      NO ───> Enviar vía BLE GATT / Wi-Fi LAN
         │
         ▼
[ ¿Es Comunidad / Gaceta? ] ─── SÍ ───> Sincronizar por Internet + Propagar por BLE local
         │
         NO
         │
         ▼
[ Tráfico Cercanos / Radar ] ───> Filtrar: ¿Distancia ≤ 2 km?
                                            ├── SÍ ──> Mostrar en Radar / Reenviar en radio local
                                            └── NO ──> Descartar silenciosamente
```
