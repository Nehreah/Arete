#!/usr/bin/env python3
"""Auditoría estática sencilla de colores literales en Kotlin UI."""
import os, re, collections
BASE = os.path.join("app", "src", "main", "java", "com", "renea", "arete_social", "gui")
for dp, _, files in os.walk(BASE):
    for name in files:
        if not name.endswith(".kt"): continue
        path = os.path.join(dp, name)
        with open(path, encoding="utf-8", errors="ignore") as f: src = f.read()
        matches = re.findall(r"Color\(0xFF[0-9A-Fa-f]{6}\)|Color\.(?:Green|Red|Blue|White|Black|Cyan|Magenta|Yellow)|#[0-9A-Fa-f]{6}", src)
        if matches: print(f"{path}: {len(matches)} colores literales; {', '.join(collections.Counter(matches).keys())}")
