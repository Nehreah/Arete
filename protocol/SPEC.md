# Especificación del Protocolo Aréte (v1)

**Estado:** Especificación canónica v1  
**Stack de referencia:** Kotlin (Android) con vectores neutrales en JSON  
**Algoritmos criptográficos:** ECDSA/ECDH sobre curva NIST P-256 (secp256r1), SHA-256, HKDF-SHA256, AES-256-GCM.

---

## 1. Principio Fundamental de Serialización y Firma

> **Se firman los bytes exactos que viajan por el transporte. Nunca se deserializa para volver a serializar al verificar.**

El identificador único de una operación (`opId`) es:
```
opId = SHA-256(bytes_del_sobre || bytes_de_la_firma)
```

Separación de dominio para la firma digital de la operación:
```
firma = ECDSA_P256_Sign(DK-s_privada, "arete/op/v1" || bytes_del_sobre)
```

---

## 2. Formato Canónico del Sobre (`OperationEnvelope`)

El sobre binario viaja con campos con prefijo de longitud fija (Big-Endian):

| Campo | Tipo / Tamaño | Descripción |
|---|---|---|
| `version` | `uint16` (2 bytes) | Versión del protocolo (`1`) |
| `operationType` | `uint16` (2 bytes) | Código del tipo de operación (ver sección 3) |
| `communityId` | 32 bytes | Hash SHA-256 de la operación génesis de la comunidad |
| `authorIk` | 65 bytes (P-256 sin comprimir `04 || X || Y`) | Clave pública de identidad del autor |
| `deviceId` | 16 bytes (UUID) | Identificador del dispositivo del autor |
| `deviceEpoch` | `uint32` (4 bytes) | Época del dispositivo (se incrementa al restaurar) |
| `deviceSeq` | `uint64` (8 bytes) | Secuencia monótona por dispositivo (1, 2, 3...) |
| `prevOpHash` | 32 bytes | Hash de la operación anterior del mismo dispositivo (`0x00...00` en la primera) |
| `ownerHeadSeq` | `uint64` (8 bytes) | Altura conocida de la columna del dueño al firmar |
| `ownerHeadHash` | 32 bytes | Hash conocido de la cabeza de la columna del dueño al firmar |
| `epochId` | 32 bytes | Hash de la operación `EpochRotate` vigente (`0x00...00` si no aplica) |
| `timestamp` | `int64` (8 bytes) | Epoch Unix millis (solo valor indicativo causal/sospecha, no para ordenamiento absoluto) |
| `bodyLength` | `uint32` (4 bytes) | Longitud de los bytes del cuerpo opaco (máximo 8.192 bytes = 8 KB) |
| `bodyBytes` | `bytes` variable | Bytes opacos específicos del tipo de operación |
| `signatureLength` | `uint16` (2 bytes) | Longitud de la firma ASN.1/DER o R\|\|S (máximo 72 bytes) |
| `signatureBytes` | `bytes` variable | Firma de la clave de dispositivo `DK-s` sobre `"arete/op/v1" || bytes_del_sobre` |

---

## 3. Tipos de Operación

### 3.1 Columna del Dueño (Autoridad Primaria, Escritor Único)
1. `GENESIS` (`0x0001`): Creación de la comunidad, reglas iniciales y política de historial.
2. `RULE_CHANGE` (`0x0002`): Actualización de reglas de la comunidad.
3. `LEADER_DESIGNATE` (`0x0003`): Designación de un nuevo Líder.
4. `LEADER_REVOKE` (`0x0004`): Revocación del Líder vigente con `cutoffOwnerSeq`.
5. `REVOKE_SUBTREE` (`0x0005`): Invalidación retroactiva de un subárbol de delegaciones.
6. `OWNERSHIP_TRANSFER` (`0x0006`): Cesión voluntaria de propiedad firmada por la IK.
7. `HEIR_DESIGNATE` (`0x0007`): Designación de clave pública de heredero y plazo de espera.
8. `HEIR_CANCEL` (`0x0008`): Anulación de designación de heredero.
9. `CHECKPOINT` (`0x0009`): Estado consolidado de miembros y roles anclado a un `ownerSeq`.
10. `DISSOLVE` (`0x000A`): Disolución de la comunidad.

