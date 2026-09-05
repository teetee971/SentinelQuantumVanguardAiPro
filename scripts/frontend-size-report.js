import { mkdir, readdir, stat, writeFile } from 'node:fs/promises';
import { gzipSync } from 'node:zlib';
import { join, relative, resolve } from 'node:path';

const DIST_DIR = resolve('frontend/dist');
const OUT_DIR = resolve('artifacts/frontend');
const OUT_FILE = join(OUT_DIR, 'size-report.json');

async function collectFiles(dir, root = dir) {
  const entries = await readdir(dir, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) files.push(...await collectFiles(full, root));
    else if (entry.isFile()) {
      const info = await stat(full);
      files.push({ path: relative(root, full).replaceAll('\\', '/'), bytes: info.size });
    }
  }
  return files;
}

async function main() {
  const files = (await collectFiles(DIST_DIR)).sort((a, b) => a.path.localeCompare(b.path));
  const enriched = [];
  let totalBytes = 0;
  let totalGzipBytes = 0;

  for (const file of files) {
    const full = join(DIST_DIR, file.path);
    const data = await import('node:fs/promises').then(({ readFile }) => readFile(full));
    const gzipBytes = gzipSync(data, { level: 9 }).length;
    totalBytes += file.bytes;
    totalGzipBytes += gzipBytes;
    enriched.push({ ...file, gzip_bytes: gzipBytes });
  }

  const report = {
    schema_version: 1,
    source: 'frontend/dist',
    file_count: enriched.length,
    total_bytes: totalBytes,
    total_gzip_bytes: totalGzipBytes,
    files: enriched,
  };

  await mkdir(OUT_DIR, { recursive: true });
  await writeFile(OUT_FILE, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
  console.log(`Frontend size report: ${enriched.length} files, ${totalBytes} bytes, ${totalGzipBytes} gzip bytes`);
  console.log(OUT_FILE);
}

main().catch((error) => {
  console.error(error?.stack || error);
  process.exitCode = 1;
});
