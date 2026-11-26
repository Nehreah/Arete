# Aréte Federated Blind Relay Server

Servidor relay ciego de código abierto para la red Aréte. Permite la comunicación asíncrona entre pares que se encuentran bajo redes NAT/CGNAT, Wi-Fi o datos móviles.

## Características de Privacidad
- **Cero Conocimiento (Zero Knowledge):** El servidor nunca ve identidades de usuarios (nombres, IPs fijas, IDs de usuario) ni contenido de mensajes.
- **Buzones Ciegos (Blind Mailboxes):** Los buzones se identifican mediante etiquetas rotativas SHA-256 (`tag`) calculadas determinísticamente por emisor y receptor según la ventana temporal (24h).
- **Sobres Cifrados E2EE con Padding:** Todo payload viene cifrado de extremo a extremo y con relleno PKCS7 a múltiplos de 256 bytes para evitar análisis de tráfico por longitud.
- **Efímero:** Los sobres se purgan automáticamente tras ser leídos (`ack`) o al expirar su TTL (24h a 72h).

## Compilación y Ejecución Local
```bash
cd server/relay
go run main.go
```
Por defecto escucha en el puerto `8080`.

## Despliegue con Docker
```bash
docker build -t arete-relay .
docker run -d -p 8080:8080 --name arete-relay arete-relay
```
