'use strict';

// SEND_SMS defaults to MODE_ALLOWED. AppOpsService uses a non-default UID
// policy before package policy; a recorded package allow cannot cancel UID
// ignore. Android 16: checkOperationUnchecked / noteOperationUnchecked.
function sendSmsAppOpState(text) {
  const modes = { uid: [], package: [] };
  for (const line of text.split(/\r?\n/)) {
    const prefix = line.match(/^\s*(Uid mode:\s*)?SEND_SMS:\s*/i);
    if (!prefix) continue;
    const match = line.slice(prefix[0].length).match(/^(allow|ignore|deny|errored|default|foreground)(?:[;\s]|$)/i);
    if (!match) return { valid: false, effectiveMode: 'UNKNOWN', reason: 'Malformed SEND_SMS mode' };
    modes[prefix[1] ? 'uid' : 'package'].push(match[1].toLowerCase());
  }
  if (modes.uid.length > 1 || modes.package.length > 1 || !modes.uid.length && !modes.package.length) {
    return { valid: false, effectiveMode: 'UNKNOWN', reason: 'Missing or ambiguous current SEND_SMS modes' };
  }
  const uidMode = modes.uid[0] || null;
  const packageMode = modes.package[0] || null;
  const effectiveMode = uidMode && uidMode !== 'allow' ? uidMode : packageMode || uidMode;
  return { valid: true, uidMode, packageMode, effectiveMode, reason: null };
}
module.exports = { sendSmsAppOpState };

if (require.main === module) {
  const [file, expected] = process.argv.slice(2);
  if (!file || !['ignore', 'allow', 'denied'].includes(expected)) throw new Error('Usage: android-appops-state.cjs FILE ignore|allow|denied');
  const state = sendSmsAppOpState(require('node:fs').readFileSync(file, 'utf8'));
  const matched = state.valid && (expected === 'denied' ? ['ignore', 'deny', 'errored'].includes(state.effectiveMode) : state.effectiveMode === expected);
  console.log(JSON.stringify({ ...state, expected, matched }));
  if (!matched) process.exitCode = 1;
}
