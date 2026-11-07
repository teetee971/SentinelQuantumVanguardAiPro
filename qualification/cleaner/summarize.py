from pathlib import Path
import json,hashlib,sys,xml.etree.ElementTree as ET
r=Path(sys.argv[1]);statuses={x.split('\t')[0]:int(x.split('\t')[1]) for x in (r/'STATUS.tsv').read_text().splitlines()};tests={}
for d in r.glob('*-junit'):
 n={k:0 for k in ('tests','failures','errors','skipped')};failed=[]
 for p in d.glob('*.xml'):
  s=ET.parse(p).getroot()
  for k in n:n[k]+=int(s.get(k,0))
  for c in s.findall('testcase'):
   if c.find('failure') is not None or c.find('error') is not None:failed.append(s.get('name')+'.'+c.get('name'))
 tests[d.name]={'counts':n,'failed':failed}
lint={}
if (r/'lint.xml').exists():
 for i in ET.parse(r/'lint.xml').getroot().findall('issue'):lint[i.get('severity')]=lint.get(i.get('severity'),0)+1
artifacts={p.name:{'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in r.glob('*.apk')}
l={'source_sha':(r/'SOURCE_SHA').read_text().strip(),'android_tree':(r/'ANDROID_TREE').read_text().strip(),'exit_codes':statuses,'tests':tests,'lint':lint,'artifacts':artifacts,'physical':'NOT_EXECUTED','installation':'NOT_PERFORMED'}
(r/'VALIDATION_LEDGER.json').write_text(json.dumps(l,indent=2)+'\n');print(json.dumps(l,indent=2));sys.exit(1 if any(statuses.values()) else 0)
