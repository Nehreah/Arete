#!/usr/bin/env python3
"""Espejo en Python del protocolo y de la máquina de estados de la Fase 6 (match/MatchProtocol.kt y
MatchMachine.kt). NO ejecuta el Kotlin: comprueba que la LÓGICA del diseño converge bajo pérdida,
duplicados y reordenamiento, y que el codec rechaza basura. Si cambias el Kotlin, cambia esto también."""
import hashlib, random, re, sys

def ids(x): return hashlib.sha256(x.encode()).hexdigest()[:32]

ID = re.compile(r'^[A-Za-z0-9:_.-]{1,64}$'); TOKEN = re.compile(r'^[A-Za-z0-9_-]*$')
TYPES = {"INV","ACC","RDY","GO","RES","END","LEFT","PING","PONG"}
MAXLEN = 14000

def enc(sess, sender, mid, seq, sent, typ, payload):
    return f"MSM1|miausnake|{sess}|{sender}|{mid}|{seq}|{sent}|{typ}|{payload}"

def dec(t):
    if len(t) > MAXLEN or not t.startswith("MSM"): return None
    p = t.split("|", 8)
    if len(p) != 9 or p[0] != "MSM1" or p[1] != "miausnake": return None
    if p[7] not in TYPES or not ID.match(p[2]) or not ID.match(p[4]) or not re.match(r'^[A-Za-z0-9:_.-]{1,132}$', p[3]): return None
    try: seq, sent = int(p[5]), int(p[6])
    except ValueError: return None
    if not (0 <= seq <= 1_000_000) or sent < 0: return None
    if p[7] in ("PING","PONG") and (len(p[8]) > 4200 or not TOKEN.match(p[8])): return None
    if p[7] == "RES" and not (p[8].startswith("MIAUR1:") and not re.search(r'[\s|]', p[8])): return None
    return dict(sess=p[2], sender=p[3], mid=p[4], seq=seq, sent=sent, typ=p[7], payload=p[8])

SEED = 0x7F3A9C
COUNTDOWN = 3000; BEACON = 3000; GO_B = 1000; INVITE_TTL = 60000; LOBBY_TTL = 180000
BACKOFF = [5000, 10000, 20000, 30000]; RESULT_WAIT = 600000; END_GAP = 2000

