# Arquitectura Técnica — Aréte

Documentación técnica consolidada de la arquitectura de **Aréte**, una plataforma libre y soberana de mensajería y red social táctica, diseñada para operar **100% de forma local, peer-to-peer y sin servidores centrales** bajo licencia GPL-3.0.

---

## 1. Principios Fundamentales

1. **Arquitectura 100% Local-First y Descentralizada:**
   - La aplicación no requiere ni se conecta a ningún servidor central en la nube. Todas las funciones esenciales (descubrimiento de personas, intercambio de mensajes, llamadas de audio/video por Wi-Fi LAN / P2P, radar táctico, salas, comunidades y mascota Michi) operan autónomamente en el dispositivo y a través de redes locales o malla mesh.
2. **Clean Architecture e Inversión de Dependencias (SOLID):**
   - El **Dominio** (`domain/`) es estrictamente puro: contiene las entidades de negocio, contratos de repositorio y utilidades comunes, con **cero dependencias** directas hacia las capas de infraestructura o frameworks de datos (`data/`).
   - La **Capa de Datos** (`data/`) implementa los contratos del dominio mediante SQLite/Room, Android Keystore, Bluetooth Low Energy y Wi-Fi LAN.
   - La **Presentación** (`feature/` y `gui/`) interactúa con el dominio a través de ViewModels y componentes declarativos modulares en Jetpack Compose.
3. **Seguridad y Privacidad Extremo a Extremo (E2EE):**
   - Cifrado E2EE v4 en conversaciones privadas mediante ECDH P-256 + HKDF-SHA256 + AES-256-GCM con AAD contextual vinculado al canal. Ningún intermediario ni nodo de relevo en la malla puede acceder al contenido.
4. **Cero Dependencias Privativas (F-Droid Ready):**
   - Sin Google Play Services, sin microG requerido, sin Firebase ni telemetría.
   - Posicionamiento geográfico mediante `LocationManager` nativo de Android y mapas locales con OpenStreetMap / `osmdroid`.
5. **Ortogonalidad entre Ciclo Día/Noche y Tema Visual:**
   - `ModoTema` (CLARO / OSCURO) controla la paleta visual de Material 3.
   - `CicloDia` (DIA / NOCHE) responde al reloj ambiental local (07:00 a 19:00 día, 19:00 a 07:00 noche), modulando los estilos del mapa y el comportamiento de la mascota.

---

## 2. Mapa Estructural de Módulos y Capas

