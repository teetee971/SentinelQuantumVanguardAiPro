import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, readFile, rm, symlink, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { generateFrontendSizeReport } from './frontend-size-report.js';

async function withFixture(assertion) {
  const root = await mkdtemp(join(tmpdir(), 'sentinel-frontend-size-'));
  try {
    await assertion(root);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
}

test('produces stable bytewise ordering and coherent raw/gzip totals', async () => {
  await withFixture(async (root) => {
    const dist = join(root, 'dist');
    const out = join(root, 'artifacts', 'size-report.json');
    await mkdir(join(dist, 'nested'), { recursive: true });
    await writeFile(join(dist, 'z.txt'), 'zzzz');
    await writeFile(join(dist, 'A.txt'), 'alpha');
    await writeFile(join(dist, 'nested', 'b.bin'), Buffer.from([0, 1, 2, 3, 255]));

    const first = await generateFrontendSizeReport({ distDir: dist, outFile: out });
    const firstBytes = await readFile(out);
    const second = await generateFrontendSizeReport({ distDir: dist, outFile: out });
    const secondBytes = await readFile(out);

    assert.deepEqual(first.report.files.map((file) => file.path), ['A.txt', 'nested/b.bin', 'z.txt']);
    assert.equal(first.report.file_count, first.report.files.length);
    assert.equal(first.report.total_bytes, first.report.files.reduce((sum, file) => sum + file.bytes, 0));
    assert.equal(first.report.total_gzip_bytes, first.report.files.reduce((sum, file) => sum + file.gzip_bytes, 0));
    assert.equal(first.serialized, second.serialized);
    assert.deepEqual(firstBytes, secondBytes);
  });
});

test('fails closed when the distribution directory is absent', async () => {
  await withFixture(async (root) => {
    await assert.rejects(
      generateFrontendSizeReport({
        distDir: join(root, 'missing'),
        outFile: join(root, 'artifacts', 'size-report.json'),
      }),
      /ENOENT/,
    );
  });
});

test('rejects symbolic links instead of following them', async (t) => {
  await withFixture(async (root) => {
    const dist = join(root, 'dist');
    const out = join(root, 'artifacts', 'size-report.json');
    await mkdir(dist, { recursive: true });
    await writeFile(join(root, 'outside.txt'), 'outside');
    try {
      await symlink(join(root, 'outside.txt'), join(dist, 'linked.txt'));
    } catch (error) {
      if (error?.code === 'EPERM' || error?.code === 'EACCES') {
        t.skip('symlink creation is not permitted in this environment');
        return;
      }
      throw error;
    }

    await assert.rejects(
      generateFrontendSizeReport({ distDir: dist, outFile: out }),
      /symbolic links are not allowed/,
    );
  });
});
