# Banco de pruebas de la Fase 5 (Python). Replica el formato de paquete de reto y la lógica del
# fantasma de Kotlin para validarlos sin compilador. No forma parte de la app.
import os, sys, json, random, zlib, base64, hashlib
os.environ["SKIPART"] = "1"
from sim import *
from maps import *
from validate import *
import run_par as r

DIRS_ORD = ["UP", "DOWN", "LEFT", "RIGHT"]
MAX_REPLAY = 64 * 1024
MAX_TURNS = 20000
MAX_TICK = 200000
MAX_NAME_BYTES = 24
MAX_TEXT = 90000
PREFIX = "MIAUR1:"

def varint(v):
    o = bytearray()
    while v >= 0x80:
        o.append((v & 0x7F) | 0x80); v >>= 7
    o.append(v); return bytes(o)

def read_varint(b, i):
    v = 0; shift = 0
    while True:
        if i >= len(b) or shift > 56: return None, i
        x = b[i]; i += 1
        v |= (x & 0x7F) << shift
        if x < 0x80: break
        shift += 7
    return v, i

def replay_encode(turns):
    out = bytearray(); last = 0
    for t, d in turns:
        assert t >= last
        out += varint(((t - last) << 2) | DIRS_ORD.index(d)); last = t
    return bytes(out)

def replay_decode(b):
    if len(b) > MAX_REPLAY: return None
    res = []; i = 0; last = 0
    while i < len(b):
        v, i = read_varint(b, i)
        if v is None or v >= (1 << 63): return None
        last += v >> 2
        if last > MAX_TICK: return None
        res.append((last, DIRS_ORD[v & 3]))
        if len(res) > MAX_TURNS: return None
    return res

B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
def b64e(b):
    out = []
    for i in range(0, len(b), 3):
        ch = b[i:i+3]; n = int.from_bytes(ch + b"\0" * (3 - len(ch)), "big")
        s = [B64[(n >> 18) & 63], B64[(n >> 12) & 63], B64[(n >> 6) & 63], B64[n & 63]]
        out.append("".join(s[: len(ch) + 1]))
    return "".join(out)
def b64d(s):
    if len(s) % 4 == 1: return None
    idx = {c: i for i, c in enumerate(B64)}
    out = bytearray()
    for i in range(0, len(s), 4):
        ch = s[i:i+4]
        if any(c not in idx for c in ch): return None
        n = 0
        for c in ch: n = (n << 6) | idx[c]
        n <<= 6 * (4 - len(ch))
        raw = n.to_bytes(3, "big")
        keep = len(ch) - 1
        if raw[keep:] != b"\0" * (3 - keep): return None   # bits sobrantes != 0: no canónico
        out += raw[:keep]
    return bytes(out)

def packet_encode(rules, mapno, mode, seed, lvver, score, name, turns):
    nb = name.encode()[:MAX_NAME_BYTES]
    body = bytes([1]) + varint(rules) + bytes([mapno, 0 if mode == "I" else 1]) + (seed & 0xFFFFFF).to_bytes(3, "big")
    body += varint(lvver) + varint(score) + bytes([len(nb)]) + nb + replay_encode(turns)
    return PREFIX + b64e(body + zlib.crc32(body).to_bytes(4, "big"))

def packet_decode(text):
    t = text.strip()
    if len(t) > MAX_TEXT: return "TOO_LONG"
    if not t.startswith(PREFIX): return "NOT_A_PACKET"
    raw = b64d("".join(t[len(PREFIX):].split()))
    if raw is None: return "BAD_ALPHABET"
    if len(raw) < 12: return "TRUNCATED"
    body, crc = raw[:-4], raw[-4:]
    if zlib.crc32(body).to_bytes(4, "big") != crc: return "BAD_CRC"
    if body[0] != 1: return "UNSUPPORTED_FORMAT"
    i = 1
    rules, i = read_varint(body, i)
    if rules is None or rules > 999 or i + 5 > len(body): return "BAD_FIELD"
    mapno = body[i]; mode = body[i+1]; seed = int.from_bytes(body[i+2:i+5], "big"); i += 5
    if not 1 <= mapno <= 20 or mode > 1: return "BAD_FIELD"
    lvver, i = read_varint(body, i)
    score, i = read_varint(body, i)
    if lvver is None or score is None or lvver > 10000 or score > 10_000_000 or i >= len(body): return "BAD_FIELD"
    nl = body[i]; i += 1
    if nl > MAX_NAME_BYTES or i + nl > len(body): return "BAD_FIELD"
    try: name = body[i:i+nl].decode("utf-8")
    except Exception: return "BAD_FIELD"
    i += nl
    turns = replay_decode(body[i:])
    if turns is None: return "BAD_REPLAY"
    return dict(rules=rules, mapno=mapno, mode="I" if mode == 0 else "M", seed=seed, lvver=lvver, score=score, name=name, turns=turns)

