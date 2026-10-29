#!/usr/bin/env python3
"""Read-only S24 evidence snapshot. No calls, permission grants, serials or raw log files."""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import subprocess


def redact(text):
    text = re.sub(r'(?i)\b(?:https?://|tel:|smsto:|sms:|sip:)[^\s\]<>]+', '[URI MASQUÉE]', text)
    text = re.sub(r'\b[^\s@]+@[^\s@]+\.[^\s@]+\b', '[EMAIL MASQUÉ]', text)
    return re.sub(r'(?<!\w)\+?(?:\d[ ()\-.]?){6,}\d(?!\w)', '[NUMÉRO MASQUÉ]', text)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--adb', default='adb')
    args = parser.parse_args()
    os.umask(0o077)
    args.output.mkdir(parents=True, exist_ok=True, mode=0o700)
    commands = []

    def adb(*values):
        result = subprocess.run([args.adb, *values], text=True, capture_output=True, timeout=30)
        commands.append({'command': ['adb', *values], 'exit_code': result.returncode})
        return result

    def save_status(status, reason):
        (args.output / 'capture-status.json').write_text(json.dumps({
            'utc': datetime.now(timezone.utc).isoformat(), 'status': status, 'reason': reason,
            'commands': commands, 'physical_scenarios': 'NON TESTÉS: record manual observations separately'
        }, indent=2) + '\n')

    try:
        state = adb('get-state')
        if state.returncode != 0 or state.stdout.strip() != 'device':
            save_status('NON EXÉCUTÉE', 'No single authorized Android device available')
            print('NON EXÉCUTÉE : aucun appareil Android unique autorisé')
            return 2
        props = {}
        for key in ('ro.product.model', 'ro.build.version.release', 'ro.build.version.sdk'):
            result = adb('shell', 'getprop', key)
            if result.returncode:
                save_status('INCOMPLÈTE', 'Unable to read runtime version')
                return 3
            props[key] = redact(result.stdout.strip())
        (args.output / 'runtime.json').write_text(json.dumps(props, indent=2) + '\n')
        role = adb('shell', 'cmd', 'role', 'get-role-holders', 'android.app.role.DIALER')
        holders = [x for x in role.stdout.splitlines() if re.fullmatch(r'[A-Za-z0-9_.]+', x.strip())]
        (args.output / 'role-holder.json').write_text(json.dumps(holders, indent=2) + '\n')
        package = adb('shell', 'cmd', 'package', 'list', 'packages', '-U', 'com.sentinel.quantum')
        uid = re.search(r'^package:com\.sentinel\.quantum\s+uid:(\d+)\s*$', package.stdout, re.M)
        if uid:
            log = adb('logcat', '-d', '-t', '400', '--uid=' + uid.group(1), '-v', 'threadtime', '-s',
                      'SentinelPhoneRole:I', 'SentinelDialer:W', 'AndroidRuntime:E')
            # Redact in memory before writing; never write the unfiltered output.
            (args.output / 'phone-core-redacted.log').write_text(redact(log.stdout))
        if any(c['exit_code'] for c in commands):
            save_status('INCOMPLÈTE', 'At least one read-only command failed; inspect command exit codes')
            return 3
        if props['ro.build.version.release'] != '17' or props['ro.build.version.sdk'] != '37':
            save_status('HORS PÉRIMÈTRE', 'Runtime is not Android 17 / API 37')
            return 3
        save_status('COLLECTE EFFECTUÉE', 'No physical scenario is automatically marked PASS; review redaction before sharing')
        return 0
    except (OSError, subprocess.TimeoutExpired):
        save_status('NON EXÉCUTÉE', 'ADB unavailable or read-only command timed out')
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