```
com.renea.arete_social/
├── ContenedorApp.kt               # Composición manual de dependencias (DI sin frameworks invasivos)
├── data/                          # Implementaciones concretas de infraestructura y persistencia
│   ├── community/                 # Gestión de libros mayores (CommunityLedger) y claves de época
│   ├── crypto/                    # CifradorE2eeAndroid, GestorIdentidadEcdsa, PinesIdentidad
│   ├── identity/                  # GestorResolverIdentidad, IdentityManager, DeviceKeyManager
│   ├── local/                     # Persistencia local soberana (Room y SharedPreferences)
│   │   ├── room/AppLocalDatabase  # Entidades y DAOs locales de Room
│   │   ├── perfil/                # ResolverPerfilesLocal, CacheUsuariosLocal, Mappers
│   │   ├── salas/                 # RepositorioSalasLocal
│   │   ├── lugares/               # RepositorioLugaresLocal
│   │   ├── regiones/              # RepositorioRegionesLocal
│   │   ├── amistades/             # RepositorioAmistadesLocal
│   │   ├── encuentros/            # RepositorioEncuentrosLocal
│   │   └── moderacion/            # RepositorioModeracionLocal
│   ├── mesh/                      # Enrutamiento de paquetes y protocolos BLE/GATT
│   │   ├── GattFramer.kt          # Framing y reensamblado seguro de fragmentos
│   │   ├── GattMensajeriaManager  # Gestor de conexiones y servidor GATT
│   │   ├── MessageRouterMesh.kt   # Deduplicación y salto de paquetes (TTL=7, TOFU)
│   │   └── RepositorioMensajesHibrido.kt
│   ├── network/                   # Wi-Fi LAN / sockets locales y OSRM
│   │   ├── wifi/WifiLanTransport  # Descubrimiento NSD/mDNS y sockets LAN acotados
│   │   └── ruta/CalculadorRutaOsrm# Cálculo de rutas peatonales con OpenStreetMap
│   └── proximity/                 # Detección BLE y GPS nativo
│       ├── ble/ProximityManagerBle# Escaneo y publicidad BLE
│       └── gps/ProximityManagerGps# LocationManager nativo (sin Google Play)
├── domain/                        # NÚCLEO PURO DE DOMINIO (Cero dependencias de data/ o Android)
│   ├── crypto/CifradorE2ee.kt     # Interfaz de cifrado
│   ├── mesh/                      # Contratos de MessageRouter y transporte
│   ├── model/                     # Modelos puros (Usuario, Chat, Evento, Mensaje, PublicacionPerfil)
│   ├── repository/                # Interfaces puras (ResolverIdentidad, ResolverPerfiles, RepositorioUsuarios)
│   └── util/                      # Utilidades transversales (HexExtensions: toHex / hexToByteArray)
├── feature/                       # Lógica de aplicación y ViewModels (Arquitectura MVI/MVVM)
│   ├── auth/                      # Identidad local, GestorAmistades, PerfilViewModel
│   ├── cartas/                    # Intercambio de correspondencia efímera
│   ├── comunidades/               # Gaceta y administración comunitaria
│   ├── identidad/                 # Gestión de credenciales criptográficas y backup offline
│   ├── libreria/                  # Biblioteca de obras y lecturas
│   ├── llamadas/                  # GestorLlamadasYAudio y TacticalNotificationManager
│   ├── mensajeria/                # ChatViewModel y SalasViewModel
│   ├── miausnake/                 # Minijuego determinista con verificación P2P
│   └── tamagotchi/                # Simulación de Michi y mundos compartidos
└── gui/                           # INTERFAZ DE USUARIO MODULAR (Jetpack Compose)
    ├── auth/perfil/               # Componentes modulares de Perfil:
    │   ├── PerfilScreen.kt        # Orquestación de pantalla de perfil
    │   ├── PublicacionesPerfilComponents.kt # Tarjetas retro y shaders Matrix CRT
    │   ├── DialogoAjustesPerfil.kt# Diálogo de ajustes y selectores de visibilidad
    │   ├── TarjetasSocialPerfil.kt# Tarjetas de amigos y solicitudes
    │   ├── CabeceraSocialPerfil.kt# Portada, avatar e intereses
    │   └── TacticalPerfilComponents.kt # Corchetes tácticos, barras de pestañas y ECG
    ├── comunidad/                 # Pantallas de comunidades y gaceta
    ├── mapa/                      # Radar en vivo, mapa OSMDroid y overlays tácticos
    ├── mensajeria/                # Componentes modulares de Chat:
    │   ├── ChatScreen.kt          # Pantalla principal de conversación
    │   ├── BurbujaMensaje.kt      # Burbujas de mensaje, estados E2EE y PTT voz
    │   ├── BarraEntradaMensaje.kt # Barra de entrada de texto y anexos
    │   └── ItemsListaChat.kt      # Ítems de chats activos, salas y personas cercanas
    ├── tamagotchi/                # Escenas pixel art de la mascota virtual
    └── theme/                     # Tokens M3, RadarPalette, CicloDia
```

---

## 3. Protocolos de Red y Malla Mesh

### 3.1 Bluetooth Low Energy (BLE) y Framing GATT
- **Anuncios y Descubrimiento:**
  - `ProximityManagerBle` gestiona el ciclo de vida del escáner y anunciador BLE, adaptando la longitud del nombre en el paquete de anuncio (`ScanRecord`) para prevenir errores `ADVERTISE_FAILED_DATA_TOO_LARGE`.
- **GattFramer:**
  - Envío confiable sobre MTU acotado (típicamente 23 a 517 bytes).
  - Estructura de trama canónica: `Header (7 bytes) = [msgId: Long (3B) | total: Short (2B) | index: Short (2B)] + [Payload]`.
  - Reensamblado con sanitización estricta (`filterNotNull`), validación de cuota de memoria (`MAX_BYTES_MENSAJE = 64KB`) y limpieza automática de sesiones huérfanas tras 12 segundos.

### 3.2 Wi-Fi LAN y Transporte UDP/TCP
- **WifiLanTransport:**
  - Descubrimiento local mediante Android Network Service Discovery (NSD / mDNS).
  - Concurrencia controlada con `ThreadPoolExecutor` acotado a 16 hilos de trabajo para evitar agotamiento de recursos.
  - Sockets de lectura protegidos con `soTimeout = 15000ms`.
  - Sincronización del puerto de enlace mediante `CountDownLatch` sin esperas activas (`Thread.sleep`).

### 3.3 Internet (Wi-Fi y Datos Móviles) y Nodos Pasarela (Gateway / Data Mule)
- **InternetMeshBridge:**
  - Supervisión reactiva mediante `ConnectivityManager.NetworkCallback` diferenciando entre transporte Wi-Fi y Celular (Datos Móviles).
  - Integra un `PeerConnection` virtual en `MessageRouterMesh` que permite retransmitir paquetes de la red mallada hacia buzones relay remotos.
  - **Sincronización Asimétrica:** Celulares con internet actúan como puentes de enlace (*Data Mules*), inyectando paquetes hacia la nube y derramando gacetas o mensajes recibidos hacia teléfonos cercanos que solo disponen de Bluetooth LE.

