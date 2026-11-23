# Miau Snake 1.0 — Matriz de QA en dispositivos

Rellena una columna por teléfono. Marca ✔ / ✘ y anota el modelo, la versión de Android y qué falló.
Gate: **todo ✔** en al menos 4 teléfonos (gama baja, media, alta y uno con API 26–28).

| # | Prueba | Esperado | T1 | T2 | T3 | T4 |
|---|---|---|---|---|---|---|
| **Pantalla y densidad** |||||||
| 1 | Tablero en pantalla pequeña (≤ 5") y grande (≥ 6,7") | Celda ≥ tamaño mínimo, nada recortado bajo barras/notch | | | | |
| 2 | Densidad alta (xxxhdpi) y baja (hdpi) | Mapas y miniaturas nítidos y proporcionados | | | | |
| 3 | Tamaño de fuente del sistema 1,3× y 2,0× | Menús legibles y con scroll; botones sin texto cortado | | | | |
| 4 | Rotación y modo multiventana | No se pierde la partida; se pausa | | | | |
| 5 | Plegable / tableta (si hay) | Tablero centrado, controles al alcance | | | | |
| **Rendimiento y ciclo de vida** |||||||
| 6 | Partida de 5 min en Infinito | ≥ 55 fps medianos; sin tirones al comer | | | | |
| 7 | Home / app reciente durante la partida | Queda en **Pausa**; «Continuar» da cuenta atrás de 3 s | | | | |
| 8 | Llamada o notificación a pantalla completa | Pausa y vuelve sin perder estado | | | | |
| 9 | Apagar pantalla y encender | Pausa; sin ticks acumulados | | | | |
| 10 | «Atrás» en juego / menús / retos / mapas | Pausa → confirma → sale; nunca cierra Michi de golpe | | | | |
| 11 | Matar el proceso en el menú y reabrir | Récords, medallas y ajustes intactos | | | | |
| 12 | Batería 15 min de juego | Consumo razonable; no calienta | | | | |
| **Juego** |||||||
| 13 | Los 20 mapas: abrir y jugar los 4 packs | Cada pack se juega entero | | | | |
| 14 | Túneles y bordes WRAP | Coinciden con la miniatura | | | | |
| 15 | Swipe encadenado rápido | Sin giros perdidos ni inversión suicida | | | | |
| 16 | Cruceta y modo zurdo | Pausa y cruceta intercambian lado | | | | |
| 17 | Ritmo relajado | No guarda récord ni medalla | | | | |
| **Integración con Michi** |||||||
| 18 | JUGAR → Automático | Idéntico a antes | | | | |
| 19 | Terminar Snake y cerrar | Una sola recompensa; cooldown correcto | | | | |
| 20 | Doble toque / cancelar / abandonar | Sin doble recompensa | | | | |
| 21 | Modo avión | Snake funciona sin conexión | | | | |
| **Retos** |||||||
| 22 | Crear reto, compartir por texto/archivo | Llega y se verifica en otro teléfono | | | | |
| 23 | QR (mostrar y escanear) | Lee a la primera; sin pedir permiso de cámara | | | | |
| 24 | Texto corrupto / truncado | «No se pudo leer», sin cierre | | | | |
| 25 | Reto diario (cambio de día UTC) | Cambia a las 00:00 UTC | | | | |
| 26 | Re-simular repetición larga en gama baja | La UI no se congela | | | | |
| **Accesibilidad** |||||||
| 27 | TalkBack: recorrer menús | Títulos como encabezado; botones con nombre y «desactivado» | | | | |
| 28 | TalkBack: jugar con cruceta | Cruceta usable; el tablero anuncia puntos y largo | | | | |
| 29 | TalkBack: pausa y resultado | Se leen solos al aparecer | | | | |
| 30 | Contraste alto | Gato, comida, suelo y paredes distinguibles | | | | |
| 31 | Daltonismo (simulador) | Nada depende solo del color | | | | |
| 32 | Objetivos táctiles | Todo pulsable ≥ 48 dp | | | | |
| 33 | Vibración apagada | No vibra | | | | |
| **Errores** |||||||
| 34 | Sin espacio / mapas dañados (simular) | Aviso con «Volver»; jamás corre Automático en silencio | | | | |
| 35 | Idioma del sistema distinto | Textos en español sin cortes | | | | |