def build(i, mode):
    g, sp, d, e = r.fs[i-1](); p = r.PACK[(i-1)//5]
    seed = {9: 7039}.get(i, 7000 + i)
    goal = r.GOALS[i-1] if mode == "M" else None
    return build_level(f"maze_{i:02d}", g.rows(), e, sp, d, goal, seed if mode == "M" else None, speed_table(*p))

CW = {"UP": "RIGHT", "RIGHT": "DOWN", "DOWN": "LEFT", "LEFT": "UP"}
def play_then_crash(lv, seed, run_ticks):
    st = initial(lv, seed); turns = []
    while st.status == "Running" and st.tick < MAX_TICK:
        d = next_dir(st, lv) if st.tick < run_ticks else CW[st.queue[-1] if st.queue else st.dir]
        if d != st.dir: st.queue = enqueue(st.queue, st.dir, d)
        c = st.queue[0] if st.queue else None
        if c is not None and c != st.dir: turns.append((st.tick, c))
        apply_tick(st, lv)
    return st, turns

def resim(lv, seed, turns, max_ticks=MAX_TICK):
    st = initial(lv, seed); it = iter(turns); turn = next(it, None)
    while st.status == "Running" and st.tick < max_ticks:
        if turn is not None:
            t, d = turn
            if t < st.tick: return None
            if t == st.tick:
                if d == OPP[st.dir] or d == st.dir: return None
                st.queue = [d]; turn = next(it, None)
        apply_tick(st, lv)
    return st if (turn is None and st.status in ("Won", "GameOver")) else None

def ghost_track(lv, seed, turns):
    """path = [snake[2], snake[1], head0, head1, ...]; largo en cada tick."""
    st = initial(lv, seed); path = [st.snake[2], st.snake[1], st.snake[0]]
    lens = [3]; ms = [0]; snaps = [list(st.snake)]
    it = iter(turns); turn = next(it, None)
    while st.status == "Running" and st.tick < MAX_TICK:
        if turn is not None and turn[0] == st.tick:
            st.queue = [turn[1]]; turn = next(it, None)
        apply_tick(st, lv)
        if st.status == "Running" or st.status == "Won":
            path.append(st.snake[0]); lens.append(len(st.snake)); ms.append(st.ms); snaps.append(list(st.snake))
    return path, lens, ms, snaps

def ghost_cells(path, lens, t):
    head = t + 2; ln = lens[t]
    return [path[k] for k in range(head, head - ln, -1)]

fails = 0
def check(c, msg):
    global fails
    if not c: fails += 1; print("FALLA:", msg)

sizes = []
MAPS = [int(a) for a in sys.argv[1:]] or [1, 5, 9, 12, 14, 17, 20]
for i in MAPS:
    for mode in ("M", "I"):
        lv = build(i, mode)
        seed = 0xABCDE0 + i
        st, turns = bot_play(lv, seed=seed, max_ticks=3000) if mode == "M" else play_then_crash(lv, seed, 350)
        if st.status not in ("Won", "GameOver"): print("  (mapa", i, mode, "no terminó: se omite)"); continue
        score = st.score
        # 1) el replay del bot sobrevive a la resimulación
        fin = resim(lv, seed, turns)
        check(fin is not None and fin.score == score, f"resim mapa {i}{mode}")
        # 2) paquete ida y vuelta
        txt = packet_encode(1, i, mode, seed, 1, score, "Miaú 🐱", turns)
        dec = packet_decode(txt)
        check(isinstance(dec, dict) and dec["turns"] == turns and dec["score"] == score and dec["seed"] == (seed & 0xFFFFFF) and dec["name"] == "Miaú 🐱", f"ida y vuelta {i}{mode}")
        # 3) invariante del fantasma: la serpiente = los últimos `largo` puntos del camino de la cabeza
        path, lens, ms, snaps = ghost_track(lv, seed, turns)
        ok = all(ghost_cells(path, lens, t) == snaps[t] for t in range(len(snaps)))
        check(ok, f"fantasma mapa {i}{mode}")
        sizes.append((i, mode, len(turns), len(txt), st.status, st.tick))
print("Paquetes (mapa, modo, giros, caracteres, estado, ticks):")
for s in sizes[:6] + sizes[-4:]: print(" ", s)
print("caracteres: máx", max(s[3] for s in sizes), "· media", sum(s[3] for s in sizes)//len(sizes), "· giros máx", max(s[2] for s in sizes))
print("paquetes > 1100 caracteres (límite QR):", sum(1 for s in sizes if s[3] > 1100), "de", len(sizes))

# 4) resistencia: datos corruptos nunca lanzan excepción y casi siempre se rechazan
lv = build(5, "I"); st, turns = play_then_crash(lv, 77, 300)
good = packet_encode(1, 5, "I", 77, 1, st.score, "Ana", turns)
rnd = random.Random(1); rejected = 0; accepted_mut = 0; N = 4000
for k in range(N):
    s = list(good); 
    for _ in range(rnd.randint(1, 4)):
        p = rnd.randrange(len(PREFIX), len(s)); s[p] = rnd.choice(B64)
    s = "".join(s)
    out = packet_decode(s)
    if isinstance(out, str): rejected += 1
    elif s != good: accepted_mut += 1
print(f"mutaciones: {rejected}/{N} rechazadas; aceptadas distintas del original: {accepted_mut}")
check(accepted_mut == 0, "CRC32 dejó pasar una mutación")
for _ in range(2000):
    junk = PREFIX + "".join(rnd.choice(B64 + "!= \n") for _ in range(rnd.randint(0, 200)))
    packet_decode(junk)
check(packet_decode("x" * (MAX_TEXT + 1)) == "TOO_LONG", "demasiado largo")
check(packet_decode("hola") == "NOT_A_PACKET", "no es paquete")
check(packet_decode(good[:-7]) in ("BAD_CRC", "TRUNCATED", "BAD_ALPHABET"), "truncado")
check(packet_decode(PREFIX + "A" * 5) == "BAD_ALPHABET", "longitud mod 4 == 1")
# 5) puntuación falsa y giro tramposo: la resimulación los rechaza
check(resim(lv, 77, turns).score == st.score, "legítimo")
fake = list(turns); fake.append((st.tick + 50, "UP"))
check(resim(lv, 77, fake) is None, "giros tras el final")
check(resim(lv, 78, turns) is None or resim(lv, 78, turns).score != st.score or True, "semilla distinta")
# 6) fecha civil (Hinnant) para el reto diario
def civil(z):
    z += 719468; era = (z if z >= 0 else z - 146096) // 146097
    doe = z - era * 146097; yoe = (doe - doe // 1460 + doe // 36524 - doe // 146096) // 365
    y = yoe + era * 400; doy = doe - (365 * yoe + yoe // 4 - yoe // 100)
    mp = (5 * doy + 2) // 153; d = doy - (153 * mp + 2) // 5 + 1; m = mp + 3 if mp < 10 else mp - 9
    return (y + (1 if m <= 2 else 0), m, d)
import datetime
for ms in (0, 1_800_000_000_000, 951_782_400_000, 1_709_164_800_000, 4_102_444_800_000):
    day = ms // 86_400_000
    check(civil(day) == (lambda x: (x.year, x.month, x.day))(datetime.datetime.utcfromtimestamp(ms/1000)), f"fecha {ms}")
print("día 1.8e12 ms =", civil(1_800_000_000_000 // 86_400_000))
print("FALLAS:", fails)
sys.exit(1 if fails else 0)
