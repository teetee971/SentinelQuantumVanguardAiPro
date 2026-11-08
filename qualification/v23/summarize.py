"""Summarize executed outputs. Never converts failed/ignored tests into delivery readiness."""
from pathlib import Path
import hashlib
import json
import sys
import xml.etree.ElementTree as ET

root=Path(sys.argv[1])
statuses={line.split('\t')[0]:int(line.split('\t')[1]) for line in (root/'STATUS.tsv').read_text().splitlines()}
tests={}
for folder in sorted(root.glob('*-junit')):
    counts={key:0 for key in ('tests','failures','errors','skipped')}
    failed=[]
    for xml in folder.glob('*.xml'):
        suite=ET.parse(xml).getroot()
        for key in counts:counts[key]+=int(suite.get(key,'0'))
        for case in suite.findall('testcase'):
            if case.find('failure') is not None or case.find('error') is not None:
                failed.append(suite.get('name','')+'.'+case.get('name',''))
    tests[folder.name]={'counts':counts,'failing_cases':failed}
lint={}
for folder in root.glob('*-lint'):
    path=folder/'lint-results-debug.xml'
    if path.exists():
        by_severity={};by_id={}
        for item in ET.parse(path).getroot().findall('issue'):
            level=item.get('severity');identifier=item.get('id')
            by_severity[level]=by_severity.get(level,0)+1;by_id[identifier]=by_id.get(identifier,0)+1
        lint[folder.name]={'by_severity':by_severity,'by_id':by_id}
artifacts={p.name:{'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in root.glob('*.apk')}
report={'source_sha':(root/'SOURCE_SHA').read_text().strip(),'android_tree':(root/'ANDROID_TREE').read_text().strip(),
        'commands_exit_status':statuses,'tests':tests,'lint':lint,'artifacts':artifacts,
        'automated_status':'FAIL' if any(statuses.values()) else 'PASS',
        'delivery_status':'BLOCKED','physical_v23':'NOT_EXECUTED','production_installation':'NOT_PERFORMED'}
(root/'VALIDATION_LEDGER.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
sys.exit(1 if any(statuses.values()) else 0)