### 3.4 Diferenciación de Ámbitos de Red
- **Radar Hiperlocal ($\le 2\text{ km}$):** Presencia y eventos acotados estrictamente a un radio de 2.000 metros mediante fórmula de Haversine y celdas geográficas. Coordenadas difusas en modo `APROXIMADA`.
- **Amigos (Global / Sin límite de distancia):** Mensajería E2EE directa enrutada por identidad criptográfica (`IK`), alternando fluidamente entre Internet y Bluetooth sin interrumpir la sesión.
- **Comunidades y Gacetas a Distancia:** Publicaciones en libros mayores distribuidos (`communityId`) que viajan por internet y se retransmiten oportunistamente por la malla local.
- Para más detalles técnicos, consultar la [Guía Integral de Comunicaciones (COMUNICACION_MESH.md)](COMUNICACION_MESH.md).

---

## 4. Criptografía de Extremo a Extremo (E2EE)

### 4.1 Mensajes Directos (DM)
- **Par de Claves Asimétricas:** Generación de claves en Curva Elíptica `secp256r1` (P-256) mediante JCA nativo con almacenamiento en Android Keystore (`EncryptedSharedPreferences`).
- **Intercambio de Claves y Derivación:** Acuerdo asimétrico mediante `ECDH` (Elliptic Curve Diffie-Hellman) y derivación de clave simétrica de 256 bits mediante `HKDF-SHA256`.
- **Cifrado Simétrico:** `AES-256-GCM` (Galois/Counter Mode) con etiqueta de autenticación de 128 bits e IV aleatorio de 12 bytes por mensaje.
- **AAD Contextual:** `remitente|destinatario|chatId` — vincula el texto cifrado al canal específico; moverlo a otro chat invalida inmediatamente el tag GCM.
- **Indicador Visual:** Etiquetas `🔒 E2EE` / `⚠️ Sin clave` en la interfaz de usuario.
- **Uso de Base64 Estándar:** Empleo exclusivo de `java.util.Base64` (API 26+), garantizando portabilidad y testabilidad completa en JVM pura.

### 4.2 Autenticidad en la Malla Local
- **Paquetes UDP Firmados:** Cada paquete lleva una firma ECDSA secp256k1 sobre `(payload‖timestamp‖nonce)`. La firma se valida antes de registrar la dirección IP del emisor; paquetes inválidos se descartan silenciosamente sin contaminar la tabla TOFU.
- **TCP LAN / Wi-Fi Directo:** Handshake ECDSA al abrir la sesión con ventana anti-replay de 60 s. Se rechaza cualquier `nonce_ts` ya visto en esa ventana.
- **Identificadores de Pares:** Comparación exacta de 32 caracteres hexadecimales mediante `HexExtensions`.
- **Protección TOFU:** Los estados verificados por PIN no se degradan ante intentos de suplantación en la malla.

---

## 5. Persistencia y Almacenamiento Local

- **Room (`AppLocalDatabase`):** Base de datos relacional local que almacena mensajes, conversaciones, registros de comunidades (`CommunityLedger`), eventos locales y cachés de pares.
- **`ModuloLocal`:** Contenedor unificado de repositorios locales que provee acceso reactivo (`StateFlow` / `Flow`) y concurrente a todas las entidades.
- **Gestor de Medios (`AlmacenMediaPerfil`):** Almacenamiento local aislado para avatares y fotos con sanitización y compresión previa para no saturar el almacenamiento del dispositivo.
- **Señalización P2P para Llamadas:** La señalización de llamadas (oferta, respuesta y candidatos ICE) se transmite mediante paquetes serializables `SenalLlamada` sobre la malla BLE / LAN, prescindiendo totalmente de servidores intermedios de señalización.

---

## 6. Pruebas Unitarias y Calidad

El proyecto cuenta con una amplia suite de pruebas unitarias automatizadas que garantizan la estabilidad de los subsistemas críticos:
- `ResolutorCicloDiaTest`: Verificación matemática de los límites horarios y ciclo día/noche.
- `GattFramerTest`: Reensamblado en orden, desorden, duplicados y descarte de corrupción.
- `GestorSesionLocalTemaTest`: Persistencia local de preferencias de tema.
- `CifradorE2eeTest`: Round-trip de cifrado/descifrado, validación de integridad y rechazo de claves no autorizadas.
- `MessageRouterMeshTest`: Tolerancia a fallos, TTL y protección contra ataques TOFU.
- `CommunityMeshSyncManagerTest`: Consistencia y validación criptográfica de operaciones distribuidas en el ledger.
- `BfsBotTest`, `EngineTest`, `ReplayTest`, `ResimulateTest`: Determinismo del motor del minijuego Miau Snake.
