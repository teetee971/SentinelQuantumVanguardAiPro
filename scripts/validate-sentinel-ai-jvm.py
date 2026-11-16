#!/usr/bin/env python3
"""Compile actual AI sources and run all AI JUnit suites; no dependency downloads.
This is JVM evidence only. Android APIs are compile-only and not executed here.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--kotlinc', default='kotlinc')
parser.add_argument('--junit', required=True)
parser.add_argument('--hamcrest', required=True)
parser.add_argument('--android-jar', required=True)
parser.add_argument('--output', help='New evidence directory; must not already exist')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
out = Path(args.output).resolve() if args.output else Path(tempfile.mkdtemp(prefix='sentinel-ai-jvm-'))
if args.output:
    out.mkdir(parents=True, exist_ok=False)
print(f'Evidence: {out}', flush=True)
source = root / 'native-android-app/app/src'
main = sorted((source / 'main/java/com/sentinel/quantum/ai').glob('*.kt'))
tests = sorted((source / 'test/java/com/sentinel/quantum/ai').glob('*.kt'))
runner = root / 'scripts/ai-validation/SentinelAiJvmRunner.java'
junit, hamcrest, android = (str(Path(p).resolve()) for p in (args.junit, args.hamcrest, args.android_jar))
for dependency in (junit, hamcrest, android):
    if not Path(dependency).is_file():
        raise SystemExit(f'Missing dependency: {dependency}')
evidence = {'commands': [], 'source_sha256': {
    str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest()
    for p in main + tests + [runner, Path(__file__).resolve()]
}, 'dependency_sha256': {p: hashlib.sha256(Path(p).read_bytes()).hexdigest()
                        for p in (junit, hamcrest, android)}}
def run(command, name):
    record = {'argv': command, 'cwd': str(root)}
    evidence['commands'].append(record)
    with (out / name).open('w') as log:
        result = subprocess.run(command, cwd=root, stdout=log, stderr=subprocess.STDOUT)
    record['exit_code'] = result.returncode
    (out / 'manifest.json').write_text(json.dumps(evidence, indent=2) + '\n')
    print((out / name).read_text(), end='', flush=True)
    if result.returncode:
        raise SystemExit(result.returncode)
run([args.kotlinc, '-version'], 'kotlin-version.log')
run(['java', '-version'], 'java-version.log')
run([args.kotlinc] + [str(p) for p in main + tests] + [
    '-cp', os.pathsep.join((junit, android)), '-include-runtime', '-d', str(out / 'ai-tests.jar')], 'compile.log')
classpath = os.pathsep.join((str(out / 'ai-tests.jar'), junit, hamcrest))
run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-cp', classpath, '-d', str(out), str(runner)], 'runner-compile.log')
classes = ['com.sentinel.quantum.ai.' + p.stem for p in tests if p.name.endswith('Test.kt')]
run(['java', '-cp', os.pathsep.join((str(out), classpath)), 'SentinelAiJvmRunner', str(out / 'xml')] + classes, 'junit.log')
print('All selected AI JVM suites completed successfully. No Android/device qualification claimed.')
