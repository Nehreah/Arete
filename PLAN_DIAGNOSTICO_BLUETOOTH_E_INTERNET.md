# Aréte — Diagnóstico y plan: Bluetooth (BLE) y comunicación WiFi ↔ datos móviles

**Proyecto analizado:** `Arete-main` (paquete `com.renea.arete_social`)
**Fecha:** 25-nov-2026
**Tipo de análisis:** estático (lectura de código) + una prueba ejecutable sobre la criptografía de Nostr. **No pude correr la app en teléfonos**, así que lo de Bluetooth son hipótesis ordenadas por probabilidad, no un veredicto. Lo de internet sí tiene una causa **comprobada**.

---

## 1. Resumen ejecutivo

| # | Síntoma | Causa | Certeza | Prioridad |
|---|---------|-------|---------|-----------|
| A1 | WiFi ↔ datos nunca se comunican | Las firmas Schnorr de los eventos Nostr **no cumplen BIP-340**; todo relay real las rechaza | **Comprobada** (0/10 firmas válidas con verificador estándar) | 🔴 Crítica |
| A2 | Idem | Se usa `kind 1059` (gift wrap), que la especificación NIP-59 recomienda proteger con AUTH (NIP-42); la app no implementa AUTH | Alta (spec) / depende de cada relay | 🔴 Alta |
| A3 | Idem | La lista de relays por defecto incluye `nostr.wine` (solo escriben miembros de pago) | Verificada en web | 🟠 Media |
| B1 | Dos celulares no se ven por BLE | Servicios de **Ubicación** del sistema apagados → Android no entrega resultados de escaneo | Alta (comportamiento de Android sin `neverForLocation`) | 🔴 Alta |
| B2 | BLE se ve pero no conecta / se cae | `connectGatt` repetido sin control de intento en curso; callbacks viejos pisan el estado del intento nuevo | Alta en condiciones reales | 🔴 Alta |
| B5 | Algunas personas "desaparecen" del radar | El ID BLE de `usuario-local-*` se trunca a 2 caracteres hex útiles → colisiones | Alta (por diseño) | 🟠 Media |

**Conclusión corta:** mientras A1 no se arregle, **ninguna** comunicación por internet puede funcionar, sin importar WiFi o datos. Por eso "alguien en WiFi y otro en datos no se pueden comunicar": no es un problema de redes distintas, es que el único puente entre ellas (el relay) está roto de raíz.

---

## 2. Por qué WiFi y datos móviles no se comunican

### 2.1 Cómo está diseñado el camino

- **WiFi LAN (`WifiLanTransport`, NSD `_antired._tcp`)**: solo funciona entre teléfonos **en la misma red local**. Un teléfono en WiFi y otro en datos jamás se descubren por aquí. Además, muchas WiFi de campus tienen *client isolation* y bloquean mDNS entre clientes; eso es esperado, no un bug.
- **P2P directo UDP (`WebRtcP2pTransport`)**: `registrarEndpointP2p()` **no tiene ningún llamador** en el proyecto (código muerto), así que `enviarDirecto()` siempre devuelve `false`. Aunque se conectara, los datos móviles van detrás de CGNAT y sin STUN/TURN no hay forma de abrir el puerto.
- **Relay ciego (`InternetMeshBridge` → `NostrRelayTransport`)**: es el **único** puente real entre una persona en WiFi y otra en datos. Todo depende de él.

### 2.2 A1 — Firma BIP-340 inválida (CRÍTICA, comprobada)

`NostrCrypto.firmarBip340()` calcula el desafío así:

```kotlin
// NostrCrypto.kt — firmarBip340() y verificarBip340()
val e = BigInteger(1, sha256Bytes(eBytes)).mod(N)   // sha256(rx || px || m)  ← INCORRECTO
```

BIP-340 exige un **hash etiquetado**:

```
e = int( SHA256( SHA256("BIP0340/challenge") || SHA256("BIP0340/challenge") || rx || px || m ) ) mod n
```

