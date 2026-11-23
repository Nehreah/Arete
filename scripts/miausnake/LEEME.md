# Banco de pruebas de mapas de Miau Snake (Python)

Réplica fiel del motor y del bot BFS de Kotlin, usada para diseñar y validar los 20 mapas sin
compilador. No forma parte de la app.

- `sim.py`: motor (xorshift64*, `step`, `applyTick`) y bot BFS.
- `maps.py`: los 20 mapas dibujados en código (cuadrícula 20×28).
- `validate.py`: el validador (spawn, bordes, alcanzables, espacio, cuellos de botella).
- `par.py <n…>`: juega el bot en el mapa n con su semilla fija y guarda `res/NN.json`.
- `gen_json.py`: escribe `app/src/main/assets/miausnake/levels/*.json` desde `maps.py` y `res/`.

Flujo para cambiar un mapa: editar `maps.py` → `python3 par.py N` → `python3 gen_json.py`.
Si cambias un mapa, sube `levelVersion` en el JSON (los récords antiguos de ese mapa se descartan).
La semilla del mapa 9 es 7039 porque con 7009 el bot se queda dando vueltas (ver FASE_4_ESTADO.md).
