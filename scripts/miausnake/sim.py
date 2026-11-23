# Port fiel del core de Miau Snake (Kotlin) a Python, para diseñar y validar mapas.
import math, json
from collections import deque
M64 = (1<<64)-1
GOLDEN = 0x9E3779B97F4A7C15
DIRS = [("UP",0,-1),("DOWN",0,1),("LEFT",-1,0),("RIGHT",1,0)]
DX = {"UP":(0,-1),"DOWN":(0,1),"LEFT":(-1,0),"RIGHT":(1,0)}
OPP = {"UP":"DOWN","DOWN":"UP","LEFT":"RIGHT","RIGHT":"LEFT"}
EXIT = {"UP":"TOP","DOWN":"BOTTOM","LEFT":"LEFT","RIGHT":"RIGHT"}
SOPP = {"TOP":"BOTTOM","BOTTOM":"TOP","LEFT":"RIGHT","RIGHT":"LEFT"}
ORDER = ["UP","DOWN","LEFT","RIGHT"]

def seed64(s): 
    s &= M64
    return GOLDEN if s==0 else s
def nxt(s):
    x=s
    x ^= x>>12
    x ^= (x<<25)&M64
    x ^= x>>27
    return x&M64
def output(s): return (s*2685821657736338717)&M64
def to_signed(v): return v-(1<<64) if v>=(1<<63) else v
def spawn_food(free, rng):
    if not free: return None, rng
    s = nxt(rng)
    n=len(free)
    o = to_signed(output(s))
    idx = ((o % n)+n)%n   # Kotlin % trunca hacia 0 con signo
    # Kotlin: ((o % n) + n) % n  con % de Kotlin (resto con signo del dividendo)
    r = abs(o)%n
    r = -r if o<0 else r
    idx = ((r+n)%n)
    return free[idx], s

class Level:
    def __init__(s, id, cols, rows, edge, walls, spawn, spawn_dir, goal, seed, speed, version=1):
        s.id=id; s.cols=cols; s.rows=rows; s.edge=edge; s.walls=set(walls)
        s.spawn=spawn; s.spawn_dir=spawn_dir; s.goal=goal; s.fixed_seed=seed; s.speed=speed; s.version=version
        s.floor=[(x,y) for y in range(rows) for x in range(cols) if (x,y) not in s.walls]
    def inb(s,c): return 0<=c[0]<s.cols and 0<=c[1]<s.rows
    def wrap(s,c): return (c[0]%s.cols, c[1]%s.rows)

def is_open(edge, side, outside):
    if edge["default"]=="WRAP": return True
    ws=set(edge.get("wrapSides",[]))
    if side in ws or SOPP[side] in ws: return True
    pos = outside[0] if side in ("TOP","BOTTOM") else outside[1]
    for g in edge.get("gaps",[]):
        if (g["side"]==side or g["side"]==SOPP[side]) and g["from"]<=pos<=g["to"]: return True
    return False

def step(c,d,lv):
    dx,dy=DX[d]; n=(c[0]+dx,c[1]+dy)
    if lv.inb(n): return None if n in lv.walls else n
    if not is_open(lv.edge, EXIT[d], n): return None
    w=lv.wrap(n)
    return None if w in lv.walls else w

def speed_table(start,minms,tau,maxn=200):
    return [int(minms+(start-minms)*math.exp(-n/tau)+0.5) for n in range(maxn+1)]

class State: pass
def initial(lv, seed):
    sx,sy=lv.spawn; dx,dy=DX[lv.spawn_dir]
    snake=[lv.wrap((sx-dx*i, sy-dy*i)) for i in range(3)]
    assert not any(c in lv.walls for c in snake) and len(set(snake))==3
    occ=set(snake)
    food,rng=spawn_food([c for c in lv.floor if c not in occ], seed64(lv.fixed_seed if lv.fixed_seed is not None else seed))
    st=State(); st.snake=snake; st.dir=lv.spawn_dir; st.queue=[]; st.food=food; st.eaten=0
    st.score=0; st.tick=0; st.ms=0; st.rng=rng; st.status="Running"
    return st