### 3.2 Operaciones Delegadas (Líder / Administradores)
11. `ADMIN_DESIGNATE` (`0x0010`): Designación de administrador.
12. `MODERATOR_DESIGNATE` (`0x0011`): Designación de moderador.
13. `ROLE_REVOKE` (`0x0012`): Revocación de un rol delegado con `cutoffOwnerSeq`.
14. `JOIN_ACCEPT` (`0x0013`): Aprobación de una solicitud de ingreso con share de época inicial.
15. `JOIN_REJECT` (`0x0014`): Rechazo formal de solicitud de ingreso.
16. `EXPEL` (`0x0015`): Expulsión de un miembro (obliga a `EPOCH_ROTATE`).
17. `EPOCH_ROTATE` (`0x0016`): Creación de una nueva clave de época $K_e$.
18. `APPEAL_DECISION` (`0x0017`): Respuesta del dueño a una apelación.
19. `HEIR_CLAIM` (`0x0018`): Reclamo de propiedad por heredero (requiere contrafirma del líder).

### 3.3 Operaciones de Miembro
20. `JOIN_REQUEST` (`0x0020`): Solicitud de ingreso (sellada hacia `AdmissionKey`).
21. `LEAVE` (`0x0021`): Salida voluntaria (obliga a `EPOCH_ROTATE`).
22. `APPEAL_REQUEST` (`0x0022`): Petición de apelación firmada hacia el dueño.
23. `CONTENT_POST` (`0x0023`): Publicación o mensaje en el registro de la comunidad.
24. `REACTION_OR_ACK` (`0x0024`): Confirmación o reacción causal.

---

## 4. Reglas de Validación de Autoridad y Conflictos

1. **Jerarquía Estricta:**  
   `Dueño (0) > Líder (1) > Administrador (2) > Moderador (3) > Miembro (4)`.  
   Un rol de rango inferior o igual no puede revocar ni modificar a uno superior.
2. **Evaluación Contra `ownerHeadSeq`:**  
   Una operación delegada se valida contra el estado de la comunidad en la altura `ownerHeadSeq` referenciada por su autor.
3. **Corte de Revocación (`cutoffOwnerSeq`):**  
   Si un delegado fue revocado con corte en `cutoffOwnerSeq`, toda operación suya con `ownerHeadSeq >= cutoffOwnerSeq` es **inválida**. Las operaciones anteriores con `ownerHeadSeq < cutoffOwnerSeq` se consideran legítimas en su momento.
4. **Operaciones Sospechosas (Fechadas hacia atrás):**  
   Si `timestamp` de la operación es posterior a la revocación pero `ownerHeadSeq` referencia una cabeza anterior al corte, la operación se marca como `SOSPECHOSA` y no surte efectos críticos sin aprobación explícita.
5. **Estados de Operación:**  
   - `RECIBIDA`: Firmada, aún no validada.
   - `PROVISIONAL`: Válida con la información local disponible, susceptible a cambios causales.
   - `CONFIRMADA`: Validada contra la columna del dueño hasta una cabeza consolidada.
   - `CONFLICTO / SOSPECHOSA`: Requiere intervención o resolución por precedencia.

---

## 5. Claves de Época y Cifrado de Grupo

1. **Clave de Época ($K_e$):** 32 bytes de entropía segura generados localmente.
2. **Derivaciones:**
   - Mensajes: $K_{msg} = \text{HKDF-SHA256}(K_e, \text{"arete/epoch/msg"}, 32)$
   - Archivos: $K_{file} = \text{HKDF-SHA256}(K_e, \text{"arete/epoch/file"}, 32)$
   - Clave de Admisión: $K_{adm} = \text{HKDF-SHA256}(K_e, \text{"arete/epoch/admission"}, 32)$
3. **Cifrado:** AES-256-GCM con nonce aleatorio de 96 bits. AAD = `communityId || epochId || messageId`.
4. **Distribución en `EpochKeyShare`:**
   Para cada miembro activo $M_i$:
   ```
   Ephemeral_Keypair = GenerarP256()
   SharedSecret = ECDH(Ephemeral_Keypair_Priv, DK-a_Miembro_Pub)
   KeyWrap = HKDF-SHA256(SharedSecret, "arete/share/v1", 32)
   EncryptedKe = AES_GCM_Encrypt(KeyWrap, Ke, AAD = communityId || epochId || Miembro_IK)
   ```
5. **Rotación Obligatoria:**
   Ante `EXPEL`, `LEAVE` o revocación con sospecha de compromiso, el emisor genera $K_{e+1}$ y emite `EpochKeyShare` únicamente a los miembros restantes.
