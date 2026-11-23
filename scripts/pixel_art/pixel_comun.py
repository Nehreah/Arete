"""Utilidades compartidas por los scripts de arte pixel de Michi."""
import re
from pathlib import Path
from PIL import Image

CUADRO = 32
ESPECIES = {"gato", "perro", "zorro", "conejo"}
ETAPAS = {"bebe", "nino", "adulto", "anciano"}
ANIM_MASCOTA = {"idle", "feliz", "hambriento", "sucio", "cansado", "triste", "enfermo",
                "dormido", "comer", "jugar", "banar", "curar", "despertar", "glitch", "caminar"}
# Animaciones que SE GENERAN POR CODIGO (atajos aceptados): no hacen falta, no son error si llegan.
ANIM_ATAJO = {"sucio", "cansado", "triste", "glitch", "despertar"}
ANIM_HUEVO = {"idle", "eclosion"}
CUADROS_SUGERIDOS = {"idle": 4, "feliz": 4, "hambriento": 4, "enfermo": 4, "dormido": 4,
                     "comer": 6, "jugar": 6, "banar": 6, "curar": 6, "caminar": 4,
                     "huevo_idle": 4, "huevo_eclosion": 6}
# id de objeto -> (anchoTiles, altoTiles). None = por confirmar (9 objetos de ambiente).
OBJETOS = {
    "cama_futurista": (2, 1), "cama_cojin": (1, 1), "comedero_automatico": (1, 1),
    "dispensador_snacks": (1, 1), "espejo_neon": (1, 1), "cabina_ducha_cyber": (1, 2),
    "rascador_poste": (1, 1), "consola_arcade": (1, 1), "estanteria_hololibros": (2, 1),
    "sofa_holografico": (2, 1), "mesa_centro": (2, 2), "planta_bonsai_led": (1, 1),
    "lampara_lava_neon": (1, 1), "alfombra_pixel_red": (2, 2), "trofeo_torneo_gym": (1, 1),
    "farol_festival_parque": (1, 1), "taza_dorada_cafe": (1, 1),
    "mueble_banca": None, "dec_arbol": None, "dec_fuente": None, "gym_cinta": None,
    "gym_pesas": None, "gym_colchoneta": None, "cafe_barra": None, "cafe_mesa": None,
    "cafe_maquina": None,
}


def leer_paleta(ruta):
    """Un color HEX por linea (#RRGGBB o RRGGBB). Lineas vacias y '#' solos se ignoran."""
    colores = []
    for linea in Path(ruta).read_text(encoding="utf-8").splitlines():
        t = linea.strip().lstrip("#")
        if re.fullmatch(r"[0-9a-fA-F]{6}", t):
            colores.append(tuple(int(t[i:i + 2], 16) for i in (0, 2, 4)))
    return colores


def clasificar(nombre):
    """Devuelve (tipo, detalle) segun el nombre del archivo, o (None, motivo)."""
    base = Path(nombre).name
    if not re.fullmatch(r"[a-z0-9_]+\.png", base):
        return None, "nombre: solo minusculas, numeros, guion bajo, sin tildes, extension .png"
    s = base[:-4]
    if s.startswith("pet_huevo_"):
        a = s[len("pet_huevo_"):]
        return ("huevo", a) if a in ANIM_HUEVO else (None, f"huevo: animacion '{a}' no valida (idle, eclosion)")
    if s.startswith("pet_"):
        p = s.split("_", 3)
        if len(p) != 4:
            return None, "mascota: formato pet_{especie}_{etapa}_{animacion}.png"
        _, esp, eta, anim = p
        if esp not in ESPECIES:
            return None, f"especie '{esp}' no valida {sorted(ESPECIES)}"
        if eta not in ETAPAS:
            return None, f"etapa '{eta}' no valida {sorted(ETAPAS)}"
        if anim not in ANIM_MASCOTA:
            return None, f"animacion '{anim}' no valida"
        return "mascota", anim
    if s in ("mimi_caminar", "mimi_idle"):
        return "mimi", s
    if s.startswith("obj_"):
        i = s[4:]
        return ("objeto", i) if i in OBJETOS else (None, f"objeto '{i}' no esta en el catalogo")
    if s.startswith("fx_"):
        return "fx", s
    if s.startswith("tiles_") or s.startswith("evento_"):
        return "escenario", s
    return None, "prefijo desconocido (pet_, mimi_, obj_, fx_, tiles_, evento_)"


def cuadros(img, ancho_cuadro):
    n = img.width // ancho_cuadro
    return [img.crop((i * ancho_cuadro, 0, (i + 1) * ancho_cuadro, img.height)) for i in range(n)]


def caja_opaca(frame):
    """Caja (izq, arr, der, aba) de los pixeles opacos, o None si el cuadro esta vacio."""
    return frame.getchannel("A").point(lambda v: 255 if v else 0).getbbox()
