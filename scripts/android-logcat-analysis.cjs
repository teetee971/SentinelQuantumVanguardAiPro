'use strict';

// Analyze the complete buffer. Ownership is derived from process/stack evidence,
// never from an allowlist that discards every AndroidRuntime or Telecom error.
function analyzeLogcat(log, packageName = 'com.sentinel.quantum') {
  const escaped = packageName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const ownedReference = new RegExp(`${escaped}(?![\\w.])`);
  const ownedFrame = new RegExp(`\\bat ${escaped}\\.`);
  const lines = log.split(/\r?\n/);
  const ownedPids = new Set();
  const expectedKills = new Set();
  const findings = [];
  const pidOf = (line) => line.match(/\b[VDIWEF]\/[^()]+\(\s*(\d+)\)/)?.[1] ||
    line.match(/^\d\d-\d\d\s+\S+\s+(\d+)\s+\d+\s+[VDIWEF]\s/)?.[1];
  const recordOf = (line) => {
    const brief = line.match(/\b[VDIWEF]\/([^()]+)\(\s*\d+\):\s?(.*)$/);
    const thread = line.match(/^\d\d-\d\d\s+\S+\s+\d+\s+\d+\s+[VDIWEF]\s+([^:]+):\s?(.*)$/);
    const match = brief || thread;
    return match ? { tag: match[1].trim(), message: match[2] } : null;
  };
  for (const line of lines) {
    if (!ownedReference.test(line)) continue;
    const start = line.match(/Start proc (\d+):/);
    const process = line.match(/Process: [^,]+, PID: (\d+)/);
    if (start || process) ownedPids.add((start || process)[1]);
  }
  for (let index = 0; index < lines.length; index++) {
    const line = lines[index];
    if (ownedReference.test(line) && /Killing \d+:/.test(line) &&
        /: stop |permission (?:change|revok)|package (?:update|replace)|user request/i.test(line)) {
      expectedKills.add(line.match(/Killing (\d+):/)[1]);
    }
    const kind = /FATAL EXCEPTION|Fatal signal/.test(line) ? 'CRASH' :
      /\bANR in\b|am_anr|Input dispatching timed out/.test(line) ? 'ANR' :
      /SecurityException|IllegalStateException|NullPointerException|NavigationException/.test(line) ? 'EXCEPTION' :
      /Process died|Process .*has died/.test(line) ? 'PROCESS_DEATH' :
      /\bE\/Sentinel|\bE\s+Sentinel/.test(line) ? 'PRODUCT_ERROR' : null;
    if (!kind) continue;
    const pid = pidOf(line);
    // Follow the exception record, not arbitrary nearby messages. system_server
    // logs many unrelated components under the same PID: a later "Displayed
    // com.sentinel.quantum" line is not a frame of DeviceLock's exception.
    const record = recordOf(line);
    const stack = [line];
    for (let next = index + 1; record && next < Math.min(lines.length, index + 128); next++) {
      if (pid && pidOf(lines[next]) !== pid) continue; // interleaved other process
      const entry = recordOf(lines[next]);
      if (!entry || entry.tag !== record.tag || !/^\s*(?:at |Caused by: |Suppressed: |\.\.\. \d+ more|Process: |(?:[\w$]+\.)+[\w$]*(?:Exception|Error)(?::|$))/.test(entry.message)) break;
      stack.push(lines[next]);
    }
    const context = stack.join('\n');
    const prior = lines.slice(Math.max(0, index - 96), index).join('\n');
    const following = lines.slice(index + 1, Math.min(lines.length, index + 96)).join('\n');
    // Android's instrumentation UiAutomation accessibility client can receive a
    // final Binder accessibility event after AndroidJUnitRunner has torn down its
    // Handler. API 37 logs that platform-only race as IllegalStateException in the
    // target PID after ActivityManager explicitly reports "finished inst". It is
    // harness teardown, not product execution. Keep the exception visible, but
    // exempt only this exact, bounded lifecycle signature; any product frame or
    // the same dead-Handler error during normal execution still fails the gate.
    const expectedTestTeardown = kind === 'EXCEPTION' && Boolean(pid) &&
      /Handler \(android\.os\.Handler\).*sending message to a Handler on a dead thread/.test(context) &&
      /android\.accessibilityservice\.AccessibilityService\$IAccessibilityServiceClientWrapper\.onAccessibilityEvent/.test(context) &&
      /android\.os\.HandlerExecutor\.execute/.test(context) &&
      !ownedReference.test(context) && !ownedFrame.test(context) &&
      new RegExp(`Force stopping ${escaped} .*finished inst`).test(prior) &&
      new RegExp(`Killing ${pid}:${escaped}\\b.*finished inst`).test(`${prior}\n${following}`);
    const product = ownedReference.test(line) || (pid && ownedPids.has(pid)) ||
      ((kind === 'CRASH' || kind === 'EXCEPTION') && (ownedReference.test(context) || ownedFrame.test(context))) || /SentinelLifecycle/.test(line);
    const deathPid = line.match(/\(pid (\d+)\)/)?.[1];
    const expectedDeath = kind === 'PROCESS_DEATH' && expectedKills.has(deathPid);
    const classification = expectedDeath ? 'EXPECTED_HARNESS_STOP' :
      expectedTestTeardown ? 'EXPECTED_TEST_TEARDOWN' : product ? 'PRODUCT' :
      kind === 'CRASH' && !/Process:|Fatal signal.*\([^)]*\)/.test(context) ? 'UNKNOWN' : 'SYSTEM';
    findings.push({ line: index + 1, kind, classification, pid: pid || null, message: line });
  }
  const failures = findings.filter((item) => item.classification === 'PRODUCT' || item.classification === 'UNKNOWN');
  return { schema_version: 1, result: log.trim() && failures.length === 0 ? 'PASS' : 'FAIL',
    package: packageName, line_count: lines.length, failures, findings };
}
module.exports = { analyzeLogcat };

function isScreenshot(data) {
  // Screenshots are visual evidence only. Reject empty/placeholder/truncated files.
  return Buffer.isBuffer(data) && data.length > 256 &&
    data.subarray(0, 8).equals(Buffer.from('89504e470d0a1a0a', 'hex')) &&
    data.toString('ascii', 12, 16) === 'IHDR' &&
    data.readUInt32BE(16) >= 240 && data.readUInt32BE(20) >= 240 &&
    data.includes(Buffer.from('IDAT')) &&
    data.subarray(-12).equals(Buffer.from('0000000049454e44ae426082', 'hex'));
}
module.exports.isScreenshot = isScreenshot;

if (require.main === module) {
  const fs = require('node:fs');
  const [input, output] = process.argv.slice(2);
  if (!input || !output) throw new Error('Usage: android-logcat-analysis.cjs LOGCAT JSON');
  const report = analyzeLogcat(fs.readFileSync(input, 'utf8'));
  fs.writeFileSync(output, JSON.stringify(report, null, 2) + '\n');
  if (report.result !== 'PASS') {
    console.error(`Android logcat gate failed: ${JSON.stringify(report.failures)}`);
    process.exitCode = 1;
  }
}
