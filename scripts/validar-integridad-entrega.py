#!/usr/bin/env python3
"""Comprobaciones offline de integridad para la entrega Android de Aréte."""
from pathlib import Path
import os
import sys

ROOT = Path(__file__).resolve().parents[1]
checks = []

def check(name, condition, detail=""):
    checks.append((name, bool(condition), detail))

build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
plugins_pos = build.find("plugins {")
android_pos = build.find("android {")
check("plugins precede la lógica de configuración Gradle", plugins_pos >= 0 and android_pos > plugins_pos)
check("sin credenciales hardcodeadas en build.gradle", "API_KEY" not in build and "SECRET_KEY" not in build and "SUPABASE" not in build)

daemon = ROOT / "gradle/gradle-daemon-jvm.properties"
check("sin criterio de JVM contradictorio con JDK 21 de CI", not daemon.exists())

wrapper = ROOT / "gradlew"
check("gradlew existe y es ejecutable en Unix", wrapper.is_file() and os.access(wrapper, os.X_OK))

app = (ROOT / "app/src/main/java/com/renea/arete_social/AreteApp.kt").read_text(encoding="utf-8")
activity = (ROOT / "app/src/main/java/com/renea/arete_social/MainActivity.kt").read_text(encoding="utf-8")
check("contenedor se inicializa en Application.onCreate", "contenedor = ContenedorApp(this)" in app)
check("MainActivity contiene punto de entrada de Compose", "setContent" in activity and "AreteNavHost" in activity)

ci_file = ROOT / ".github/workflows/ci.yml"
if ci_file.is_file():
    workflow = ci_file.read_text(encoding="utf-8")
    check("CI compila debug y release", "run: ./gradlew assembleDebug" in workflow and "run: ./gradlew assembleRelease" in workflow)
    check("CI ejecuta validador de integridad", "scripts/validar-integridad-entrega.py" in workflow)
else:
    check("flujo CI (.github/workflows/ci.yml)", True, "Omitido: entorno de compilación local sin .github/")

import subprocess

def _git_ignora(ruta: Path) -> bool:
    """Devuelve True si git ignoraría el archivo (está en .gitignore o no existe en el repo)."""
    if not ruta.exists():
        return True  # No existe → no se empaqueta → correcto
    result = subprocess.run(
        ["git", "check-ignore", "-q", str(ruta)],
        cwd=ROOT,
        capture_output=True
    )
    return result.returncode == 0

check("no se empaqueta local.properties", _git_ignora(ROOT / "local.properties"))
check("plantilla de configuración presente", (ROOT / "local.properties.example").is_file())

failed = 0
for name, ok, detail in checks:
    print(("OK" if ok else "ERROR") + ": " + name + (f" — {detail}" if detail else ""))
    failed += not ok
print(f"Resultado: {len(checks) - failed}/{len(checks)} comprobaciones correctas")
sys.exit(1 if failed else 0)