**Prueba:** el script adjunto `bip340_check.py` porta 1:1 la firma de la app y la verifica con un verificador de referencia de BIP-340 (validado antes con el vector oficial #0):

```
Vector oficial BIP-340 #0 verifica con el verificador de referencia: True
Firmas generadas con el algoritmo de NostrCrypto aceptadas por verificador BIP-340 estándar: 0/10
```

Consecuencia: cada `["EVENT", …]` que envía `deposit()` es rechazado por el relay con `OK false` ("invalid signature"). `confirmados == 0` → `deposit()` devuelve `false` → el mensaje nunca sale. El test existente (`NostrCryptoTest`) solo comprueba que firma y verificación **de la propia app** sean consistentes entre sí, por eso no lo detectó.

**Arreglo:**

```kotlin
private fun taggedHash(tag: String, vararg partes: ByteArray): ByteArray {
    val th = sha256Bytes(tag.toByteArray(Charsets.UTF_8))
    val buf = partes.fold(th + th) { acc, p -> acc + p }
    return sha256Bytes(buf)
}
// En firmarBip340 y en verificarBip340:
val e = BigInteger(1, taggedHash("BIP0340/challenge", rxBytes, pxBytes, msgHash32)).mod(N)
```

El nonce propio (`sha256(d || m)`) es válido para firmar; lo único que el relay verifica es la ecuación de la firma, así que basta corregir el desafío (y mantener firma/verificación sincronizadas).

**Prueba de aceptación:** agregar a `NostrCryptoTest` los vectores oficiales de BIP-340 (`bip-0340/test-vectors.csv`). Ejemplo, vector #0:

```
pubkey  F9308A019258C31049344F85F89D5229B531C845836F99B08601F113BCE036F9
msg     0000000000000000000000000000000000000000000000000000000000000000
sig     E907831F80848D1069A5371B402410364BDF1C5F8307B0084C55F1CE2DCA8215
        25F66A4A85EA8B71E482A74F382D2CE5EBEEE8FDB2172F477DF4900D310536C0
```

### 2.3 A2 — `kind 1059` y AUTH (NIP-42)

`deposit()` y `fetch()` usan `kind 1059` (gift wrap). La especificación NIP-59 indica que los relays **deberían** proteger la lectura de `kind 1059` con AUTH y servirlos solo al destinatario marcado. La app no implementa NIP-42: `consultarEventosNostr()` ignora los mensajes `AUTH`, `CLOSED` y `NOTICE` ("auth-required"), así que en un relay que lo exija el `fetch` simplemente devuelve vacío tras 4 s, sin error visible. Además aquí el "destinatario" es una clave derivada del tag, no una identidad real, así que una AUTH legítima tampoco encajaría.

Esto no se puede afirmar relay por relay sin probarlos. **Recomendación:** dejar de depender de relays públicos para este caso de uso (ver Fase 2) o usar un `kind` almacenable que no requiera AUTH.

### 2.4 A3 — Lista de relays por defecto

`AjustesRed.RELAYS_POR_DEFECTO` marca `nostr.wine` como "premium permisivo", pero es un relay **pago para escribir** (solo miembros publican); toda escritura ahí será rechazada. `relay.nostr.band` y `relay.snort.social` no pude verificarlos: hay que probarlos con un cliente (p. ej. `nak event … wss://relay`) antes de dejarlos por defecto. Además, los relays públicos se caen o cambian de política con frecuencia.

### 2.5 Otros hallazgos del lado internet

| ID | Gravedad | Hallazgo | Dónde |
|----|----------|----------|-------|
| A4 | 🟠 | WebSocket "a mano": no responde `ping`, no reensambla frames fragmentados (opcode 0x0), no distingue `CLOSED`/`AUTH`. Abre una conexión TLS nueva por relay en **cada** consulta (6 relays, `Semaphore(3)`, timeout 4 s) × 2 tags por usuario cada 8–12 s → latencia de decenas de segundos, batería y datos. | `NostrRelayTransport` |
| A5 | 🟠 | Ventana `since` = último `created_at` − 30 s, usando el reloj **del emisor**. Si un teléfono tiene la hora atrasada >30 s, sus mensajes quedan fuera de la ventana y se pierden para siempre. Mejor: `since = ahora − TTL` y deduplicar por `id`. | `InternetMeshBridge.iniciarBucleRelay` |
| A6 | 🟠 | `iniciarBucleRelay(usuarioId)` captura el ID al arrancar y no se reinicia si cambia la sesión sin cerrar el mesh (login tras usar el ID de dispositivo). Verificar con logs qué ID consulta. | `InternetMeshBridge` |
| A7 | 🟡 | El tag de buzón usa `destinatarioId` tal cual. Si el emisor tiene el ID truncado de BLE (ver B5) y el receptor deriva el tag con su ID completo, los tags no coinciden y el mensaje queda en un buzón que nadie lee. | `depositarEnRelay` |
| A8 | 🟡 | "Hay internet" si `VALIDATED` **o** `NOT_SUSPENDED` (esta última casi siempre es verdadera) → en portal cautivo se declara online sin estarlo. | `actualizarEstadoRed` |
| A9 | 🟡 | El servidor propio `server/relay/main.go` + `HttpRelayTransport` existen pero nunca se usan por defecto (la selección es "si hay un `wss://`, usa Nostr"). El relay guarda en memoria: se pierde todo al reiniciar. | `InternetMeshBridge.relayTransport` |

---

## 3. Por qué dos celulares no se encuentran por Bluetooth

El código BLE está, en general, bien pensado (UN solo escáner y UN solo anunciante compartidos, tiers de respaldo para la publicidad, backoff en fallos de escaneo, fusión por MAC del paquete principal y el scan response). Revisé que el paquete de anuncio cabe en los 31 bytes (28 B) y que el filtro de escaneo `0xFED0` coincide con lo que se anuncia. No encontré un bug que impida **siempre** el descubrimiento, así que lo más probable es una combinación de entorno + robustez de la conexión:

### 3.1 B1 — Servicios de Ubicación apagados (🔴 más probable)

El manifest deja `BLUETOOTH_SCAN` **sin** `neverForLocation` (decisión correcta para estimar cercanía). Con esa decisión, Android exige además el permiso de ubicación **y el interruptor global de Ubicación encendido**; si está apagado, `startScan` "funciona" pero **no llega ningún resultado**, sin error.

- Existe `GestorCompatibilidadFabricantes.estaUbicacionHabilitada()`, pero solo se usa en la pestaña Radar (`ContenidoRadar.kt` ~l.198/501) como aviso.
- `ProximityManagerBle.descubrir()` solo espera permisos y Bluetooth encendido; no considera la Ubicación.

**Arreglo:** incluir `estaUbicacionHabilitada()` en las condiciones de espera, mostrar un banner global (no solo en el radar) y registrar el motivo en logcat.

### 3.2 B2 — `connectGatt` sin control de intento en curso (🔴)

En `GattMensajeriaManager.ClienteGatt`:

- El colector de descubrimiento corre en cada emisión (≥ cada 400 ms mientras llegan anuncios) y llama a `conectar()` si `!estaListo() && debeReintentar()`.
- `debeReintentar()` solo mira el tiempo desde el último intento (2,5 s + jitter). Un `connectGatt` sin `autoConnect` puede tardar hasta ~30 s en fallar, así que con señal débil se lanzan **varios `connectGatt` simultáneos** al mismo peer.
- El valor devuelto por `connectGatt` se descarta (`runCatching { device.connectGatt(...) }`), por lo que no hay manejo para cerrar los intentos pendientes.
- Los callbacks de un intento viejo que falla (p. ej. status 133) ejecutan `alDesconectar()`, que pone `listo=false`, cancela los jobs, **quita el peer del router** y hace `gatt = null`: pisa el estado de un intento nuevo que sí estaba sano.
- `requestMtu(512)` no tiene timeout: si `onMtuChanged` nunca llega, el cliente queda "conectado pero no listo" para siempre.

Resultado típico: "se ven en el radar pero no se mandan mensajes", errores 133 intermitentes y que mejore al apagar/encender Bluetooth.

**Arreglo:** bandera `conectando` + timeout (~12 s) que cierre el `BluetoothGatt` y reintente; guardar el handle de `connectGatt` y cerrarlo antes de uno nuevo; ignorar callbacks cuyo `g` no sea el intento vigente; timeout de 2–3 s para `requestMtu` y seguir con `discoverServices()`.

### 3.3 B5 — ID BLE truncado para `usuario-local-*` (🟠)

Los usuarios con sesión local usan `usuario-local-<UUID>`. `construirPaquetesTier()` solo empaqueta UUID binario si el ID es UUID puro; para este formato cae al tipo `0x03` (texto) cortado a **16 bytes** → `usuario-local-` + **2 caracteres hex**. Consecuencias:

- 256 valores posibles: con ~20 personas cerca, ≈50 % de probabilidad de que dos colisionen y `distinctBy` en `ProximityManagerHibrido` oculte a una.
- El desempate de conexión `miId < uid` compara un ID completo con uno truncado (cuando coinciden los 2 primeros caracteres, ninguno de los dos es iniciador y se depende del fallback de 3,5 s).
- El `PING_DIRECTO` se envía a un `destinatarioId` truncado, y `resolverNombreProveedor` no encuentra el perfil → "Cargando…".

**Arreglo:** anunciar un identificador BLE corto pero único (8–12 bytes de un hash del ID o de la clave pública) y resolver al ID completo en el handshake firmado.

### 3.4 Otros puntos BLE

| ID | Gravedad | Hallazgo |
|----|----------|----------|
| B3 | 🟠 | `ProximityManagerHibrido` documenta `WhileSubscribed` pero usa `SharingStarted.Eagerly`: el BLE **nunca se detiene** (ni `detener()` ni al cerrar sesión se apaga el anuncio; sigue anunciando con la identidad anterior hasta matar la app). |
| B4 | 🟡 | A los 30 s el escaneo pasa a `SCAN_MODE_BALANCED`; en la práctica el primer descubrimiento y la reconexión tras perder señal tardan varios segundos más. Mantener `LOW_LATENCY` mientras el radar esté visible. |
| B6 | 🟡 | Si `isMultipleAdvertisementSupported = false` o `bluetoothLeAdvertiser == null`, el teléfono **solo escucha** y el otro nunca lo verá. Solo hay un `Log.w`; falta avisarlo en la UI. |
| B7 | ℹ️ | `0xFED0`/`0xFED1` están en el rango de UUIDs de 16 bits asignados por el Bluetooth SIG a empresas miembro. No es la causa del problema; un UUID de 128 bits propio sería más correcto, pero no cabe junto al ID en 31 bytes. No tocar ahora. |
| B8 | ℹ️ | Cada celular debe tener concedidos **Dispositivos cercanos** (SCAN, ADVERTISE, CONNECT) y Ubicación. El diálogo solo se lanza desde `MapaScreen`; si se negó una vez, queda `PantallaPermiso`. |

---

## 4. Plan de trabajo por fases

### Fase 0 — Diagnóstico en los dos teléfonos (sin tocar código, ~30 min)

1. En ambos: Bluetooth **ON**, **Ubicación ON** (interruptor global), permiso *Dispositivos cercanos* y *Ubicación* concedidos, sin ahorro de batería para Aréte.
2. Instalar **nRF Connect** en un tercer teléfono (o en uno de los dos): buscar anuncios con servicio `0xFED0`. Si no aparece → problema de anuncio (B6/B8). Si aparece pero Aréte no lo lista → escaneo (B1).
3. Capturar logs de cada teléfono:
   ```
   adb logcat -s ProximityBLE GattManager MessageRouter GestorPerfilBle \
                 InternetMeshBridge NostrRelayTransport AntiRedWifiLAN
   ```
   Buscar: `Requisitos BLE cumplidos…`, `Publicidad BLE iniciada exitosamente (Tier N)`, `Escaneo BLE iniciado`, `Iniciando conexión GATT`, `status=133`, y en internet `RECHAZÓ evento` / `aceptado por 0/N relays`.
4. Confirmar A1 en vivo: debe aparecer `Relay … RECHAZÓ evento … invalid …` en `NostrRelayTransport`.

### Fase 1 — Desbloquear internet (bajo esfuerzo, alto impacto)

- [ ] Corregir el desafío BIP-340 con hash etiquetado (A1) en firma **y** verificación.
- [ ] Agregar los vectores oficiales BIP-340 a `NostrCryptoTest`.
- [ ] Quitar `nostr.wine` de los relays por defecto (A3) y validar uno por uno los restantes.
- **Aceptación:** logcat muestra `Depósito en buzón … aceptado por ≥1/N relays` y un mensaje enviado desde WiFi llega a un teléfono en datos.

### Fase 2 — Hacer el relay fiable

- [ ] Opción recomendada para campus: desplegar `server/relay` (Go, ya en el repo) con almacenamiento persistente y usar `HttpRelayTransport` como transporte principal; dejar Nostr público solo como respaldo.
- [ ] Si se mantiene Nostr: no usar `kind 1059` sin AUTH; manejar `CLOSED`/`AUTH`/`NOTICE auth-required` y registrar el motivo.
- [ ] Reemplazar el WebSocket manual por un cliente real (ping/pong, fragmentación, reconexión) y reutilizar conexiones (A4).
- [ ] `since = ahora − TTL` con deduplicación por `id` (A5); confirmar qué ID usa el bucle tras login (A6).
- **Aceptación:** latencia WiFi→datos < 15 s; sobrevive a reinicio del relay; funciona con relojes desajustados 5 min.

### Fase 3 — Robustecer la conexión GATT (B2)

- [ ] Bandera `conectando`, timeout de conexión y de `requestMtu`, cierre del `BluetoothGatt` previo, ignorar callbacks obsoletos.
- **Aceptación:** 30 min con los teléfonos a 5–8 m y movimiento: sin acumulación de `status=133` y reconexión < 10 s tras alejarse y volver.

### Fase 4 — Diagnóstico visible para el usuario (B1, B6, B8)

Pantalla "Estado de la red" con: Bluetooth, permisos, Ubicación del sistema, ¿el teléfono puede anunciarse? (tier activo), escaneo activo, peers BLE vistos, clientes GATT listos, tipo de internet (WiFi/datos), por relay: último depósito/consulta OK/fallo y motivo.

### Fase 5 — Limpieza de identidad y arquitectura

- [ ] ID BLE corto único (B5) y mapeo al ID completo vía handshake (A7).
- [ ] `Eagerly` → `WhileSubscribed` real (o apagar BLE explícitamente al cerrar sesión) (B3).
- [ ] `LOW_LATENCY` mientras el radar esté visible (B4).
- [ ] Eliminar o conectar el P2P UDP muerto (A9/2.1); si se quiere P2P real, STUN/TURN.
- [ ] Verificar conectividad real (dominio de prueba) en vez de `NOT_SUSPENDED` (A8).

---

## 5. Alcance y limitaciones de este análisis

- Revisé en detalle: `ProximityManagerBle`, `ProximityManagerHibrido`, `GattMensajeriaManager` (servidor, conector y cliente), `MeshForegroundService`, `AndroidManifest`, flujo de permisos en `MapaScreen`, `InternetMeshBridge`, `NostrRelayTransport`, `NostrCrypto`, `AjustesRed`, y partes de `MessageRouterMesh`/`GestorResolverIdentidad`.
- **No revisé a fondo:** `GattFramer`, `GestorPerfilBle`/`GestorEventosBle`/`GestorPasesBle`, `HttpRelayTransport`, `WifiLanTransport` (más allá de NSD), ni la UI del radar.
- **No ejecuté la app.** Lo único probado de forma ejecutable es la firma BIP-340 (`bip340_check.py`). Las hipótesis BLE se confirman o descartan con la Fase 0.
- El estado de los relays públicos puede cambiar; `nostr.wine` como relay de pago está verificado en fuentes web, el resto de relays hay que probarlos directamente.
