import { readFile, readdir, stat } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SCAN_DIRS = ['ai-governance', 'decision-plane', 'security', 'scripts', 'src', 'public', 'android'];
const ALLOWED_TEXT = new Set(['.js', '.mjs', '.cjs', '.json', '.yml', '.yaml', '.ts', '.tsx', '.jsx', '.html', '.css', '.md', '.xml', '.properties', '.gradle', '.kts']);
const FORBIDDEN = [
  /from\s+['"][^'"]*firebase/i,
  /import\s+['"]firebase(?:\/|['"])/i,
  /require\(\s*['"]firebase/i,
  /firebaseConfig|google-services\.json|FIREBASE_/i,
  /@firebase\//i,
  /akiprisaye|a\s*ki\s*pri\s*sa\s*y[eé]/i,
];

async function walk(directory, files = []) {
  let entries;
  try { entries = await readdir(directory, { withFileTypes: true }); } catch { return files; }
  for (const entry of entries) {
    if (['node_modules', '.git', 'dist', 'coverage'].includes(entry.name)) continue;
    const full = path.join(directory, entry.name);
    if (entry.isDirectory()) await walk(full, files);
    else if (ALLOWED_TEXT.has(path.extname(entry.name).toLowerCase())) files.push(full);
  }
  return files;
}

export async function checkSentinelIsolation(root = ROOT) {
  const files = [];
  for (const directory of SCAN_DIRS) await walk(path.join(root, directory), files);
  files.push(path.join(root, 'package.json'));

  const violations = [];
  for (const file of files) {
    const content = await readFile(file, 'utf8');
    for (const pattern of FORBIDDEN) {
      if (pattern.test(content)) violations.push({ file: path.relative(root, file), pattern: pattern.source });
    }
  }
  return { passed: violations.length === 0, files_scanned: files.length, violations };
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const result = await checkSentinelIsolation();
  console.log(JSON.stringify(result));
  if (!result.passed) process.exitCode = 1;
}
