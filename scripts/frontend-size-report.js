import { constants } from 'node:fs';
import { mkdir, mkdtemp, open, readdir, rename, rm } from 'node:fs/promises';
import { Transform } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import { createGzip } from 'node:zlib';
import { basename, dirname, join, relative, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const DEFAULT_DIST_DIR = resolve('frontend/dist');
const DEFAULT_OUT_FILE = resolve('artifacts/frontend/size-report.json');

function bytewiseCompare(left, right) {
  return Buffer.compare(Buffer.from(left), Buffer.from(right));
}

async function collectFiles(dir, root = dir) {
  const entries = await readdir(dir, { withFileTypes: true });
  entries.sort((a, b) => bytewiseCompare(a.name, b.name));

  const files = [];
  for (const entry of entries) {
    const full = join(dir, entry.name);
    if (entry.isSymbolicLink()) {
      throw new Error(`symbolic links are not allowed in frontend size reports: ${relative(root, full).replaceAll('\\', '/')}`);
    }
    if (entry.isDirectory()) {
      files.push(...await collectFiles(full, root));
      continue;
    }
    if (!entry.isFile()) {
      throw new Error(`unsupported filesystem entry in frontend build: ${relative(root, full).replaceAll('\\', '/')}`);
    }
    files.push(relative(root, full).replaceAll('\\', '/'));
  }
  return files;
}

async function measureFile(fullPath) {
  const flags = constants.O_RDONLY | (constants.O_NOFOLLOW ?? 0);
  const handle = await open(fullPath, flags);
  try {
    const before = await handle.stat();
    if (!before.isFile()) throw new Error(`frontend size report entry is not a regular file: ${fullPath}`);

    let bytes = 0;
    let gzipBytes = 0;
    const rawCounter = new Transform({
      transform(chunk, encoding, callback) {
        bytes += chunk.length;
        callback(null, chunk);
      },
    });
    const gzipCounter = new Transform({
      transform(chunk, encoding, callback) {
        gzipBytes += chunk.length;
        callback();
      },
    });

    await pipeline(
      handle.createReadStream({ autoClose: false }),
      rawCounter,
      createGzip({ level: 9, mtime: 0 }),
      gzipCounter,
    );

    const after = await handle.stat();
    if (before.size !== after.size || before.mtimeMs !== after.mtimeMs) {
      throw new Error(`frontend file changed while being measured: ${fullPath}`);
    }

    return { bytes, gzipBytes };
  } finally {
    await handle.close();
  }
}

async function writeFileAtomic(path, content) {
  const parent = dirname(path);
  await mkdir(parent, { recursive: true });
  const tempDir = await mkdtemp(join(parent, `.${basename(path)}.tmp-`));
  const tempPath = join(tempDir, basename(path));
  let handle;
  try {
    handle = await open(tempPath, 'wx', 0o600);
    await handle.writeFile(content, 'utf8');
    await handle.sync();
    await handle.close();
    handle = undefined;
    await rename(tempPath, path);
  } finally {
    if (handle) await handle.close().catch(() => {});
    await rm(tempDir, { recursive: true, force: true }).catch(() => {});
  }
}

export async function generateFrontendSizeReport({
  distDir = DEFAULT_DIST_DIR,
  outFile = DEFAULT_OUT_FILE,
} = {}) {
  const resolvedDist = resolve(distDir);
  const resolvedOut = resolve(outFile);
  const files = await collectFiles(resolvedDist);

  const enriched = [];
  let totalBytes = 0;
  let totalGzipBytes = 0;

  for (const path of files) {
    const { bytes, gzipBytes } = await measureFile(join(resolvedDist, path));
    totalBytes += bytes;
    totalGzipBytes += gzipBytes;
    enriched.push({ path, bytes, gzip_bytes: gzipBytes });
  }

  const report = {
    schema_version: 1,
    source: 'frontend/dist',
    file_count: enriched.length,
    total_bytes: totalBytes,
    total_gzip_bytes: totalGzipBytes,
    files: enriched,
  };

  const serialized = `${JSON.stringify(report, null, 2)}\n`;
  await writeFileAtomic(resolvedOut, serialized);
  return { report, serialized, outFile: resolvedOut };
}

async function main() {
  const { report, outFile } = await generateFrontendSizeReport();
  console.log(`Frontend size report: ${report.file_count} files, ${report.total_bytes} bytes, ${report.total_gzip_bytes} gzip bytes`);
  console.log(outFile);
}

const invokedAsScript = process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href;
if (invokedAsScript) {
  main().catch((error) => {
    console.error(error?.stack || error);
    process.exitCode = 1;
  });
}
