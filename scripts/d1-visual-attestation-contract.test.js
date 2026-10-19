#!/usr/bin/env node
// Host-only regression gate: parse the actual inline attestation program, not a copy.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const file = '.github/workflows/sentinel-d1-visual-capture.yml';
const workflow = fs.readFileSync(file, 'utf8');
const start = 'SOURCE_COMMIT="$SOURCE_COMMIT" OUTPUT="$output" node <<\'NODE\'';
const end = '\n          NODE';
const index = workflow.indexOf(start);
assert(index !== -1, 'D1 attestation heredoc missing');
const after = index + start.length;
const stop = workflow.indexOf(end, after);
assert(stop > after, 'D1 attestation heredoc is not closed');
const program = workflow.slice(after, stop).split('\n').map(line =>
  line.startsWith('          ') ? line.slice(10) : line
).join('\n');
assert.doesNotThrow(() => new vm.Script(program, { filename: 'd1-attestation-inline.js' }));
for (const expected of [
  'validatePng(bytes, file)',
  'PNG CRC mismatch',
  'PNG scanline size mismatch',
  'Duplicate screenshot bytes:',
  'const allowedIdenticalPair',
  'apk_sha256: apkHash',
  'apk_bytes: apkBytes',
  'manifest.built_commit !== manifest.source_commit',
  "physical_device_validation: false",
  "galaxy_s24_plus_validation: false",
]) assert(program.includes(expected), 'Required attestation contract missing: ' + expected);
assert(program.includes("file.replace(/\\.png$/, '')"), 'PNG reference extraction must use a literal dot');
assert(program.includes("apkLine.split(/\\s+/)[0]"), 'SHA-256 token parsing must split whitespace');
console.log('D1 inline attestation syntax and source contracts: PASS (not emulator qualification)');
