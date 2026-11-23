## Descripción del cambio
<!-- Resume brevemente qué problema resuelve este PR o qué funcionalidad introduce. -->

## Tipo de cambio
- [ ] Corrección de bug
- [ ] Nueva característica
- [ ] Refactorización / Mantenimiento
- [ ] Actualización de seguridad

## Checklist de Seguridad y Calidad
- [ ] ¿Toca criptografía (E2EE, firmas ECDSA, Keystore)? **Se requiere test unitario.**
- [ ] ¿Toca red mesh BLE o Wi-Fi LAN? **Verificar que preserve la soberanía local-first sin telemetría.**
- [ ] He ejecutado los scripts de validación estática (`scripts/validar-*.py`) localmente y todos pasan.
- [ ] No se incluyen claves API, contraseñas ni datos privados.