class M:
    def __init__(s, role, me, peer, sess, now):
        s.role, s.me, s.peer, s.sess = role, me, peer, sess
        s.phase = "INVITING" if role == "H" else "INVITED"; s.since = now; s.lastseq = -1
        s.myready = s.peerready = s.peerleft = False; s.cd = 0; s.cdat = 0; s.beacon = now
        s.my = s.peerrun = None; s.peerrej = False; s.verifying = False; s.acked = None
        s.finished_at = 0; s.resb = 0; s.lastres = 0; s.lastend = -1; s.endpl = ""; s.closed = None
    def enter(s, p, now): s.phase, s.since = p, now
    def close(s, why, now, *x): s.phase, s.closed, s.since = "CLOSED", why, now; return list(x)
    def begin(s): return [("INV", "cfg")] if s.role == "H" else []
    def accept(s, now):
        if s.role == "G" and s.phase == "INVITED": s.enter("LOBBY", now); return [("ACC", "n")]
        return []
    def decline(s, now):
        if s.role == "G" and s.phase == "INVITED": return s.close("DECL", now, ("LEFT", "D"))
        return []
    def ready(s, now):
        if s.phase != "LOBBY" or s.myready: return []
        if s.role == "G": s.myready = True; s.beacon = now; return [("RDY", "n")]
        return s.go(now) if s.peerready else []
    def go(s, now):
        s.myready = True; s.cd = COUNTDOWN; s.cdat = now; s.beacon = now; s.enter("COUNTDOWN", now); return [("GO", f"{SEED:06X};0;{s.cd}")]
    def leave(s, now):
        if s.phase == "CLOSED": return []
        return s.close("CANCEL", now, *([] if s.phase == "FINISHED" else [("LEFT", "C")]))
    def i_finished(s, outcome, pk, now):
        if s.phase not in ("PLAYING", "COUNTDOWN") or s.my: return []
        s.my = (outcome, ids(pk)); s.finished_at = now; s.lastres = now; s.phase = "WAITING"; s.since = now
        s.pk = pk; out = [("RES", pk)]; s.maybe(now); return out
    def on(s, e, now):
        if s.phase == "CLOSED" or e["sess"] != s.sess or e["sender"] != s.peer or e["seq"] <= s.lastseq: return []
        s.lastseq = e["seq"]; t = e["typ"]
        if t == "LEFT":
            if s.phase in ("PLAYING", "WAITING", "FINISHED", "COUNTDOWN"): s.peerleft = True; return []
            return s.close("PEER_LEFT", now)
        if t == "ACC" and s.role == "H":
            if s.phase == "INVITING": s.enter("LOBBY", now)
            return []
        if t == "RDY" and s.role == "H":
            if s.phase == "INVITING": s.enter("LOBBY", now)
            if s.phase == "LOBBY": s.peerready = True
            return []
        if t == "GO" and s.role == "G" and s.phase == "LOBBY":
            seed, tick, cd = e["payload"].split(";")
            if int(seed, 16) != SEED: return s.close("PROTO", now, ("LEFT", "E"))
            s.cd = int(cd); s.cdat = now; s.enter("COUNTDOWN", now); return []
        if t == "RES" and s.phase in ("COUNTDOWN", "PLAYING", "WAITING", "FINISHED"):
            if s.peerrun or s.peerrej:
                if s.lastend >= 0 and now - s.lastend >= END_GAP: s.lastend = now; return [("END", s.endpl)]
                return []
            if s.verifying: return []
            s.verifying = True; return [("VERIFY", e["payload"])]
        if t == "END" and s.my and e["payload"].split(";")[0] == s.my[1] and s.acked is None:
            s.acked = e["payload"].endswith("OK")
        return []
    def verified(s, ok, rid, now):
        if s.phase == "CLOSED" or not s.verifying: return []
        s.verifying = False
        if ok: s.peerrun = rid
        else: s.peerrej = True
        s.endpl = rid + (";OK" if ok else ";BAD"); s.lastend = now; s.maybe(now); return [("END", s.endpl)]
    def maybe(s, now):
        if s.my and (s.peerrun or s.peerrej) and s.phase == "WAITING": s.enter("FINISHED", now)
    def tick(s, now):
        el = now - s.since; p = s.phase
        if p == "INVITING":
            if el > INVITE_TTL: return s.close("TIMEOUT", now, ("LEFT", "T"))
            if now - s.beacon >= BEACON: s.beacon = now; return [("INV", "cfg")]
        elif p == "INVITED":
            if el > INVITE_TTL: return s.close("TIMEOUT", now)
        elif p == "LOBBY":
            if el > LOBBY_TTL: return s.close("TIMEOUT", now, ("LEFT", "T"))
            if s.role == "G" and now - s.beacon >= BEACON: s.beacon = now; return [("RDY" if s.myready else "ACC", "n")]
        elif p == "COUNTDOWN":
            if now - s.cdat >= s.cd: s.enter("PLAYING", now); return [("PLAY", "")]
            if s.role == "H" and now - s.beacon >= GO_B: s.beacon = now; return [("GO", f"{SEED:06X};0;{s.cd}")]
        elif p in ("WAITING", "FINISHED") and s.my:
            if p == "WAITING" and s.peerleft: return s.close("PEER_LEFT", now)
            if now - s.finished_at > RESULT_WAIT: return s.close("TIMEOUT", now) if p == "WAITING" else []
            if s.acked is not None or s.peerleft: return []
            w = BACKOFF[min(s.resb, 3)]
            if now - s.lastres >= w: s.resb += 1; s.lastres = now; return [("RES", s.pk)]
        return []

class Net:
    """Red con pérdida, duplicados y reordenamiento. Cada envío usa messageId nuevo y secuencia creciente."""
    def __init__(s, rng, loss, dup, jitter): s.rng, s.loss, s.dup, s.jit, s.q = rng, loss, dup, jitter, []
    def send(s, now, to, text):
        if s.rng.random() < s.loss: return
        for _ in range(2 if s.rng.random() < s.dup else 1):
            s.q.append((now + s.rng.randint(5, s.jit), to, text))

def play(seed, loss, dup, jitter, my_len=40000, peer_len=55000, bad_peer=False):
    rng = random.Random(seed); net = Net(rng, loss, dup, jitter)
    now = 0; h = M("H", "A", "B", "S1", 0); g = M("G", "B", "A", "S1", 0)
    seqs = {"A": 0, "B": 0}; who = {"A": h, "B": g}; pend = []
    def emit(m, effs):
        for typ, pl in effs:
            if typ == "VERIFY": pend.append((now + 50, m, pl)); continue
            if typ == "PLAY": continue
            t = enc("S1", m.me, f"m{seqs[m.me]}x{rng.random()}"[:60].replace('.', '_'), seqs[m.me], now, typ, pl)
            seqs[m.me] += 1; net.send(now, m.peer, t)
    emit(h, h.begin()); decided = {}
    guest_acc = False; host_start = False; fin = {"A": None, "B": None}
    while now < 900000:
        now += 100
        for item in sorted([x for x in net.q if x[0] <= now], key=lambda x: x[0]):
            net.q.remove(item); _, to, text = item; e = dec(text)
            if e: emit(who[to], who[to].on(e, now))
        for item in [x for x in pend if x[0] <= now]:
            pend.remove(item); _, m, pl = item
            ok = not (bad_peer and m is h)   # bad_peer: el anfitrión recibe un resultado inválido
            emit(m, m.verified(ok, ids(pl), now))
        for m in (h, g): emit(m, m.tick(now))
        if g.phase == "INVITED" and now > 1000: emit(g, g.accept(now))
        if g.phase == "LOBBY" and not g.myready and now > 2000: emit(g, g.ready(now))
        if h.phase == "LOBBY" and h.peerready and not h.myready: emit(h, h.ready(now))
        for m, ln, nm in ((h, my_len, "A"), (g, peer_len, "B")):
            if m.phase == "PLAYING" and fin[nm] is None and now - m.since >= ln:
                fin[nm] = now; emit(m, m.i_finished(10 if nm == "A" else 20, "MIAUR1:" + nm * 20 + "xx", now))
        if h.phase == "FINISHED" and g.phase == "FINISHED" and h.acked is not None and g.acked is not None: break
    return h, g, now

