#!/usr/bin/env python3
"""Inspect the actual merged release manifest; does not contact a device or grant permissions."""
import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('manifest', type=Path)
args = parser.parse_args()
root = ET.parse(args.manifest).getroot()
a = '{http://schemas.android.com/apk/res/android}'
app = root.find('application')
sdk = root.find('uses-sdk')
assert root.get('package') == 'com.sentinel.quantum'
assert sdk.get(a + 'minSdkVersion') == '24'
assert sdk.get(a + 'targetSdkVersion') == '36'
assert app.get(a + 'debuggable', 'false') == 'false'
assert app.get(a + 'allowBackup') == 'false'
assert app.get(a + 'usesCleartextTraffic') == 'false'
permissions = sorted(n.get(a + 'name') for n in root.findall('uses-permission'))
assert 'android.permission.CAMERA' not in permissions
assert 'android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION' not in permissions
services = {n.get(a + 'name'): n for n in app.findall('service')}
assert 'io.livekit.android.room.track.screencapture.ScreenCaptureService' not in services
service = services['com.sentinel.quantum.security.SentinelInCallService']
assert service.get(a + 'exported') == 'true'
assert service.get(a + 'permission') == 'android.permission.BIND_INCALL_SERVICE'
assert any(n.get(a + 'name') == 'android.telecom.IN_CALL_SERVICE_UI' and
           n.get(a + 'value') == 'true' for n in service.findall('meta-data'))
for name in ('SentinelSmsDeliverReceiver', 'SentinelMmsDeliverReceiver', 'SentinelMissedCallReceiver'):
    receiver = next(n for n in app.findall('receiver') if n.get(a + 'name').endswith('.' + name))
    assert receiver.get(a + 'permission'), name
exported = []
for tag in ('activity', 'activity-alias', 'service', 'receiver', 'provider'):
    for node in app.findall(tag):
        if node.get(a + 'exported') == 'true':
            exported.append({'type': tag, 'name': node.get(a + 'name'), 'permission': node.get(a + 'permission')})
for component in ('PhoneCoreActivationActivity', 'SentinelInCallActivity', 'PhoneCoreDiagnosticActivity'):
    node = next(n for n in app.findall('activity') if n.get(a + 'name').endswith('.' + component))
    assert node.get(a + 'exported') == 'false', component
print(json.dumps({'result': 'PASS', 'permissions': permissions, 'permission_count': len(permissions),
                  'exported_components': exported}, indent=2))
