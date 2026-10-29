#!/usr/bin/env python3
"""Reject partial, skipped or failed instrumentation; match exact discovered identities."""
import argparse
import json
import re
from pathlib import Path


def identities(text):
    if re.search(r'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=', text):
        raise ValueError('instrumentation failure')
    terminal = re.findall(r'^INSTRUMENTATION_CODE: (-?\d+)\s*$', text, re.MULTILINE)
    if terminal != ['-1'] or not re.search(r'INSTRUMENTATION_CODE: -1\s*\Z', text):
        raise ValueError('missing or unsuccessful terminal instrumentation code')
    started, finished = [], []
    fields = {}
    for line in text.splitlines():
        match = re.match(r'INSTRUMENTATION_STATUS: (class|test)=(.*)', line.strip())
        if match:
            fields[match[1]] = match[2].strip()
        code = re.match(r'INSTRUMENTATION_STATUS_CODE: (-?\d+)', line.strip())
        if code:
            status = int(code[1])
            if status not in (0, 1):
                raise ValueError(f'non-passing status {status}')
            if 'class' not in fields or 'test' not in fields:
                raise ValueError('status without test identity')
            identity = fields['class'] + '#' + fields['test']
            (started if status == 1 else finished).append(identity)
            fields = {}
    footer = re.findall(r'^OK \((\d+) tests?\)\s*$', text, re.MULTILINE)
    if not started or len(footer) != 1 or int(footer[0]) != len(started):
        raise ValueError('missing/incorrect successful JUnit count')
    if len(set(started)) != len(started) or sorted(started) != sorted(finished):
        raise ValueError('duplicated or unfinished tests')
    return sorted(started)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('log', type=Path)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--discover', type=Path)
    group.add_argument('--expected', type=Path)
    group.add_argument('--single')
    args = parser.parse_args()
    actual = identities(args.log.read_text())
    if args.discover:
        args.discover.write_text(json.dumps(actual, indent=2) + '\n')
        print(f'DISCOVERED {len(actual)} tests (discovery is NOT execution)')
    else:
        expected = [args.single] if args.single else json.loads(args.expected.read_text())
        if actual != sorted(expected):
            raise ValueError(f'executed identities differ: expected={expected}, actual={actual}')
        print(f'VERIFIED {len(actual)} executed tests')


if __name__ == '__main__':
    main()
