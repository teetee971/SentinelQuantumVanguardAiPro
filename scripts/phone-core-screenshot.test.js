import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';

const require = createRequire(import.meta.url);
const { isValidPngFile } = require('./phone-core-screenshot.cjs');
const ONE_PIXEL_PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
  'base64'
);

test('accepts a complete PNG capture with dimensions and CRCs', () => {
  const directory = mkdtempSync(join(tmpdir(), 'sentinel-phone-core-png-'));
  try {
    const file = join(directory, 'capture.png');
    writeFileSync(file, ONE_PIXEL_PNG);
    assert.equal(isValidPngFile(file), true);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});

test('rejects random, truncated, and CRC-tampered capture files', () => {
  const directory = mkdtempSync(join(tmpdir(), 'sentinel-phone-core-png-invalid-'));
  try {
    const randomFile = join(directory, 'random.png');
    const truncatedFile = join(directory, 'truncated.png');
    const tamperedFile = join(directory, 'tampered.png');
    writeFileSync(randomFile, Buffer.alloc(300));
    writeFileSync(truncatedFile, ONE_PIXEL_PNG.subarray(0, -1));
    const tampered = Buffer.from(ONE_PIXEL_PNG);
    tampered[tampered.length - 5] ^= 0xff;
    writeFileSync(tamperedFile, tampered);

    assert.equal(isValidPngFile(randomFile), false);
    assert.equal(isValidPngFile(truncatedFile), false);
    assert.equal(isValidPngFile(tamperedFile), false);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});
