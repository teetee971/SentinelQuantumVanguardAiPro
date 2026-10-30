#!/usr/bin/env python3
"""Read-only preflight. Never installs, uninstalls, clears data, grants roles or reads keys."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile

PACKAGE = 'com.sentinel.quantum'


def command(args):
    result = subprocess.run([str(x) for x in args], capture_output=True, text=True, timeout=90)
    if result.returncode:
        raise ValueError('READ_OR_VERIFICATION_FAILED')
    return result.stdout


def inspect(apk, build_tools):
    certs = command([build_tools / 'apksigner', 'verify', '--verbose', '--print-certs', apk])
    digests = sorted(set(re.findall(r'Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]{64})', certs)))
    badging = command([build_tools / 'aapt', 'dump', 'badging', apk])
    package = re.search(r"^package: name='([^']+)' versionCode='([0-9]+)'", badging, re.M)
    if not digests or not package:
        raise ValueError('APK_METADATA_UNAVAILABLE')
    return dict(package=package.group(1), version_code=int(package.group(2)),
                certificate_sha256=[s.lower() for s in digests], sha256=hashlib.sha256(apk.read_bytes()).hexdigest())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--candidate', type=Path, required=True)
    parser.add_argument('--build-tools', type=Path, required=True)
    parser.add_argument('--installed-apk', type=Path, help='Optional previously extracted base APK; not proof of live device state')
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial', help='Select an authorized device; never written to the report')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    report = dict(status='UNVERIFIED', installed_device_verified=False, installation_performed=False)
    code = 2
    try:
        report['candidate'] = inspect(args.candidate, args.build_tools)
        with tempfile.TemporaryDirectory(prefix='sentinel-signature-') as directory:
            installed = args.installed_apk
            if installed is None:
                if not args.serial:
                    raise ValueError('EXPLICIT_DEVICE_SERIAL_REQUIRED')
                adb = [args.adb, '-s', args.serial]
                if command(adb + ['get-state']).strip() != 'device':
                    raise ValueError('NO_UNIQUE_AUTHORIZED_DEVICE')
                user = command(adb + ['shell', 'am', 'get-current-user']).strip()
                if not user.isdigit():
                    raise ValueError('ANDROID_USER_UNAVAILABLE')
                paths = command(adb + ['shell', 'pm', 'path', '--user', user, PACKAGE]).splitlines()
                paths = [p.removeprefix('package:').strip() for p in paths if p.startswith('package:')]
                bases = [p for p in paths if p.endswith('/base.apk')]
                path = bases[0] if len(bases) == 1 else paths[0] if len(paths) == 1 else ''
                if not re.fullmatch(r'/[A-Za-z0-9_./=+~:@%\-]+\.apk', path):
                    raise ValueError('INSTALLED_BASE_APK_UNAVAILABLE')
                installed = Path(directory) / 'installed-base.apk'
                command(adb + ['pull', path, str(installed)])
                report['reference_source'] = 'CONNECTED_DEVICE_BASE_APK'
            else:
                report['reference_source'] = 'LOCAL_APK_ONLY_NOT_LIVE_DEVICE'
            report['reference'] = inspect(installed, args.build_tools)
            a, b = report['candidate'], report['reference']
            if a['package'] != PACKAGE or b['package'] != PACKAGE:
                report.update(status='INCOMPATIBLE', reason='PACKAGE_MISMATCH'); code = 4
            elif a['certificate_sha256'] != b['certificate_sha256']:
                report.update(status='INCOMPATIBLE', reason='CERTIFICATE_MISMATCH_OR_LINEAGE_REQUIRES_SEPARATE_REVIEW'); code = 4
            elif a['version_code'] < b['version_code']:
                report.update(status='INCOMPATIBLE', reason='VERSION_DOWNGRADE'); code = 4
            else:
                report.update(status='MATCH', reason='SAME_CERTIFICATE_SET_AND_NONDECREASING_VERSION'); code = 0
            report['installed_device_verified'] = report['reference_source'] == 'CONNECTED_DEVICE_BASE_APK'
    except (ValueError, OSError, subprocess.TimeoutExpired) as error:
        # No raw subprocess stderr, file paths or device identifiers in the shareable report.
        report['reason'] = str(error) if isinstance(error, ValueError) else 'TOOL_OR_INPUT_UNAVAILABLE'
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))
    return code


if __name__ == '__main__':
    raise SystemExit(main())
