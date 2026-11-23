#!/usr/bin/env python3
"""Auditoría estática de colores literales en pantallas que faltan por migrar.
No modifica archivos; los colores detectados requieren interpretación manual.
"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
GUI = ROOT / "app/src/main/java/com/renea/arete_social/gui"
TARGETS = ["comunidad", "auth", "evento", "lugar"]
PATTERN = re.compile(r"Color\s*\(\s*0x[0-9A-Fa-f]{8}\s*\)|Color\.(?:White|Black|Red|Green|Blue|Yellow|Cyan|Magenta)")

def main():
    total = 0
    print("Auditoría estática — no modifica código")
    for folder in TARGETS:
        base = GUI / folder
        if not base.exists():
            continue
        print(f"\n[{folder}]")
        for path in sorted(base.rglob("*.kt")):
            try:
                content = path.read_text(encoding="utf-8")
            except (UnicodeDecodeError, OSError):
                continue
            hits = [(i, line.strip(), len(PATTERN.findall(line))) for i, line in enumerate(content.splitlines(), 1) if PATTERN.search(line)]
            if hits:
                count = sum(x[2] for x in hits)
                total += count
                print(f"- {path.relative_to(ROOT)}: {count} coincidencias")
                for line_no, line, _ in hits[:5]:
                    print(f"    L{line_no}: {line[:150]}")
                if len(hits) > 5:
                    print(f"    ... {len(hits)-5} líneas más")
    print(f"\nTotal de coincidencias literales: {total}")
    print("Nota: una coincidencia no es automáticamente un error; revisar contexto y contraste.")

if __name__ == "__main__":
    main()