def apply_tick(st, lv):
    if st.status!="Running": return
    d = st.queue[0] if st.queue else st.dir
    nx = step(st.snake[0], d, lv)
    if nx is None: st.status="GameOver"; return
    eats = nx==st.food
    body = st.snake if eats else st.snake[:-1]
    if nx in body: st.status="GameOver"; return
    new=[nx]+body
    st.ms += lv.speed[min(st.eaten, len(lv.speed)-1)]
    eaten = st.eaten+(1 if eats else 0)
    st.snake=new; st.dir=d; st.queue=st.queue[1:]; st.eaten=eaten
    st.score += 10 if eats else 0; st.tick+=1
    if not eats: return
    if lv.goal is not None and eaten>=lv.goal: st.status="Won"; return
    occ=set(new)
    cell,r2=spawn_food([c for c in lv.floor if c not in occ], st.rng)
    st.rng=r2
    if cell is None: st.status="Won"
    else: st.food=cell

# ---- Bot BFS (misma lógica que BfsBot.kt) ----
def shortest_path(start,goal,lv,blocked,forbidden_first):
    parent={}; seen={start}; q=deque([start])
    while q:
        c=q.popleft()
        if c==goal: break
        for d in ORDER:
            if c==start and d==forbidden_first: continue
            n=step(c,d,lv)
            if n is None or n in seen or n in blocked: continue
            seen.add(n); parent[n]=(c,d); q.append(n)
    if goal not in seen: return None
    path=[]; c=goal
    while c!=start:
        p,d=parent[c]; path.append((d,c)); c=p
    path.reverse(); return path
def can_reach_tail(snake,lv):
    head=snake[0]; tail=snake[-1]; body=set(snake[:-1]); seen={head}; q=deque([head])
    while q:
        c=q.popleft()
        for d in ORDER:
            n=step(c,d,lv)
            if n is None: continue
            if n==tail: return True
            if n in body or n in seen: continue
            seen.add(n); q.append(n)
    return False
def area(start,lv,blocked):
    seen={start}; q=deque([start])
    while q:
        c=q.popleft()
        for d in ORDER:
            n=step(c,d,lv)
            if n is None or n in blocked or n in seen: continue
            seen.add(n); q.append(n)
    return len(seen)
def next_dir(st,lv):
    snake=st.snake; head=snake[0]; blocked=set(snake[:-1]); back=OPP[st.dir]
    path=shortest_path(head,st.food,lv,blocked,back)
    if path:
        after=([c for _,c in path][::-1]+snake)[:len(snake)+1]
        if can_reach_tail(after,lv): return path[0][0]
    best=None; best_tail=False; best_area=-1
    for d in ORDER:
        if d==back: continue
        n=step(head,d,lv)
        if n is None or n in blocked: continue
        moved=[n]+snake[:-1]
        tail=can_reach_tail(moved,lv)
        libre=area(n,lv,blocked|{n})
        better = best is None or (tail and not best_tail) or (tail==best_tail and libre>best_area)
        if better: best=d; best_tail=tail; best_area=libre
    return best if best else st.dir

def enqueue(q,cur,d):
    ref=q[-1] if q else cur
    return q if (len(q)>=2 or d==ref or d==OPP[ref]) else q+[d]

def bot_play(lv, seed=0, max_ticks=200000):
    st=initial(lv,seed); turns=[]
    while st.status=="Running" and st.tick<max_ticks:
        d=next_dir(st,lv)
        if d!=st.dir: st.queue=enqueue(st.queue,st.dir,d)
        consumed = st.queue[0] if st.queue else None
        if consumed is not None and consumed!=st.dir: turns.append((st.tick,consumed))
        apply_tick(st,lv)
    return st,turns
