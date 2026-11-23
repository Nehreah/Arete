from sim import *
from collections import deque

def build_level(mid, rows, edge, spawn, sdir, goal, seed, speed):
    walls=[(x,y) for y,r in enumerate(rows) for x,c in enumerate(r) if c=='#']
    return Level(mid,len(rows[0]),len(rows),edge,walls,spawn,sdir,goal,seed,speed)

def validate(lv):
    errs=[]; warns=[]
    # spawn: 3 segmentos libres y 3 celdas libres por delante
    dx,dy=DX[lv.spawn_dir]
    body=[lv.wrap((lv.spawn[0]-dx*i, lv.spawn[1]-dy*i)) for i in range(3)]
    if any(c in lv.walls for c in body) or len(set(body))<3: errs.append("spawn sin espacio para 3 segmentos")
    c=lv.spawn
    for i in range(3):
        c=step(c,lv.spawn_dir,lv)
        if c is None: errs.append("spawn sin 3 celdas libres por delante"); break
    # spawn no mira a una apertura a < 3 celdas
    p=lv.spawn
    for i in range(1,4):
        n=(p[0]+dx,p[1]+dy)
        if not lv.inb(n):
            if is_open(lv.edge,EXIT[lv.spawn_dir],n): errs.append("spawn mira a una apertura a menos de 3 celdas")
            break
        p=n
    # aberturas: aterrizaje en suelo
    for y in range(lv.rows):
        for (xa,xb,side) in ((0,lv.cols-1,"LEFT"),):
            a=(0,y); b=(lv.cols-1,y)
            if is_open(lv.edge,"LEFT",(-1,y)):
                if (a in lv.walls)!=(b in lv.walls): errs.append(f"aterrizaje en pared (fila {y})")
    for x in range(lv.cols):
        a=(x,0); b=(x,lv.rows-1)
        if is_open(lv.edge,"TOP",(x,-1)):
            if (a in lv.walls)!=(b in lv.walls): errs.append(f"aterrizaje en pared (col {x})")
    for g in lv.edge.get("gaps",[]):
        if g["side"] in ("TOP","BOTTOM"):
            rng=range(g["from"],g["to"]+1)
            if g["from"]>g["to"] or g["from"]<0 or g["to"]>=lv.cols: errs.append("gap fuera de rango"); continue
            for x in rng:
                if (x,0) in lv.walls or (x,lv.rows-1) in lv.walls: errs.append(f"túnel sin suelo en col {x}")
        else:
            if g["from"]>g["to"] or g["from"]<0 or g["to"]>=lv.rows: errs.append("gap fuera de rango"); continue
            for y in range(g["from"],g["to"]+1):
                if (0,y) in lv.walls or (lv.cols-1,y) in lv.walls: errs.append(f"túnel sin suelo en fila {y}")
    # alcanzables
    seen={lv.spawn}; q=deque([lv.spawn])
    while q:
        c=q.popleft()
        for d in ORDER:
            n=step(c,d,lv)
            if n is not None and n not in seen: seen.add(n); q.append(n)
    unreach=[c for c in lv.floor if c not in seen]
    if unreach: errs.append(f"{len(unreach)} celdas de suelo inalcanzables (p. ej. {unreach[0]})")
    # espacio
    if lv.goal is not None:
        need=math.ceil((3+lv.goal)*1.4)
        if len(seen)<need: errs.append(f"espacio {len(seen)} < {need}")
    import os
    if os.environ.get("SKIPART"): return errs,warns,len(seen)
    # cuellos de botella (articulaciones)
    art=0
    for cell in seen:
        if cell==lv.spawn: continue
        s2={lv.spawn}; q=deque([lv.spawn])
        while q:
            c=q.popleft()
            for d in ORDER:
                n=step(c,d,lv)
                if n is not None and n!=cell and n not in s2: s2.add(n); q.append(n)
        if len(s2)<len(seen)-1: art+=1
    if art: warns.append(f"{art} cuellos de botella")
    return errs,warns,len(seen)
