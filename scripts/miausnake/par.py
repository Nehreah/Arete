import sys, json, os
os.environ["SKIPART"]="1"
from maps import *
from validate import *
import run_par as r
def one(i):
    g,sp,d,e=r.fs[i-1](); p=r.PACK[(i-1)//5]
    seed={9:7039}.get(i,7000+i)
    lv=build_level(f"maze_{i:02d}",g.rows(),e,sp,d,r.GOALS[i-1],seed,speed_table(*p))
    errs,warns,n=validate(lv)
    out={"i":i,"errs":errs,"floor":n}
    if not errs:
        st,_=bot_play(lv,seed=0)
        out.update(status=st.status,eaten=st.eaten,ticks=st.tick,ms=st.ms)
    json.dump(out,open(f"res/{i:02d}.json","w"))
    print(out,flush=True)
for i in [int(a) for a in sys.argv[1:]]: one(i)
