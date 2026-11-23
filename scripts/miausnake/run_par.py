import sys, json
from multiprocessing import Pool
from maps import *
from validate import *
GOALS=[12,12,14,14,16,16,18,16,18,18,20,20,20,22,22,24,24,26,26,30]
PACK=[(260,140,12),(240,120,14),(220,110,16),(200,95,18)]
fs=[m01,m02,m03,m04,m05,m06,m07,m08,m09,m10,m11,m12,m13,m14,m15,m16,m17,m18,m19,m20]
N=int(sys.argv[1]) if len(sys.argv)>1 else 8
def work(i):
    g,sp,d,e=fs[i-1](); p=PACK[(i-1)//5]
    lv=build_level(f"m{i:02d}",g.rows(),e,sp,d,GOALS[i-1],7000+i,speed_table(*p))
    errs,warns,n=validate(lv)
    if errs: return f"{i:02d} floor={n} ERR {errs}"
    wins=0; dead=[]
    for s in range(N):
        st,_=bot_play(lv,seed=1000+s)
        if st.status=="Won": wins+=1
        else: dead.append(st.eaten)
    return f"{i:02d} floor={n:3d} goal={GOALS[i-1]} wins={wins}/{N} dead_at={dead} warns={warns}"
if __name__=="__main__":
    ids=[int(a) for a in sys.argv[2:]] or list(range(1,21))
    with Pool(8) as p:
        for r in p.imap(work,ids): print(r,flush=True)
