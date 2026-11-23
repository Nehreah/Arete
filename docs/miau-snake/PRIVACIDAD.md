# Miau Snake — Política y Garantías de Privacidad

## Almacenamiento Local Soberano
- **Récords, Ajustes y Medallas:** Persistidos exclusivamente en el almacenamiento privado del dispositivo (`arete_miausnake`).
- **Retos y Duelos:** Almacenados en base de datos protegida local con caducidad automática de 7 días.
- **Sin Respaldo en la Nube:** El manifiesto de la aplicación configura `allowBackup="false"`, evitando que los datos se sincronicen con servicios de almacenamiento en la nube no controlados por el usuario.

## Intercambio de Retos
- **Contenido del Paquete:** Un paquete de reto (`MIAU1-…` / `MIAUR1:…`) contiene únicamente: identificador de mapa, modo de juego, semilla numérica del PRNG, puntuación y la secuencia de movimientos de la repetición.
- **Anonimato por Diseño:** No se incluye identificador de usuario, dirección MAC, ubicación geográfica ni telemetría del terminal.
- **Acción Explícita del Usuario:** Ningún reto o repetición se transmite de manera automática. Requiere la acción deliberada del usuario (seleccionar enviar por chat en la malla o exportar archivo/QR).

## Cero Rastreos y Cero Permisos Extra
- Sin analítica de uso, sin seguimiento de comportamiento y sin anuncios.
- No solicita permisos adicionales del sistema operativo para funcionar.
- Cero conexiones a servidores externos o servicios privativos de terceros.
