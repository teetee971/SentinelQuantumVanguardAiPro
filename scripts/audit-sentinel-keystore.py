#!/usr/bin/env python3
"""Read a public certificate in place. No export, key generation, copying or signing."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess

EXPECTED = '68915958f2f8683afead51cc3a1e578d182df4b9d9b2505c48c261b1a17b3fe2'


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--keystore', type=Path, required=True)
    p.add_argument('--keytool', default='keytool')
    p.add_argument('--alias', default='androiddebugkey')
    p.add_argument('--expected', default=EXPECTED)
    passwords = p.add_mutually_exclusive_group(required=True)
    passwords.add_argument('--debug-default-password', action='store_true', help='Use only the conventional Android debug-store password, never guess a custom password')
    passwords.add_argument('--password-env', help='Name of an existing local password variable; value is never printed')
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    if args.output.suffix != '.json' or args.output.is_symlink() or args.output.resolve() == args.keystore.resolve():
        p.error('output must be a JSON report distinct from the keystore and not a symlink')
    report = dict(status='UNVERIFIED', private_key_copied=False, private_key_exported=False,
                  signing_performed=False, expected_certificate_sha256=args.expected.lower())
    code = 2
    try:
        if not re.fullmatch(r'[A-Fa-f0-9]{64}', args.expected):
            raise ValueError('INVALID_PUBLIC_FINGERPRINT')
        env = os.environ.copy()
        password = 'android' if args.debug_default_password else env.get(args.password_env, '')
        if not password:
            raise ValueError('LOCAL_PASSWORD_UNAVAILABLE')
        env['SENTINEL_LOCAL_KEYSTORE_AUDIT_PASSWORD'] = password
        command = [args.keytool, '-J-Duser.language=en', '-J-Duser.country=US', '-list', '-v',
                   '-keystore', str(args.keystore), '-alias', args.alias,
                   '-storepass:env', 'SENTINEL_LOCAL_KEYSTORE_AUDIT_PASSWORD']
        result = subprocess.run(command, env=env, capture_output=True, text=True, timeout=30)
        report['keytool_exit'] = result.returncode
        if result.returncode:
            raise ValueError('STORE_OR_ALIAS_UNREADABLE_WITH_PROVIDED_LOCAL_CREDENTIAL')
        certificates = [s.replace(':', '').lower() for s in re.findall(r'SHA256:\s*([A-Fa-f0-9:]+)', result.stdout)]
        # Only the leaf certificate identifies the selected signing key; chain roots do not.
        leaf = certificates[0] if certificates else None
        private_entry = 'Entry type: PrivateKeyEntry' in result.stdout
        report.update(certificate_sha256=leaf, private_key_entry_present=private_entry)
        if leaf == args.expected.lower() and private_entry:
            report['status'] = 'MATCH_PRIVATE_ENTRY'; code = 0
        else:
            report['status'] = 'MISMATCH_OR_NO_PRIVATE_ENTRY'; code = 4
    except (OSError, subprocess.TimeoutExpired, ValueError) as error:
        report['reason'] = str(error) if isinstance(error, ValueError) else 'LOCAL_TOOL_UNAVAILABLE'
    try:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        # Exclusive creation preserves existing files, hard links and symlink targets.
        with args.output.open('x') as output:
            output.write(json.dumps(report, indent=2) + '\n')
    except OSError:
        print(json.dumps(dict(status='UNVERIFIED', reason='REPORT_CREATION_REFUSED')))
        return 2
    print(json.dumps(report, indent=2))
    return code


if __name__ == '__main__':
    raise SystemExit(main())
