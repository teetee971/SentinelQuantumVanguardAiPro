import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const read = (path) => readFileSync(resolve(path), 'utf8');

test('physical evidence collector is privacy-bounded and never self-certifies release readiness', () => {
  const collector = read('scripts/phone-core-physical-evidence.sh');

  for (const marker of [
    'adb shell getprop ro.build.fingerprint',
    'adb shell dumpsys package com.sentinel.quantum',
    'adb shell dumpsys role',
    'adb shell dumpsys telecom',
    'adb logcat -b crash -d',
    'adb shell dumpsys batterystats',
    'SOURCE_HEAD_SHA',
    'APK_SHA256',
    'OBSERVED',
    'MISSING',
    'NOT_EXECUTED',
    'physical-evidence.json',
  ]) {
    assert.match(collector, new RegExp(marker.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
  }

  assert.doesNotMatch(collector, /physically_validated\s*[=:]\s*true/i);
  assert.doesNotMatch(collector, /commercial_release_claim\s*[=:]\s*true/i);
  assert.doesNotMatch(collector, /content:\/\/sms|content:\/\/mms|content:\/\/contacts|content:\/\/call_log/i);
  assert.doesNotMatch(collector, /service call iphonesubinfo|subscription_id|subscriber_id|iccid|imsi/i);
});

test('physical evidence truth gate forbids PII collection and automatic promotion', () => {
  const gate = read('scripts/check-phone-core-physical-evidence.js');

  for (const marker of [
    'phone-core-physical-evidence.sh',
    'physically_validated',
    'commercial_release_claim',
    'SOURCE_HEAD_SHA',
    'APK_SHA256',
    'content://sms',
    'content://mms',
    'content://contacts',
    'content://call_log',
    'subscription_id',
    'subscriber_id',
  ]) {
    assert.match(gate, new RegExp(marker.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
  }
});
