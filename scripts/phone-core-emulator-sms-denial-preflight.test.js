import assert from 'node:assert/strict';
import { chmodSync, existsSync, mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';

const root = resolve(new URL('..', import.meta.url).pathname);
const preflight = join(root, 'scripts', 'phone-core-emulator-sms-denial-preflight.sh');

function runScenario(scenario) {
  const dir = mkdtempSync(join(tmpdir(), 'sentinel-sms-denial-preflight-'));
  const bin = join(dir, 'bin');
  const output = join(dir, 'evidence');
  mkdirSync(bin);
  mkdirSync(output);
  const adb = join(bin, 'adb');
  writeFileSync(adb, `#!/usr/bin/env bash
set -u
ROOT="${dir}"
SCENARIO="${scenario}"
STATE="$ROOT/permission-state"
APPOP="$ROOT/appop-state"
LAUNCHES="$ROOT/launches"
PKGIGNORE="$ROOT/package-ignore"
printf '%s\\n' "$*" >> "$ROOT/commands.log"
state() { if [[ -s "$STATE" ]]; then cat "$STATE"; else printf 'denied\\n'; fi; }
case "$*" in
  shell\\ dumpsys\\ role)
    printf '%s\\n' 'RoleUserState { user_id=0 roleNameToPackageNames={android.app.role.SMS=[com.sentinel.quantum]} }';;
  shell\\ dumpsys\\ package*)
    if [[ "$SCENARIO" == unreadable ]]; then exit 1; fi
    printf 'android.permission.SEND_SMS: granted=%s\\n' "$( [[ "$(state)" == granted ]] && echo true || echo false )";;
  shell\\ pm\\ grant*SEND_SMS)
    if [[ "$SCENARIO" == already-denied ]]; then printf 'denied\\n' > "$STATE"; exit 1; fi
    printf 'granted\\n' > "$STATE";;
  shell\\ pm\\ revoke*SEND_SMS)
    case "$SCENARIO" in
      fallback|uid-allow) printf 'granted\\n' > "$STATE"; exit 0;;
      revoke-failed) printf 'denied\\n' > "$STATE"; exit 1;;
      *) printf 'denied\\n' > "$STATE"; exit 0;;
    esac;;
  shell\\ appops\\ set*--uid*SEND_SMS*ignore)
    if [[ "$SCENARIO" == uid-allow ]]; then printf 'allow\\n' > "$APPOP"; else printf 'denied\\n' > "$APPOP"; fi;;
  shell\\ appops\\ set*--user\\ 0\\ com.sentinel.quantum\\ SEND_SMS\\ ignore)
    touch "$PKGIGNORE";;
  shell\\ appops\\ set*SEND_SMS*allow)
    printf 'allow\\n' > "$APPOP"; rm -f "$PKGIGNORE";;
  shell\\ appops\\ get*)
    if [[ "$SCENARIO" == uid-allow ]]; then
      printf 'Uid mode: SEND_SMS: allow\\n'; [[ -e "$PKGIGNORE" ]] && printf 'SEND_SMS: ignore\\n'
    elif [[ -s "$APPOP" && "$(cat "$APPOP")" == denied ]]; then
      printf 'Uid mode: SEND_SMS: ignore\\n'
    else
      printf 'SEND_SMS: allow\\n'
    fi;;
  shell\\ am\\ start*)
    n=0; [[ -s "$LAUNCHES" ]] && n="$(cat "$LAUNCHES")"; n=$((n+1)); printf '%s\\n' "$n" > "$LAUNCHES"
    if [[ "$SCENARIO" == relaunch-restores && "$n" -ge 2 ]]; then printf 'granted\\n' > "$STATE"; fi
    printf 'Status: ok\\n';;
  shell\\ uiautomator\\ dump*) exit 0;;
  shell\\ cat*sentinel-sms-preflight.xml)
    printf '%s\\n' '<hierarchy><node package="com.sentinel.quantum" resource-id="phone_core_sms_send" clickable="true" enabled="false" bounds="[0,0][20,20]"/></hierarchy>';;
  exec-out\\ screencap*) head -c 300 /dev/zero;;
  *) exit 0;;
esac
`, { mode: 0o755 });
  chmodSync(adb, 0o755);
  const result = spawnSync('bash', [preflight, output], {
    cwd: root,
    encoding: 'utf8',
    env: { ...process.env, PATH: `${bin}:${process.env.PATH}` }
  });
  const proofPath = join(output, 'sms-denial-transition-proof.json');
  const proof = existsSync(proofPath) ? JSON.parse(readFileSync(proofPath, 'utf8')) : null;
  const commands = existsSync(join(dir, 'commands.log')) ? readFileSync(join(dir, 'commands.log'), 'utf8') : '';
  return { dir, result, proof, commands };
}

for (const [scenario, probe] of [
  ['runtime', 'SEND_SMS_RUNTIME_PERMISSION_REVOKED'],
  ['fallback', 'SEND_SMS_APP_OP_DENIED']
]) {
  test(`preflight proves a real ${scenario} SEND_SMS transition`, () => {
    const run = runScenario(scenario);
    try {
      assert.equal(run.result.status, 0, run.result.stderr || run.result.stdout);
      assert.equal(run.proof?.baseline_granted, true);
      assert.equal(run.proof?.denial_observed, true);
      assert.equal(run.proof?.post_foreground_denial_observed, true);
      assert.equal(run.proof?.ui_fail_closed, true);
      assert.equal(run.proof?.effective_permission_probe, probe);
      assert.equal(run.proof?.physical_modem_claim, false);
      assert.equal(run.proof?.commercial_release_claim, false);
    } finally { rmSync(run.dir, { recursive: true, force: true }); }
  });
}

test('already-denied SEND_SMS cannot qualify as a revocation transition', () => {
  const run = runScenario('already-denied');
  try {
    assert.notEqual(run.result.status, 0);
    assert.equal(run.proof, null);
    assert.doesNotMatch(run.commands, /pm revoke .*SEND_SMS/);
    assert.match(run.result.stdout + run.result.stderr, /grant baseline is not proven/);
  } finally { rmSync(run.dir, { recursive: true, force: true }); }
});

test('unreadable package permission state fails closed before revocation', () => {
  const run = runScenario('unreadable');
  try {
    assert.notEqual(run.result.status, 0);
    assert.equal(run.proof, null);
    assert.doesNotMatch(run.commands, /pm revoke .*SEND_SMS/);
  } finally { rmSync(run.dir, { recursive: true, force: true }); }
});

test('failed pm revoke cannot be credited even when SEND_SMS later appears denied', () => {
  const run = runScenario('revoke-failed');
  try {
    assert.notEqual(run.result.status, 0);
    assert.equal(run.proof, null);
    assert.match(run.result.stdout + run.result.stderr, /not attributable to a successful revoke/);
  } finally { rmSync(run.dir, { recursive: true, force: true }); }
});

test('foreground return that restores SEND_SMS invalidates runtime denial evidence', () => {
  const run = runScenario('relaunch-restores');
  try {
    assert.notEqual(run.result.status, 0);
    assert.equal(run.proof, null);
    assert.match(run.result.stdout + run.result.stderr, /did not survive foreground return/);
  } finally { rmSync(run.dir, { recursive: true, force: true }); }
});

test('package ignore cannot override an explicit UID allow AppOp', () => {
  const run = runScenario('uid-allow');
  try {
    assert.notEqual(run.result.status, 0);
    assert.equal(run.proof, null);
    assert.match(run.result.stdout + run.result.stderr, /AppOp denial is not proven/);
  } finally { rmSync(run.dir, { recursive: true, force: true }); }
});
