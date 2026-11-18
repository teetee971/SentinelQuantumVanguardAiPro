#!/usr/bin/env python3
"""Assert the Phone Core permissions, exports and exclusions in a merged V1 manifest."""
from argparse import ArgumentParser
from xml.etree import ElementTree

ANDROID = "{http://schemas.android.com/apk/res/android}"
COMPONENT_PREFIX = "com.sentinel.quantum."

parser = ArgumentParser(description=__doc__)
parser.add_argument("manifest")
parser.add_argument("package", choices=("com.sentinel.quantum", "com.sentinel.quantum.v1validation"))
args = parser.parse_args()
root = ElementTree.parse(args.manifest).getroot()
application = root.find("application")
assert application is not None

def attribute(element, name):
    return element.get(ANDROID + name)

permissions = {attribute(item, "name") for item in root.findall("uses-permission")}
components = {}
for component in application:
    if component.tag in ("activity", "activity-alias", "service", "receiver", "provider"):
        name = attribute(component, "name")
        if name.startswith("."):
            name = COMPONENT_PREFIX + name[1:]
        components[name] = component

errors = []

def require(condition, message):
    if not condition:
        errors.append(message)

require(root.get("package") == args.package, f"Unexpected package: {root.get('package')}")
require(attribute(application, "allowBackup") == "false", "Backup must be disabled")
require(attribute(application, "usesCleartextTraffic") == "false", "Cleartext traffic must be denied")
require(attribute(application, "networkSecurityConfig") is None, "Unexpected cleartext exception")
for name in ("RECORD_AUDIO", "CAMERA", "NEARBY_WIFI_DEVICES", "BLUETOOTH_SCAN", "BLUETOOTH_CONNECT",
             "BLUETOOTH", "BLUETOOTH_ADMIN", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION",
             "FOREGROUND_SERVICE_MEDIA_PROJECTION", "CHANGE_WIFI_STATE", "ACCESS_WIFI_STATE",
             "CHANGE_WIFI_MULTICAST_STATE", "USE_BIOMETRIC", "USE_FINGERPRINT"):
    require("android.permission." + name not in permissions, f"Optional permission remains: {name}")
for name in ("CALL_PHONE", "READ_CALL_LOG", "READ_PHONE_STATE", "READ_CONTACTS", "POST_NOTIFICATIONS",
             "USE_FULL_SCREEN_INTENT", "SEND_SMS", "RECEIVE_SMS", "READ_SMS", "RECEIVE_MMS", "RECEIVE_WAP_PUSH"):
    require("android.permission." + name in permissions, f"Core permission missing: {name}")
for name in ("VoiceStudioActivity", "ai.SentinelAiActivity", "security.SentinelWearListenerService"):
    require(COMPONENT_PREFIX + name not in components, f"Optional component remains: {name}")
for name in components:
    require(not name.startswith(("com.auth0.", "io.livekit.", "com.wireguard.", "androidx.credentials.",
                                  "com.google.android.gms.auth.")) and name != "androidx.activity.ComponentActivity",
            f"Optional SDK component remains: {name}")

expected_public = {
    "MainActivity": None,
    "SentinelDialerActivity": None,
    "SmsComposeActivity": None,
    "security.SentinelInCallService": "BIND_INCALL_SERVICE",
    "security.SentinelCallScreeningService": "BIND_SCREENING_SERVICE",
    "security.SentinelSmsDeliverReceiver": "BROADCAST_SMS",
    "security.SentinelMmsDeliverReceiver": "BROADCAST_WAP_PUSH",
    "security.SentinelRespondViaMessageService": "SEND_RESPOND_VIA_MESSAGE",
    "security.SentinelMissedCallReceiver": "MODIFY_PHONE_STATE",
}
for name, permission in expected_public.items():
    item = components.get(COMPONENT_PREFIX + name)
    require(item is not None and attribute(item, "exported") == "true", f"Core component missing: {name}")
    if item is not None:
        require(attribute(item, "permission") == (None if permission is None else "android.permission." + permission),
                f"Unexpected protection on {name}")
for name in ("security.SentinelCallActionReceiver", "security.SentinelMmsDownloadReceiver",
             "security.SentinelMmsSendStatusReceiver", "security.SentinelSmsStatusReceiver", "CallerIdActivity",
             "PhoneCoreActivationActivity", "PhoneCoreDiagnosticActivity", "SentinelInCallActivity"):
    item = components.get(COMPONENT_PREFIX + name)
    require(item is not None and attribute(item, "exported") == "false", f"Private component missing or exported: {name}")
allowed_exports = {COMPONENT_PREFIX + name for name in expected_public}
library_exports = {
    "androidx.work.impl.background.systemjob.SystemJobService": "android.permission.BIND_JOB_SERVICE",
    "androidx.profileinstaller.ProfileInstallReceiver": "android.permission.DUMP",
}
for name, component in components.items():
    if attribute(component, "exported") == "true" and name not in allowed_exports:
        require(name in library_exports and attribute(component, "permission") == library_exports.get(name),
                f"Unexpected export: {name}")
main = components.get(COMPONENT_PREFIX + "MainActivity")
if main is not None:
    actions = {attribute(action, "name") for action in main.findall("./intent-filter/action")}
    require(actions == {"android.intent.action.MAIN"}, f"Launcher exposes unexpected actions: {actions}")
require(any(
    any(attribute(action, "name") == "android.intent.action.EDIT" for action in intent.findall("action"))
    and any(attribute(data, "mimeType") == "vnd.android.cursor.item/contact" for data in intent.findall("data"))
    for intent in root.findall("./queries/intent")
), "System Contacts editor query is missing")
provider = components.get("androidx.core.content.FileProvider")
require(provider is not None and attribute(provider, "exported") == "false" and
        attribute(provider, "authorities") == args.package + ".fileprovider", "FileProvider isolation failed")

if errors:
    print(*errors, sep="\n")
    raise SystemExit(1)
print(f"PASS merged V1 manifest: {args.package}, {len(permissions)} permissions, {len(components)} components")
