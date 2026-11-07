#!/usr/bin/env python3
"""Read-only candidate-specific ELF/manifest checks; not a Play approval gate."""
import argparse
import json
import struct
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('aab', type=Path)
p.add_argument('debug_apk', type=Path)
p.add_argument('bundle_config', type=Path)
p.add_argument('bundle_manifest', type=Path)
p.add_argument('report', type=Path)
a = p.parse_args()
config = json.loads(a.bundle_config.read_text())
assert config['optimizations']['uncompressNativeLibraries']['alignment'] == 'PAGE_ALIGNMENT_16K'
manifest = ET.fromstring(a.bundle_manifest.read_text())
ns = '{http://schemas.android.com/apk/res/android}'
assert manifest.get('package') == 'com.sentinel.quantum'
assert manifest.get(ns + 'versionCode') == '24'
assert manifest.get(ns + 'versionName') == '1.0.10'
assert manifest.find('uses-sdk').get(ns + 'targetSdkVersion') == '36'
assert manifest.find('application').get(ns + 'debuggable', 'false') == 'false'
assert not any(n.get(ns + 'name') == 'com.google.android.gms.permission.AD_ID'
               for n in manifest.findall('uses-permission'))
rows = []
with zipfile.ZipFile(a.debug_apk) as apk, zipfile.ZipFile(a.aab) as bundle:
    for name in bundle.namelist():
        if not name.startswith(('base/lib/arm64-v8a/', 'base/lib/x86_64/')) or not name.endswith('.so'):
            continue
        data = bundle.read(name)
        assert data == apk.read(name[5:]), name
        assert data[:5] == b'\x7fELF\x02' and data[5] in (1, 2), name
        endian = '<' if data[5] == 1 else '>'
        offset = struct.unpack_from(endian + 'Q', data, 32)[0]
        size, count = struct.unpack_from(endian + 'HH', data, 54)
        assert size >= 56 and offset + size * count <= len(data), name
        aligns = []
        for i in range(count):
            header = offset + i * size
            if struct.unpack_from(endian + 'I', data, header)[0] != 1:
                continue
            file_offset, address = struct.unpack_from(endian + 'QQ', data, header + 8)
            alignment = struct.unpack_from(endian + 'Q', data, header + 48)[0]
            assert alignment >= 16384 and alignment & (alignment - 1) == 0, name
            assert file_offset % 16384 == address % 16384, name
            aligns.append(alignment)
        assert aligns, name
        rows.append({'file': name, 'load_segment_alignments': aligns,
                     'debug_apk_bytes_identical': True})
assert rows, 'No 64-bit native library was inspected'
a.report.write_text(json.dumps(rows, indent=2) + '\n')
print(f'PASS static bundle checks: {len(rows)} 64-bit ELF libraries. Runtime NOT EXECUTED.')
