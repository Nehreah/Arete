#!/usr/bin/env python3
"""Miau Snake · Fase 7: auditoría ESTÁTICA de calidad (sin compilador).

Uso:  python3 scripts/miausnake/phase7_check.py [raiz_del_repo]
Sale con código 1 si hay algún BLOQUEO. No sustituye a compilar ni a probar en teléfonos.
"""
import os, re, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
SRC = os.path.join(ROOT, "app", "src")
MS = "com/renea/arete_social/feature/miausnake"
res = []  # (nivel, nombre, detalle)

def add(ok, nombre, detalle="", nivel="BLOQUEO"):
    res.append(("OK" if ok else nivel, nombre, detalle))

def kt_files():
    for d, _, fs in os.walk(SRC):
        for f in fs:
            if f.endswith(".kt"):
                yield os.path.join(d, f)

def read(p):
    with open(p, encoding="utf8") as h:
        return h.read()

def balance(src):
    """Revisa que (), [] y {} casen, entendiendo comentarios, cadenas con ${...} anidadas y literales.
    Devuelve una lista de problemas (vacía si todo casa)."""
    pares = {")": "(", "]": "[", "}": "{"}
    pila, i, n, errs = [], 0, len(src), []
    # pila de contextos: ('c', cierre) código; ('s', raw) cadena
    modo = [("code", None)]
    def linea(k): return src.count("\n", 0, k) + 1
    while i < n:
        m, _ = modo[-1]
        c = src[i]
        if m == "code":
            if src.startswith("//", i):
                j = src.find("\n", i); i = n if j < 0 else j; continue
            if src.startswith("/*", i):
                j = src.find("*/", i + 2); i = n if j < 0 else j + 2; continue
            if src.startswith('"""', i):
                modo.append(("raw", None)); i += 3; continue
            if c == '"':
                modo.append(("str", None)); i += 1; continue
            if c == "'":
                j = i + 1
                if j < n and src[j] == "\\": j += 2
                else: j += 1
                if j < n and src[j] == "'": i = j + 1; continue
                i += 1; continue
            if c in "([{":
                pila.append((c, i))
            elif c in ")]}":
                if not pila or (pila[-1][0] != pares[c] and not (c == "}" and pila[-1][0] == "${")):
                    errs.append(f"'{c}' sin pareja en línea {linea(i)}"); return errs
                abierto = pila.pop()
                if abierto[0] == "${":
                    modo.pop()  # vuelve a la cadena que contenía la plantilla
            i += 1
            continue
        # dentro de cadena
        if m == "str" and c == "\\": i += 2; continue
        if m == "str" and c == '"': modo.pop(); i += 1; continue
        if m == "raw" and src.startswith('"""', i): modo.pop(); i += 3; continue
        if m in ("str", "raw") and src.startswith("${", i):
            pila.append(("${", i)); modo.append(("code", None)); i += 2; continue
        if m == "str" and c == "\n": modo.pop(); i += 1; continue
        i += 1
    return errs if not pila else [f"'{pila[-1][0]}' sin cerrar (línea {linea(pila[-1][1])})"]

# 1. Equilibrio de llaves/paréntesis en el feature (¡el código nunca se compiló!)
desbal = []
for p in kt_files():
    if MS not in p.replace("\\", "/"):
        continue
    e = balance(read(p))
    if e:
        desbal.append(f"{os.path.relpath(p, ROOT)}: {e[0]}")
add(not desbal, "Llaves/paréntesis equilibrados en miausnake/", "; ".join(desbal))

# 2. Imports internos que no resuelven a ninguna declaración (el paquete levels/ de la Fase 4)
decl = set()
rx_top = re.compile(
    r'^(?:@\w+(?:\([^)]*\))?\s*)*(?:(?:public|internal|private|data|sealed|enum|abstract|open|inline|value|'
    r'annotation|const|suspend|operator|infix|fun)\s+)*(?:class|object|interface|fun|val|var|typealias)\s+'
    r'(?:<[^>]*>\s*)?(?:[\w.<>?, ]+\.)?(\w+)', re.M)
for p in kt_files():
    t = read(p)
    m = re.search(r'^package\s+([\w.]+)', t, re.M)
    if m:
        for n in rx_top.findall(t):
            decl.add(m.group(1) + "." + n)
faltan = {}
base = "com.renea.arete_social.feature.miausnake."
for p in kt_files():
    if MS not in p.replace("\\", "/"):
        continue
    for imp in re.findall(r'^import\s+(com\.renea\.arete_social\.feature\.miausnake[\w.]*)', read(p), re.M):
        if imp.endswith(".*"):
            if not any(d.startswith(imp[:-1]) for d in decl):
                faltan.setdefault(imp, set()).add(os.path.basename(p))
        elif imp not in decl and imp.rsplit(".", 1)[0] not in decl:
            faltan.setdefault(imp, set()).add(os.path.basename(p))
add(not faltan, "Todo import interno de miausnake resuelve",
    "; ".join(f"{k.replace(base, '')} ← {sorted(v)}" for k, v in sorted(faltan.items())))

# 3. Sin arte ni audio de Miau Snake
ext = {"png", "webp", "jpg", "jpeg", "gif", "svg", "ogg", "mp3", "wav", "m4a", "flac", "mid", "aac", "ttf", "otf", "woff", "woff2"}
infr = []
for d, _, fs in os.walk(SRC):
    pp = d.replace("\\", "/")
    if "/res" in pp or "/assets" in pp:
        for f in fs:
            if f.rsplit(".", 1)[-1].lower() in ext and re.search(r"miau|snake", f, re.I):
                infr.append(os.path.join(d, f))
