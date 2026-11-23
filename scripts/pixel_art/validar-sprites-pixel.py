#!/usr/bin/env python3
"""Valida las hojas de sprites pixel art de Michi antes de integrarlas.

Uso:
  python3 -I scripts/validar-sprites-pixel.py assets_michi/ [--paleta assets_michi/paleta.txt] [--estricto]

Errores (impiden integrar): nombre, tamano, semitransparencias, colores fuera de paleta, cuadros vacios.
Avisos (revisar): pies que saltan entre cuadros, cuerpo descentrado, numero de cuadros distinto del sugerido,
                  mas de 32 colores, animaciones que se generan por codigo.
Codigo de salida: 0 sin errores, 1 con errores (con --estricto tambien por avisos).
"""
import argparse
import sys
from pathlib import Path
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from pixel_comun import (CUADRO, ANIM_ATAJO, CUADROS_SUGERIDOS, OBJETOS,
                         leer_paleta, clasificar, cuadros, caja_opaca)


def validar(ruta, paleta):
    errores, avisos = [], []
    tipo, detalle = clasificar(ruta.name)
    if tipo is None:
        return [detalle], avisos
    try:
        img = Image.open(ruta)
        img.load()
    except Exception as e:  # archivo corrupto
        return [f"no se pudo abrir: {e}"], avisos
    img = img.convert("RGBA")
    w, h = img.size

    # --- Tamano de cuadro segun el tipo ---
    if tipo in ("mascota", "huevo", "mimi"):
        ancho_c, alto_c = CUADRO, CUADRO
    elif tipo == "objeto":
        t = OBJETOS[detalle]
        ancho_c, alto_c = (t[0] * CUADRO, t[1] * CUADRO) if t else (None, None)
        if t is None:
            if w % CUADRO or h % CUADRO:
                errores.append(f"tamano {w}x{h}: debe ser multiplo de {CUADRO} (medidas del objeto por confirmar)")
            ancho_c = None
    else:  # fx / escenario: multiplos de 16 (fx) o de 32 (tiles)
        ancho_c = alto_c = None
        base = 16 if tipo == "fx" else CUADRO
        if w % base or h % base:
            errores.append(f"tamano {w}x{h}: debe ser multiplo de {base}")

    if ancho_c:
        if h != alto_c:
            errores.append(f"alto {h}: debe ser {alto_c}")
        if w % ancho_c or w == 0:
            errores.append(f"ancho {w}: debe ser multiplo de {ancho_c}")
    n = w // ancho_c if ancho_c else 1

    # --- Transparencia estricta ---
    alfa = img.getchannel("A")
    semi = sum(1 for v in alfa.tobytes() if 0 < v < 255)
    if semi:
        errores.append(f"{semi} pixeles semitransparentes (antialiasing o sombras suaves)")

    # --- Colores ---
    datos = img.tobytes()
    opacos = {tuple(datos[i:i + 3]) for i in range(0, len(datos), 4) if datos[i + 3] == 255}
    if len(opacos) > 32:
        avisos.append(f"{len(opacos)} colores en esta hoja (el plan fija 16-32 en total)")
    if paleta:
        fuera = sorted(opacos - set(paleta))
        if fuera:
            ej = ", ".join("#%02X%02X%02X" % c for c in fuera[:4])
            errores.append(f"{len(fuera)} colores fuera de la paleta (ej. {ej})")

    # --- Cuadros: vacios, pies y centrado (solo animaciones de personaje) ---
    if ancho_c and w % ancho_c == 0 and h == alto_c:
        frames = cuadros(img, ancho_c)
        cajas = [caja_opaca(f) for f in frames]
        if any(c is None for c in cajas):
            errores.append("hay cuadros completamente vacios")
        elif tipo in ("mascota", "huevo", "mimi"):
            pies = {c[3] for c in cajas}
            if len(pies) > 1:
                avisos.append(f"los pies cambian de fila entre cuadros {sorted(pies)} (el personaje 'salta')")
            centros = [((c[0] + c[2]) / 2) for c in cajas]
            if max(centros) - min(centros) > 3:
                avisos.append("el cuerpo se desplaza mas de 3 px de lado a lado entre cuadros")
            if any(c[0] == 0 or c[2] == CUADRO for c in cajas):
                avisos.append("el dibujo toca el borde lateral del cuadro (se puede recortar)")
        clave = ("huevo_" + detalle) if tipo == "huevo" else detalle
        sug = CUADROS_SUGERIDOS.get(clave)
        if sug and n != sug and tipo in ("mascota", "huevo"):
            avisos.append(f"{n} cuadros (sugeridos {sug}; no es obligatorio)")
    if tipo == "mascota" and detalle in ANIM_ATAJO:
        avisos.append(f"'{detalle}' se genera por codigo; esta hoja no se usara salvo que se desactive el atajo")
    return errores, avisos


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("carpeta")
    ap.add_argument("--paleta", help="archivo con un color HEX por linea")
    ap.add_argument("--estricto", action="store_true", help="los avisos tambien cuentan como fallo")
    a = ap.parse_args()
    raiz = Path(a.carpeta)
    archivos = sorted(raiz.rglob("*.png"))
    if not archivos:
        print(f"No hay PNG en {raiz}")
        return 1
    paleta = leer_paleta(a.paleta) if a.paleta else None
    if a.paleta:
        print(f"Paleta: {len(paleta)} colores")
        if len(paleta) > 32:
            print("  AVISO: la paleta tiene mas de 32 colores")
    ne = na = 0
    for f in archivos:
        err, avi = validar(f, paleta)
        rel = f.relative_to(raiz)
        if not err and not avi:
            print(f"OK     {rel}")
        for e in err:
            print(f"ERROR  {rel}: {e}")
        for v in avi:
            print(f"AVISO  {rel}: {v}")
        ne += len(err)
        na += len(avi)
    print(f"\n{len(archivos)} archivos · {ne} errores · {na} avisos")
    return 1 if ne or (a.estricto and na) else 0


if __name__ == "__main__":
    sys.exit(main())
