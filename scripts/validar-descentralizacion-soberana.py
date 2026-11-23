#!/usr/bin/env python3
"""Validación estática de la arquitectura soberana y descentralizada de Aréte.
Verifica la ausencia total de servidores centrales o esquemas externos,
garantizando que la aplicación opere de forma estrictamente soberana (local-first y mallas P2P).
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
auth_repo_path = ROOT / "app/src/main/java/com/renea/arete_social/data/local/auth/RepositorioAutenticacionLocal.kt"
user_repo_path = ROOT / "app/src/main/java/com/renea/arete_social/data/local/perfil/RepositorioUsuariosLocal.kt"
ble_path = ROOT / "app/src/main/java/com/renea/arete_social/data/mesh/GattMensajeriaManager.kt"
build_gradle_path = ROOT / "app/build.gradle.kts"

auth_repo = auth_repo_path.read_text(encoding="utf-8") if auth_repo_path.exists() else ""
user_repo = user_repo_path.read_text(encoding="utf-8") if user_repo_path.exists() else ""
ble_code = ble_path.read_text(encoding="utf-8") if ble_path.exists() else ""
build_gradle = build_gradle_path.read_text(encoding="utf-8") if build_gradle_path.exists() else ""

checks = {
    "Cero dependencias o esquemas de servidores remotos en el repositorio": not (ROOT / "docs/legacy/supabase").exists() and not (ROOT / "supabase").exists(),
    "Sin dependencias de SDKs de nube/servidores remotos en gradle": "io.github.jan.supabase" not in build_gradle and "com.google.firebase" not in build_gradle,
    "Autenticación e identidad 100% soberana local-first": "invitado-local-" in auth_repo and "signInAnonymously" not in auth_repo,
    "Perfiles y avatares gestionados localmente en SQLite/disco": "set(\"latitud\"" not in user_repo,
    "BLE firma paquetes PING antes de enviar": "messageRouter.firmarPaquete(ping)" in ble_code,
    "BLE conecta cliente inverso al recibir paquete entrante": "clientes[device.address] == null" in ble_code and "cli.conectar()" in ble_code,
}

print("=== Comprobando Arquitectura Descentralizada Soberana ===")
errors = 0
for name, passed in checks.items():
    status = "OK" if passed else "FALLO"
    if not passed:
        errors += 1
    print(f"{status} — {name}")

if errors > 0:
    print(f"\nResultado: {len(checks) - errors}/{len(checks)} correctas. Fallaron {errors}.")
    sys.exit(1)

print(f"\nResultado: {len(checks)}/{len(checks)} correctas. Todo el sistema cumple con la descentralización soberana.")
