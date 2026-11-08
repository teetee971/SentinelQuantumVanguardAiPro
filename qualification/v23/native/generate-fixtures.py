#!/usr/bin/env python3
"""Generate deterministic host JPEG fixtures with the vendored upstream cjpeg tool."""
from pathlib import Path
import argparse, subprocess
p=argparse.ArgumentParser();p.add_argument('cjpeg');p.add_argument('output');a=p.parse_args()
out=Path(a.output);out.mkdir(parents=True,exist_ok=True)
for name,w,h,args in [
 ('progressive-420-12mp',4000,3000,['-progressive','-sample','2x2']),
 ('progressive-444-limit',4000,3000,['-progressive','-sample','1x1']),
 ('restart',96,64,['-restart','1B']),('baseline',800,600,[])]:
 ppm=out/(name+'.ppm')
 with ppm.open('wb') as f:
  f.write(f'P6\n{w} {h}\n255\n'.encode())
  for y in range(h):f.write(bytes(c for x in range(w) for c in ((x*17+y*13)%256,(x*7+y*19)%256,(x*23+y*3)%256)) if name=='baseline' else bytes((40,120,200))*w)
 subprocess.run([a.cjpeg,*args,'-quality','90','-outfile',str(out/(name+'.jpg')),str(ppm)],check=True)
 ppm.unlink()
