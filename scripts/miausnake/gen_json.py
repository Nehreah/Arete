import json, os, glob
from maps import *
NAMES=["El patio","Doble carril","La cruz","Marco partido","Islas","El caracol","El anillo","El puente","La serpentina","El espejo","La huella","El pez","La carita","El ovillo","Mini laberinto","La bifurcación","El reloj","El ocho","El atajo falso","El gato se esconde"]
GOALS=[12,12,14,14,16,16,18,16,18,18,20,20,20,22,22,24,24,26,26,30]
PACK=[(260,140,12),(240,120,14),(220,110,16),(200,95,18)]
SEEDS={9:7039}
fs=[m01,m02,m03,m04,m05,m06,m07,m08,m09,m10,m11,m12,m13,m14,m15,m16,m17,m18,m19,m20]
HERE=os.path.dirname(os.path.abspath(__file__))
OUT=os.path.join(HERE,"..","..","app","src","main","assets","miausnake","levels")
os.chdir(HERE)
os.makedirs(OUT,exist_ok=True)
index=[]
for i in range(1,21):
    res=json.load(open(f"res/{i:02d}.json"))
    assert res["status"]=="Won" and not res["errs"], i
    g,sp,d,e=fs[i-1](); p=PACK[(i-1)//5]
    rows=[list(r) for r in g.rows()]
    rows[sp[1]][sp[0]]='S'
    par=int(round(res["ms"]*1.5/100.0))*100
    doc={"id":f"maze_{i:02d}","name":NAMES[i-1],"pack":(i-1)//5+1,"levelVersion":1,
      "grid":{"columns":W,"rows":H},"tileSize":16,
      "edge":{"default":e["default"],"wrapSides":e.get("wrapSides",[]),"gaps":e.get("gaps",[])},
      "spawn":{"x":sp[0],"y":sp[1],"direction":d},
      "seed":SEEDS.get(i,7000+i),"goal":{"bites":GOALS[i-1],"parMs":par},
      "speed":{"startMs":p[0],"minMs":p[1],"tau":p[2],"growth":1},
      "difficulty":(i-1)%5+1,
      "rows":[''.join(r) for r in rows]}
    fn=f"maze_{i:02d}.json"
    json.dump(doc,open(f"{OUT}/{fn}","w"),ensure_ascii=False,indent=1)
    index.append(fn)
    print(i,doc["goal"],res["ticks"],res["ms"])
json.dump({"format":1,"levels":index},open(f"{OUT}/index.json","w"),indent=1)
for pk in PACK:
    t=speed_table(*pk); print(pk,[t[n] for n in (0,4,8,12,20,40,200)])