def main():
    fails = 0
    # codec
    t = enc("S1", "A", "m1", 3, 1000, "GO", "7F3A9C;0;3000"); assert dec(t)
    for bad in ["", "hola", "MSM2|miausnake|S|A|m|1|1|GO|x", "MSM1|otro|S|A|m|1|1|GO|x", "MSM1|miausnake|S|A|m|-1|1|GO|x",
                "MSM1|miausnake|S|A|m|1|1|ZZZ|x", "MSM1|miausnake|S|A|m|1|1|PING|a b", "MSM1|miausnake|S|A|m|1|1|RES|abc",
                "MSM1|miausnake|S|A|m|1|1|PING|" + "a" * 4300, "M" * 20000, "MSM1|miausnake|S|A|m|1|1"]:
        assert dec(bad) is None, bad
    rng = random.Random(1)
    for _ in range(20000):
        s = "".join(rng.choice("MSM1|miausnake ABCDEF0123;-_:.\n") for _ in range(rng.randint(0, 120))); dec(s)
    mut = 0
    for i in range(len(t)):
        for c in "|;\n \x00é":
            m = t[:i] + c + t[i + 1:]
            try: dec(m)
            except Exception as ex: print("CODEC LANZA", repr(m), ex); fails += 1
    print("codec: ok (basura, 20000 fuzz, mutaciones sin excepciones)")
    # convergencia
    n = 0
    for seed in range(300):
        loss = [0.0, 0.1, 0.3, 0.5][seed % 4]
        h, g, now = play(seed, loss, 0.2, 900)
        ok = h.phase == "FINISHED" and g.phase == "FINISHED" and h.acked and g.acked
        if loss <= 0.3 and not ok:
            print("NO CONVERGE", seed, loss, h.phase, g.phase, h.acked, g.acked); fails += 1
        if ok: n += 1
    print(f"convergencia: {n}/300 duelos completos (pérdida 0-50 %, duplicados 20 %, retraso hasta 0,9 s); con pérdida ≤30 % deben ser todos")
    # el rival tramposo: resultado inválido para el anfitrión
    h, g, now = play(5, 0.1, 0.1, 500, bad_peer=True)
    assert h.peerrej and g.acked is False, (h.peerrej, g.acked)
    print("rival inválido: el anfitrión lo rechaza y el rival se entera (END BAD)")
    # el rival no responde: invitación caduca
    h = M("H", "A", "B", "S9", 0); h.begin(); t0 = 0
    while t0 < 70000 and h.phase != "CLOSED": t0 += 500; h.tick(t0)
    assert h.closed == "TIMEOUT" and 60000 <= t0 <= 61000; print("invitación sin respuesta: caduca a los 60 s")
    # GO con otra semilla
    g = M("G", "B", "A", "S1", 0); g.accept(0)
    r = g.on(dict(sess="S1", sender="A", seq=1, typ="GO", payload="000001;0;3000", mid="x", sent=0), 10)
    assert g.closed == "PROTO"; print("GO con semilla distinta: se cierra por protocolo")
    # secuencia vieja ignorada y sesión ajena
    g = M("G", "B", "A", "S1", 0); g.accept(0)
    g.on(dict(sess="S1", sender="A", seq=5, typ="LEFT", payload="C", mid="x", sent=0), 1)
    assert g.phase == "CLOSED"
    g = M("G", "B", "A", "S1", 0); g.accept(0)
    g.on(dict(sess="OTRA", sender="A", seq=5, typ="LEFT", payload="C", mid="x", sent=0), 1)
    g.on(dict(sess="S1", sender="C", seq=6, typ="LEFT", payload="C", mid="x", sent=0), 1)
    assert g.phase == "LOBBY"; print("sesión ajena y remitente ajeno: ignorados")
    print("FALLOS:", fails); sys.exit(1 if fails else 0)

main()