add(not infr, "Sin arte ni audio propios (gate de la Fase 7)", "; ".join(infr))

# 4. Función cercana apagada por defecto
ff = read(os.path.join(SRC, "main/java/com/renea/arete_social/domain/model/FeatureFlags.kt"))
add(bool(re.search(r"var\s+MIAUSNAKE_CERCANIA\s*:\s*Boolean\s*=\s*false", ff)),
    "MIAUSNAKE_CERCANIA = false (la Fase 6 no pasó su puerta)")

# 5. Nada de permisos nuevos en el código del juego
perm = [os.path.basename(p) for p in kt_files() if MS in p.replace("\\", "/") and "android.permission" in read(p)
        and "/test/" not in p.replace("\\", "/")]
add(not perm, "El juego no pide permisos propios", ", ".join(perm))

# 6. Versión ya no es «Alpha»
gr = read(os.path.join(ROOT, "app", "build.gradle.kts"))
v = re.search(r'versionName\s*=\s*"([^"]+)"', gr)
add(bool(v) and "alpha" not in v.group(1).lower(), "versionName marcada como candidata", v.group(1) if v else "no encontrada")

# 7. Depuración solo en builds debug
host = read(os.path.join(SRC, "main", "java", *MS.split("/"), "ui/host/MiauSnakeEnMichi.kt"))
add(bool(re.search(r"if\s*\(\s*BuildConfig\.DEBUG\s*\)", host)) and "showDebug" in host,
    "El panel de depuración del host solo existe con BuildConfig.DEBUG")
playtest = [p for p in kt_files() if "MiauSnakePlaytestScreen" in read(p) and "MiauSnakePlaytestActivity" not in p
            and "MiauSnakePlaytestScreen.kt" not in p and "/test/" not in p.replace("\\", "/")]
add(not [p for p in playtest if "/debug/" not in p.replace("\\", "/")],
    "PlaytestScreen solo se usa desde src/debug", ", ".join(os.path.basename(p) for p in playtest), "AVISO")

# 8. Registros (Log) con datos sensibles
sens = []
for p in kt_files():
    if MS not in p.replace("\\", "/") or "/test/" in p.replace("\\", "/"):
        continue
    for i, l in enumerate(read(p).splitlines(), 1):
        if re.search(r"\b(Log\.[dviwe]|println)\(", l) and re.search(r"replay|payload|packet|firma|key|secret|nombre|player", l, re.I):
            sens.append(f"{os.path.basename(p)}:{i}")
add(not sens, "Sin Log/println con repeticiones, paquetes, claves o nombres", ", ".join(sens))

# 9. Objetivos táctiles (heurística): clickable/size < 48 dp
peq = []
for p in kt_files():
    if MS not in p.replace("\\", "/") or "/test/" in p.replace("\\", "/"):
        continue
    t = read(p)
    for m in re.finditer(r"(?:minHeight|heightIn\(min)\s*=\s*(\d+)\.dp", t):
        if int(m.group(1)) < 48 and "clickable" in t[max(0, m.start() - 300): m.end() + 300]:
            peq.append(f"{os.path.basename(p)}: {m.group(0)}")
add(not peq, "Ningún control pulsable de menos de 48 dp", "; ".join(peq), "AVISO")

# 10. Texto: nada por debajo de 12 sp fuera de depuración
chicos = []
for p in kt_files():
    if MS not in p.replace("\\", "/") or "/test/" in p.replace("\\", "/") or "spike" in p or "Playtest" in p:
        continue
    for i, l in enumerate(read(p).splitlines(), 1):
        m = re.search(r"size\s*=\s*(\d+)", l)
        if m and int(m.group(1)) < 12 and "debugText" not in l:
            chicos.append(f"{os.path.basename(p)}:{i}={m.group(1)}")
add(not chicos, "Sin texto por debajo de 12 sp", ", ".join(chicos), "AVISO")

# 11. Semántica de accesibilidad presente
sw = read(os.path.join(SRC, "main", "java", *MS.split("/"), "ui/play/SnakeWidgets.kt"))
pl = read(os.path.join(SRC, "main", "java", *MS.split("/"), "ui/play/MiauSnakePlayScreen.kt"))
add("heading()" in sw and "onClickLabel" in sw and "disabled()" in sw, "SnakeButton/SnakeText con semántica (encabezado, etiqueta, desactivado)")
add("stateDescription" in pl and "liveRegion" in pl, "Tablero con estado y resultados en región viva")

# 12. Documentos de la versión
docs = ["README.md", "PRIVACIDAD.md", "QA_MATRIZ.md", "CHECKLIST_RELEASE.md"]
falta = [d for d in docs if not os.path.isfile(os.path.join(ROOT, "docs", "miau-snake", d))]
add(not falta, "Documentación de la versión completa", ", ".join(falta))

w = max(len(n) for _, n, _ in res)
for nivel, nombre, det in res:
    print(f"[{nivel:7}] {nombre:<{w}}" + (f"  → {det}" if det and nivel != "OK" else ""))
bl = [r for r in res if r[0] == "BLOQUEO"]
print(f"\n{len(res) - len(bl) - len([r for r in res if r[0]=='AVISO'])} OK · {len([r for r in res if r[0]=='AVISO'])} avisos · {len(bl)} bloqueos")
sys.exit(1 if bl else 0)
