# Modelo de Amenazas y Seguridad — Aréte

Este documento define la superficie de ataque, los vectores de amenaza y las defensas criptográficas y arquitectónicas implementadas en **Aréte**. El sistema opera bajo un paradigma **100% Local-First y Peer-to-Peer**, sin servidores centrales, sin telemetría y sin almacenamiento en la nube.

---

## 1. Capas del Sistema y Mecanismos de Seguridad

### Capa 1 — Cifrado Extremo a Extremo (E2EE) en Mensajería Privada

**Formato de mensaje:** `ENC:v4:<iv_b64>:<ciphertext_b64>`

**Mecanismo:**
- Negociación de secretos compartidos mediante intercambio Diffie-Hellman en curva elíptica (**ECDH P-256 / X25519**).
- Derivación de clave simétrica de 256 bits mediante **HKDF-SHA256** con contexto versionado.
- Cifrado autenticado mediante **AES-256-GCM** con vector de inicialización (IV) aleatorio de 12 bytes generado con `SecureRandom`.
- **Datos Asociados Autenticados (AAD):** `remitente|destinatario|chatId` — vincula criptográficamente el texto cifrado al canal específico, impidiendo la inyección o reutilización de paquetes entre distintas conversaciones.
- Almacenamiento seguro de claves privadas locales en el **Android Keystore** y almacenamiento protegido de la app.

**Amenazas Mitigadas:**
| Amenaza | Vector de Ataque | Mitigación Implementada |
|---|---|---|
| **Intercepción pasiva** | Nodos de relevo en la malla o atacantes en el espectro de radio leyendo el tráfico. | AES-256-GCM indescifrable sin la clave privada del destinatario. |
| **Ataque de repetición / Replay entre chats** | Un atacante copia un paquete cifrado de un chat y lo inyecta en otro. | Fallo del tag GCM debido a que el AAD incluye el `chatId` unívoco. |
| **Manipulación de contenido** | Modificación de bits en tránsito a través de saltos intermedios. | Rechazo inmediato del paquete por fallo de autenticación GCM. |

---

## 2. Capa 2 — Integridad y Autenticidad en la Malla Mesh (BLE / Wi-Fi LAN)

**Mecanismo:**
- **Paquetes Mesh Firmados:** Cada paquete (`PaqueteMesh`) transmitido por Bluetooth Low Energy (GATT) o Wi-Fi LAN lleva una firma digital **ECDSA secp256k1** calculada sobre el identificador, canal, remitente, destinatario, timestamp y payload.
- **Ventana Anti-Replay y Deduplicación:** Filtro en memoria y tabla de nonces en SQLite con ventana deslizante de 24 horas. Los paquetes repetidos o con marcas temporales desfasadas se descartan silenciosamente.
- **Límite de Saltos (TTL):** Campo TTL decrementado en cada retransmisión para evitar tormentas de difusión en la malla.
- **Framing Seguro (`GattFramer`):** Control de flujo en fragmentación BLE con longitud máxima estricta para evitar desbordamiento de búfer o ataques de denegación de servicio (DoS).

**Amenazas Mitigadas:**
| Amenaza | Vector de Ataque | Mitigación Implementada |
|---|---|---|
| **Suplantación de nodo** | Emisión de paquetes falsificando el ID de otro usuario. | Validación de firma ECDSA previa al procesamiento o enrutamiento. |
| **Tormenta de broadcast (DoS)** | Bucles infinitos de paquetes en la malla. | Deduplicación por hash de paquete y decremento de TTL (máximo 7 saltos). |
| **Desbordamiento de memoria** | Envío de fragmentos BLE corruptos o infinitos. | Búfer con límite de tamaño estricto en `GattFramer` y caducidad de reensamblado. |

---

## 3. Capa 3 — Identidad Soberana y Modelo de Confianza (TOFU)

**Mecanismo:**
- **Cero Dependencia de Terceros:** No existen registros centrales, números de teléfono ni correos electrónicos. La identidad de cada nodo es su propio par de claves criptográficas asimétricas.
- **Trust On First Use (TOFU):** El primer intercambio de claves públicas entre dos nodos se asocia a su huella digital (`peerId`). Si un nodo intenta reemplazar silenciosamente la clave de una identidad ya conocida sin prueba criptográfica de rotación válida, la actualización es rechazada para prevenir ataques de intermediario (*Man-in-the-Middle*).
- **Huella Visual y QR:** Verificación presencial o fuera de banda mediante código QR de huella criptográfica.

**Amenazas Mitigadas:**
| Amenaza | Vector de Ataque | Mitigación Implementada |
|---|---|---|
| **Hombre en el medio (MitM)** | Un nodo intermedio sustituye la clave pública durante el descubrimiento inicial. | Detección de colisiones TOFU y verificación opcional de huella por QR. |
| **Secuestro de cuenta** | Robo de credenciales en un servidor central. | Inexistente: no hay servidor ni base de datos central que pueda ser vulnerada. |

---

## 4. Capa 4 — Privacidad Geográfica y Discreción en Radar

**Mecanismo:**
- **Tres Modos de Visibilidad:**
  - `VISIBLE`: Comparte ubicación aproximada a pares autorizados.
  - `APROXIMADA`: Difumina las coordenadas geográficas introduciendo ruido aleatorio (~50 a 100 metros) para evitar triangulación milimétrica.
  - `INVISIBLE`: Apaga completamente la difusión de balizas de ubicación.
- **Mapas Soberanos:** Uso exclusivo de OpenStreetMap y `osmdroid` con teselas locales y cliente de rutas OSRM, sin rastreo por Google Maps ni envío de geolocalización a proveedores externos.

---

## 5. Capa 5 — Almacenamiento Local y Sanitización

**Mecanismo:**
- Almacenamiento 100% en el dispositivo (`AppLocalDatabase` / Room y SQLite protegido).
- `allowBackup="false"` declarado en el `AndroidManifest.xml` para impedir extracción involuntaria en copias de seguridad en la nube de Google Drive / Android Backup.
- Caducidad automática de estados y bitácoras efímeras a los 7 días para minimizar la huella de datos persistida.
- Sanitización de contenido multimedia (límites de tamaño en Base64 e imágenes escaladas para prevenir agotamiento de memoria).
