#!/usr/bin/env python3
"""Limpia hojas de sprites (por ejemplo generadas con prompts) para que pasen la validacion.

Uso:
  python3 -I scripts/limpiar-sprites-pixel.py entrada/ salida/ [--paleta paleta.txt | --colores 24]
         [--alinear-pies] [--centrar] [--reducir N]

Nunca modifica la carpeta de entrada: escribe copias en 'salida/' con la misma estructura.
Pasos, en este orden:
  1. --reducir N   : si la imagen es un 'pixel art' ampliado N veces (cada pixel real = NxN), la devuelve a su tamano real.
  2. Alfa binario  : alfa >= 128 -> 255, el resto -> 0 (sin antialiasing ni sombras suaves).
  3. Colores       : con --paleta, cada pixel pasa al color mas cercano; con --colores N, se reduce a N colores.
  4. --alinear-pies: en hojas de personaje, mueve cada cuadro en vertical para que los pies queden en la misma fila.
  5. --centrar     : centra horizontalmente el cuerpo de cada cuadro.
Despues ejecuta validar-sprites-pixel.py sobre la salida.
"""
import argparse
import sys
from pathlib import Path
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from pixel_comun import CUADRO, leer_paleta, clasificar, cuadros, caja_opaca


def cuadrado_mas_cercano(c, paleta):
    return min(paleta, key=lambda p: (p[0] - c[0]) ** 2 + (p[1] - c[1]) ** 2 + (p[2] - c[2]) ** 2)


def reducir(img, n):
    w, h = img.size
    if w % n or h % n:
        print(f"    aviso: {w}x{h} no es multiplo de {n}; no se reduce")
        return img
    return img.resize((w // n, h // n), Image.NEAREST)


def a_binario_y_paleta(img, paleta, colores):
    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            px[x, y] = (r, g, b, 255) if a >= 128 else (0, 0, 0, 0)
    if paleta:
        cache = {}
        for y in range(h):
            for x in range(w):
                r, g, b, a = px[x, y]
                if a:
                    k = (r, g, b)
                    if k not in cache:
                        cache[k] = cuadrado_mas_cercano(k, paleta)
                    px[x, y] = cache[k] + (255,)
    elif colores:
        rgb = Image.new("RGB", img.size)
        rgb.paste(img.convert("RGB"), mask=img.getchannel("A"))
        q = rgb.quantize(colors=colores, method=Image.MEDIANCUT, dither=Image.NONE).convert("RGB")
        qp = q.load()
        for y in range(h):
            for x in range(w):
                if px[x, y][3]:
                    px[x, y] = qp[x, y] + (255,)
    return img


def alinear(img, ancho_c, pies, centrar):
    frames = cuadros(img, ancho_c)
    cajas = [caja_opaca(f) for f in frames]
    validas = [c for c in cajas if c]
    if not validas:
        return img
    fila_pies = max(c[3] for c in validas)  # la fila mas baja entre todos los cuadros
    salida = Image.new("RGBA", img.size, (0, 0, 0, 0))
    for i, (f, c) in enumerate(zip(frames, cajas)):
        dx = dy = 0
        if c:
            if pies:
                dy = fila_pies - c[3]
            if centrar:
                dx = ancho_c // 2 - (c[0] + c[2]) // 2
        nuevo = Image.new("RGBA", f.size, (0, 0, 0, 0))
        nuevo.paste(f, (dx, dy))
        salida.paste(nuevo, (i * ancho_c, 0))
    return salida


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("entrada")
    ap.add_argument("salida")
    g = ap.add_mutually_exclusive_group()
    g.add_argument("--paleta")
    g.add_argument("--colores", type=int)
    ap.add_argument("--alinear-pies", action="store_true")
    ap.add_argument("--centrar", action="store_true")
    ap.add_argument("--reducir", type=int, metavar="N")
    a = ap.parse_args()
    ent, sal = Path(a.entrada).resolve(), Path(a.salida).resolve()
    if sal == ent or ent in sal.parents:
        print("La carpeta de salida no puede estar dentro de la de entrada.")
        return 1
    paleta = leer_paleta(a.paleta) if a.paleta else None
    n = 0
    for f in sorted(ent.rglob("*.png")):
        img = Image.open(f).convert("RGBA")
        if a.reducir and a.reducir > 1:
            img = reducir(img, a.reducir)
        img = a_binario_y_paleta(img, paleta, a.colores)
        tipo, _ = clasificar(f.name)
        if tipo in ("mascota", "huevo", "mimi") and (a.alinear_pies or a.centrar) and img.width % CUADRO == 0:
            img = alinear(img, CUADRO, a.alinear_pies, a.centrar)
        destino = sal / f.relative_to(ent)
        destino.parent.mkdir(parents=True, exist_ok=True)
        img.save(destino)
        n += 1
        print(f"limpio  {f.relative_to(ent)}")
    print(f"\n{n} archivos escritos en {sal}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
