"""Emit every Kotlin string literal candidate and existing Android string resource.

This intentionally includes technical strings so no UI candidate is dropped by a
French-only heuristic. Review candidates before migrating them; protocol tokens,
log markers and test IDs are not translations. Locations remain reproducible.
"""
import json
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

root = pathlib.Path(__file__).resolve().parent.parent
base = root / 'native-android-app/app/src/main'
entries = []
pattern = re.compile(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"')
for file in sorted((base / 'java').rglob('*.kt')):
    text = file.read_text(encoding='utf-8')
    for match in pattern.finditer(text):
        entries.append({'path': str(file.relative_to(root)),
                        'line': text.count('\n', 0, match.start()) + 1,
                        'literal_candidate': match.group()})
resources = []
for file in sorted((base / 'res').glob('values*/*.xml')):
    for element in ET.parse(file).getroot():
        if element.tag in ('string', 'plurals', 'string-array'):
            resources.append({'path': str(file.relative_to(root)),
                              'type': element.tag, 'name': element.get('name')})
json.dump({'scope': 'all app main Kotlin literals, including technical/comment candidates; all values resources',
           'literal_candidates': entries, 'resources': resources}, sys.stdout, ensure_ascii=False, indent=2)
print()
