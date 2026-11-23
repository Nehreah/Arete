import random, math
from sim import *
W,H=20,28
class G:
    def __init__(s,fill='.'): s.g=[[fill]*W for _ in range(H)]
    def rect(s,x0,y0,x1,y1,c='#'):
        for y in range(y0,y1+1):
            for x in range(x0,x1+1):
                if 0<=x<W and 0<=y<H: s.g[y][x]=c
    def ring(s,x0,y0,x1,y1):
        s.rect(x0,y0,x1,y0); s.rect(x0,y1,x1,y1); s.rect(x0,y0,x0,y1); s.rect(x1,y0,x1,y1)
    def ell(s,cx,cy,rx,ry,c='#',fill=True,thick=None):
        for y in range(H):
            for x in range(W):
                v=((x-cx)/rx)**2+((y-cy)/ry)**2
                if fill and v<=1: s.g[y][x]=c
    def ellring(s,cx,cy,rx,ry,t=1.0):
        for y in range(H):
            for x in range(W):
                v=((x-cx)/rx)**2+((y-cy)/ry)**2
                vi=((x-cx)/(rx-t))**2+((y-cy)/(ry-t*1.0))**2
                if v<=1 and vi>1: s.g[y][x]='#'
    def rows(s): return [''.join(r) for r in s.g]

def m01():
    g=G()
    g.rect(3,6,4,7); g.rect(14,12,15,13); g.rect(7,21,8,22)
    return g,(10,14),'RIGHT',{'default':'WRAP'}
def m02():
    g=G()
    g.rect(3,3,3,24); g.rect(16,3,16,24); g.rect(9,3,10,24)
    for y0 in (8,18): g.rect(9,y0,10,y0+1,'.')
    return g,(6,14),'UP',{'default':'WRAP'}
def m03():
    g=G()
    g.rect(9,3,10,24); g.rect(2,13,17,14)
    return g,(4,8),'RIGHT',{'default':'WRAP'}
def m04():
    g=G()
    g.ring(4,6,15,21)
    g.rect(6,6,7,6,'.'); g.rect(12,21,13,21,'.'); g.rect(4,11,4,12,'.'); g.rect(15,16,15,17,'.')
    return g,(9,15),'UP',{'default':'WRAP'}
def m05():
    g=G()
    g.rect(3,4,4,5); g.rect(14,5,16,5); g.rect(8,10,8,12); g.rect(2,16,3,17); g.rect(13,16,14,18); g.rect(6,23,8,23); g.rect(7,24,7,24)
    return g,(10,14),'RIGHT',{'default':'WRAP'}
def m06():
    g=G()
    g.ring(3,3,16,24); g.ring(7,7,12,20)
    g.rect(9,24,10,24,'.')      # ring exterior: abre abajo
    g.rect(9,7,10,7,'.')        # ring interior: abre arriba
    return g,(1,8),'DOWN',{'default':'SOLID'}
def m07():
    g=G()
    g.rect(6,8,13,19)
    g.rect(2,5,8,5); g.rect(11,22,17,22)
    return g,(3,12),'UP',{'default':'SOLID'}
def m08():
    g=G()
    g.rect(0,13,19,14); g.rect(3,13,4,14,'.'); g.rect(15,13,16,14,'.')
    return g,(10,5),'RIGHT',{'default':'SOLID'}
def m09():
    g=G()
    for i,y in enumerate((5,10,15,20)):
        if i%2==0: g.rect(0,y,16,y)
        else: g.rect(3,y,19,y)
    return g,(3,2),'RIGHT',{'default':'SOLID'}
def m10():
    g=G()
    blocks=[(2,3,4,5),(2,9,6,10),(5,14,7,17),(2,20,5,21),(1,24,3,24)]
    for (x0,y0,x1,y1) in blocks:
        g.rect(x0,y0,x1,y1); g.rect(19-x1,y0,19-x0,y1)
    return g,(9,22),'UP',{'default':'SOLID'}
def m11():
    g=G()
    g.ell(9.5,19,4.6,4.2)
    for (cx,cy,rx,ry) in [(3.8,11.2,1.8,2.4),(7.6,7.4,1.8,2.4),(12.4,7.4,1.8,2.4),(16.2,11.2,1.8,2.4)]:
        g.ell(cx,cy,rx,ry)
    return g,(3,22),'RIGHT',{'default':'SOLID','wrapSides':['LEFT','RIGHT']}
def m12():
    g=G('#')
    g.rect(8,0,11,27,'.')
    for (y0,y1) in ((2,7),(11,16),(20,25)):
        g.rect(0,y0,6,y1,'.'); g.rect(13,y0,19,y1,'.')
        g.rect(6,y0+2,8,y0+3,'.'); g.rect(11,y0+2,13,y0+3,'.')
    return g,(9,22),'UP',{'default':'SOLID','wrapSides':['LEFT','RIGHT']}
def m13():
    g=G()
    g.ring(2,3,7,9); g.rect(4,9,5,9,'.')
    g.ring(12,3,17,9); g.rect(14,9,15,9,'.')
    g.ring(7,16,12,21); g.rect(9,16,10,16,'.')
    return g,(10,12),'DOWN',{'default':'SOLID','wrapSides':['TOP','BOTTOM']}
