#!/usr/bin/env python3
"""
Script de Auditoría de Compilación Reproducible para Aréte.

Verifica que el proceso de compilación produzca artefactos binarios (.apk)
bit a bit idénticos (mismo hash SHA-256) entre compilaciones sucesivas e
independientes, garantizando la soberanía e integridad del código fuente.
"""

import hashlib
import os
import subprocess
import sys
import time
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent.parent
GRADLEW = ROOT_DIR / "gradlew"
APK_RELEASE_DIR = ROOT_DIR / "app" / "build" / "outputs" / "apk" / "release"

def calcular_sha256(ruta_archivo: Path) -> str:
    sha = hashlib.sha256()
    with open(ruta_archivo, "rb") as f:
        while chunk := f.read(65536):
            sha.update(chunk)
    return sha.hexdigest()

def buscar_apk_release() -> Path:
    if not APK_RELEASE_DIR.exists():
        return None
    apks = list(APK_RELEASE_DIR.glob("*.apk"))
    if not apks:
        return None
    # Priorizar unsigned si existe, o el primer .apk de release
    unsigned = [a for a in apks if "unsigned" in a.name.lower()]
    return unsigned[0] if unsigned else apks[0]

def ejecutar_comando(comando: list, descripcion: str):
    print(f"▶ {descripcion}...")
    env = os.environ.copy()
    env["SOURCE_DATE_EPOCH"] = "1700000000"
    res = subprocess.run(comando, cwd=ROOT_DIR, env=env)
    if res.returncode != 0:
        print(f"❌ Error al ejecutar: {descripcion} (código {res.returncode})", file=sys.stderr)
        sys.exit(res.returncode)

def main():
    print("=" * 70)
    print("  AUDITORÍA DE COMPILACIÓN REPRODUCIBLE — ARÉTE")
    print("=" * 70)

    if not GRADLEW.exists():
        print("❌ No se encontró gradlew en la raíz del proyecto", file=sys.stderr)
        sys.exit(1)

    # 1. Primera compilación
    print("\n[Paso 1/4] Compilación limpia #1...")
    ejecutar_comando([str(GRADLEW), "clean", "assembleRelease", "-x", "lint"], "Primera compilación release")
    apk_1 = buscar_apk_release()
    if not apk_1 or not apk_1.exists():
        print("❌ No se encontró el APK generado en la primera compilación", file=sys.stderr)
        sys.exit(1)

    hash_1 = calcular_sha256(apk_1)
    tamano_1 = apk_1.stat().st_size
    nombre_1 = apk_1.name
    print(f"  ✔ Artefacto 1: {nombre_1} ({tamano_1:,} bytes)")
    print(f"  ✔ SHA-256 #1:  {hash_1}")

    # Guardar copia temporal
    copia_temporal = ROOT_DIR / "app" / "build" / f"apk_build_1_{hash_1[:8]}.apk"
    copia_temporal.write_bytes(apk_1.read_bytes())

    # Pausa intencional para verificar que la marca de tiempo (timestamp) no altere el hash
    print("\n[Paso 2/4] Esperando 3 segundos para asegurar cambio de timestamp del reloj...")
    time.sleep(3)

    # 2. Segunda compilación tras limpieza completa
    print("\n[Paso 3/4] Compilación limpia #2...")
    ejecutar_comando([str(GRADLEW), "clean", "assembleRelease", "-x", "lint"], "Segunda compilación release")
    apk_2 = buscar_apk_release()
    if not apk_2 or not apk_2.exists():
        print("❌ No se encontró el APK generado en la segunda compilación", file=sys.stderr)
        sys.exit(1)

    hash_2 = calcular_sha256(apk_2)
    tamano_2 = apk_2.stat().st_size
    nombre_2 = apk_2.name
    print(f"  ✔ Artefacto 2: {nombre_2} ({tamano_2:,} bytes)")
    print(f"  ✔ SHA-256 #2:  {hash_2}")

    # Limpiar archivo temporal
    if copia_temporal.exists():
        copia_temporal.unlink()

    # 3. Comparación
    print("\n[Paso 4/4] Verificación de reproducibilidad bit a bit...")
    print("=" * 70)
    if hash_1 == hash_2:
        print("✅ COMPILACIÓN 100% REPRODUCIBLE (VERIFICADA CON ÉXITO)")
        print(f"   Hash SHA-256 verificado: {hash_1}")
        print("   Los binarios generados en momentos distintos son idénticos bit a bit.")
        print("=" * 70)
        sys.exit(0)
    else:
        print("❌ LA COMPILACIÓN NO ES REPRODUCIBLE")
        print(f"   Hash #1: {hash_1}")
        print(f"   Hash #2: {hash_2}")
        print("=" * 70)
        sys.exit(1)

if __name__ == "__main__":
    main()
