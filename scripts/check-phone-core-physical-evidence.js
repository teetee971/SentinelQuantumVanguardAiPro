#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const COLLECTOR_PATH = path.join(ROOT, 'scripts', 'phone-core-physical-evidence.sh');

const REQUIRED_MARKERS = Object.freeze([
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
  'commercial_release_claim=false',
]);

const FORBIDDEN_SOURCES = Object.freeze([
  'content://sms',
  'content://mms',
  'content://contacts',
  'content://call_log',
  'service call iphonesubinfo',
  'subscription_id',
  'subscriber_id',
  'iccid',
  'imsi',
]);

export function auditPhysicalEvidenceCollector(source) {
  const errors = [];
  const text = String(source ?? '');

  for (const marker of REQUIRED_MARKERS) {
    if (!text.includes(marker)) {
      errors.push(`physical evidence collector missing required marker: ${marker}`);
    }
  }

  const lower = text.toLowerCase();
  for (const token of FORBIDDEN_SOURCES) {
    if (lower.includes(token.toLowerCase())) {
      errors.push(`physical evidence collector forbidden PII source: ${token}`);
    }
  }

  if (/physically_validated\s*[=:]\s*true/i.test(text)) {
    errors.push('physical evidence collector must never self-promote physically_validated=true');
  }
  if (/commercial_release_claim\s*[=:]\s*true/i.test(text)) {
    errors.push('physical evidence collector must never claim commercial release readiness');
  }

  return errors;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  let collector = '';
  try {
    collector = fs.readFileSync(COLLECTOR_PATH, 'utf8');
  } catch (error) {
    console.error(`Phone Core physical evidence gate: FAILED - ${error.message}`);
    process.exitCode = 1;
  }

  if (collector) {
    const errors = auditPhysicalEvidenceCollector(collector);
    if (errors.length > 0) {
      console.error('Phone Core physical evidence gate: FAILED');
      for (const error of errors) console.error(`- ${error}`);
      process.exitCode = 1;
    } else {
      console.log('Phone Core physical evidence gate: OK');
    }
  }
}