def m14():
    g=G()
    g.ellring(9.5,13.5,9.3,13.3,1.2)
    g.ellring(9.5,13.5,5.2,8.4,1.2)
    g.rect(1,12,2,15,'.')      # anillo exterior: abre a la izquierda
    g.rect(8,5,11,6,'.')       # anillo interior: abre arriba
    return g,(0,6),'DOWN',{'default':'SOLID','wrapSides':['LEFT','RIGHT']}
def m15():
    rnd=random.Random(1503)
    CW,CH=6,9
    g=G('#')
    def cell_xy(cx,cy): return 1+cx*3, 1+cy*3
    def carve_cell(cx,cy):
        x,y=cell_xy(cx,cy); g.rect(x,y,x+1,y+1,'.')
    def open_between(a,b):
        (ax,ay),(bx,by)=cell_xy(*a),cell_xy(*b)
        if ax==bx: g.rect(ax,min(ay,by)+2,ax+1,min(ay,by)+2,'.')
        else: g.rect(min(ax,bx)+2,ay,min(ax,bx)+2,ay+1,'.')
    seen={(0,0)}; stack=[(0,0)]; carve_cell(0,0)
    edges=set()
    while stack:
        c=stack[-1]
        nb=[(c[0]+dx,c[1]+dy) for dx,dy in ((1,0),(-1,0),(0,1),(0,-1)) if 0<=c[0]+dx<CW and 0<=c[1]+dy<CH and (c[0]+dx,c[1]+dy) not in seen]
        if not nb: stack.pop(); continue
        n=rnd.choice(nb); seen.add(n); carve_cell(*n); open_between(c,n); edges.add((c,n)); edges.add((n,c)); stack.append(n)
    # bucles: abre paredes extra
    cand=[]
    for cy in range(CH):
        for cx in range(CW):
            for dx,dy in ((1,0),(0,1)):
                n=(cx+dx,cy+dy)
                if n[0]<CW and n[1]<CH and ((cx,cy),n) not in edges: cand.append(((cx,cy),n))
    rnd.shuffle(cand)
    for a,b in cand[:14]: open_between(a,b)
    g.rect(4,0,5,0,'.'); g.rect(16,0,17,0,'.'); g.rect(4,27,5,27,'.'); g.rect(16,27,17,27,'.')
    return g,(4,13),'DOWN',{'default':'SOLID','wrapSides':['TOP','BOTTOM']}
def m16():
    g=G()
    g.rect(4,10,15,10); g.rect(4,17,15,17)
    g.rect(9,3,10,8); g.rect(9,19,10,24)
    return g,(10,13),'LEFT',{'default':'SOLID','gaps':[{'side':'LEFT','from':13,'to':14}]}
def m17():
    g=G()
    g.ellring(9.5,13.5,7.6,10.6,1.2)
    g.rect(9,2,10,5,'.'); g.rect(9,22,10,25,'.'); g.rect(1,13,3,14,'.'); g.rect(16,13,18,14,'.')
    g.rect(9,13,10,14)
    return g,(10,24),'UP',{'default':'SOLID','gaps':[{'side':'TOP','from':9,'to':10},{'side':'LEFT','from':13,'to':14}]}
def m18():
    g=G('#')
    g.ell(9.5,7,8.6,6.2,'.'); g.ell(9.5,20,8.6,6.2,'.')
    g.rect(8,12,11,15,'.'); g.rect(9,0,10,3,'.'); g.rect(9,24,10,27,'.')
    return g,(10,18),'UP',{'default':'SOLID','gaps':[{'side':'TOP','from':9,'to':10}]}
def m19():
    g=G()
    g.rect(15,3,15,10); g.rect(15,3,19,3); g.rect(15,10,19,10)
    g.rect(7,13,12,13); g.rect(6,20,13,20)
    g.rect(3,16,3,19)
    return g,(6,17),'RIGHT',{'default':'SOLID','gaps':[{'side':'LEFT','from':6,'to':7},{'side':'TOP','from':4,'to':5}]}
def m20():
    g=G()
    g.ellring(9.5,15.5,8.6,10.6,1.2)
    g.rect(0,12,2,13,'.'); g.rect(17,12,19,13,'.'); g.rect(0,17,2,18,'.'); g.rect(17,17,19,18,'.')
    g.rect(1,12,1,13,'.')
    g.rect(2,3,3,5); g.rect(16,3,17,5)           # orejas
    g.rect(6,12,7,13); g.rect(12,12,13,13)       # ojos
    g.rect(9,16,10,16)                            # nariz
    g.rect(8,20,11,20)                            # boca
    return g,(10,10),'DOWN',{'default':'SOLID','gaps':[{'side':'LEFT','from':12,'to':13},{'side':'LEFT','from':17,'to':18}]}

def show(name,g):
    print(name)
    for i,r in enumerate(g.rows()): print(f"{i:2d} {r}")
if __name__=="__main__":
    for n,f in [('01',m01),('02',m02),('03',m03),('04',m04),('05',m05),('06',m06),('07',m07),('08',m08),('09',m09),('10',m10)]:
        g,sp,d,e=f(); 
        gg=[r[:] for r in g.g]
        x,y=sp; gg[y][x]='S'
        print(n)
        for i,r in enumerate(gg): print(f"{i:2d} {''.join(r)}")
