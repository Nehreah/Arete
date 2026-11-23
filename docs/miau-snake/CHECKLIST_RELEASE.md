# Miau Snake 1.0 — Checklist de publicación

## Código
- [ ] Paquete `levels/` de la Fase 4 en el repo y todo compila.
- [ ] `./gradlew testDebugUnitTest` en verde (primera ejecución real de las Fases 0–7).
- [ ] `python3 scripts/miausnake/phase7_check.py .` → 0 bloqueos.
- [ ] `./gradlew assembleRelease` con R8: probar menús, retos y partida (revisar `proguard-rules.pro`).
- [ ] Spike 16 vs 24 px **cerrado** y documentado (los mapas están a 20×28).
- [ ] Decidir si `MiauSnakePlaytestScreen`, `CellSizeSpike`, `PlaytestLevels`, `TransportSpike` pasan a `src/debug`.
- [ ] `MIAUSNAKE_CERCANIA = false` (solo se enciende con mediciones reales: ver `FASE_6_ESTADO`).

## Calidad
- [ ] `QA_MATRIZ.md` completa en ≥ 4 teléfonos.
- [ ] Pasada con TalkBack real (pruebas 27–29).
- [ ] Sin cierres ni ANR en la consola de la build de prueba durante 7 días.

## Documentos y versión
- [ ] `PRIVACIDAD.md` revisada; línea en la ficha de la tienda.
- [ ] `CHANGELOG.md` actualizado.
- [ ] `versionName = "1.0.0"` y `versionCode` incrementado.
- [ ] `git tag v1.0.0` y rama de mantenimiento.

## Distribución de prueba
- [ ] Build firmada a 5–10 personas (mezcla de modelos).
- [ ] Recoger feedback con `FEEDBACK_BETA.md`; corregir lo bloqueante.
