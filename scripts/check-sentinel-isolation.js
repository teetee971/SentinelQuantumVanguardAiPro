import { readFile, readdir, lstat } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

export const ALLOWED_TEXT = new Set([
  '.js', '.mjs', '.cjs', '.json', '.yml', '.yaml',
  '.ts', '.tsx', '.jsx', '.html', '.css', '.xml',
  '.properties', '.gradle', '.kts', '.kt', '.toml', '.lock',
]);

export const FORBIDDEN_FILENAMES = new Set([
  'google-services.json',
  'googleservice-info.plist',
]);

// Keep the external-project identity out of Sentinel's own source text while
// retaining the security control that blocks its operational identifiers.
const FORBIDDEN_EXTERNAL_PROJECT = String.fromCharCode(
  97, 107, 105, 112, 114, 105, 115, 97, 121, 101,
);
const escapeRegExp = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const externalProjectPattern = escapeRegExp(FORBIDDEN_EXTERNAL_PROJECT)
  .split('')
  .join('[-\\s]*');

export const FORBIDDEN_PATTERNS = [
  { name: 'firebase-static-import', regex: /from\s+['"][^'"]*firebase/i },
  { name: 'firebase-static-import-bare', regex: /import\s+['"]firebase(?:\/|['"])/i },
  { name: 'firebase-dynamic-import', regex: /import\s*\(\s*['"][^'"]*firebase/i },
  { name: 'firebase-require', regex: /require\s*\(\s*['"][^'"]*firebase/i },
  { name: 'firebase-config-or-env', regex: /firebaseConfig|FIREBASE_[A-Z0-9_]*/i },
  { name: 'firebase-scoped-package', regex: /@firebase\//i },
  { name: 'firebase-rn-package', regex: /@react-native-firebase\//i },
  { name: 'firebase-admin-sdk', regex: /firebase-admin/i },
  { name: 'firebase-functions-sdk', regex: /firebase-functions/i },
  { name: 'firebase-messaging-sdk', regex: /firebase-messaging/i },
  { name: 'firebase-android-gradle', regex: /com\.google\.firebase|google-services\s*(?:plugin)?/i },
  { name: 'firebase-package-dependency', regex: /["'](?:@react-native-firebase\/[^"']+|firebase(?:-[^"']+)?)\s*["']\s*:/i },
  {
    name: 'forbidden-external-project-reference',
    regex: new RegExp(`${externalProjectPattern}|com\\.${escapeRegExp(FORBIDDEN_EXTERNAL_PROJECT)}`, 'i'),
  },
];

const ALWAYS_IGNORED_DIR_NAMES = new Set(['node_modules', '.git']);
const GENERATED_DIR_NAMES = new Set(['dist', 'coverage', 'build', '.next', '.expo']);
const SCAN_GENERATED = process.env.SENTINEL_SCAN_GENERATED === '1';
const IGNORED_DIR_NAMES = new Set([
  ...ALWAYS_IGNORED_DIR_NAMES,
  ...(SCAN_GENERATED ? [] : GENERATED_DIR_NAMES),
]);
const MAX_DEPTH = 40;
const MAX_FILES = 50000;
const MAX_ENTRIES = 100000;
const MAX_FILE_BYTES = 20 * 1024 * 1024;

const SELF_FILES = new Set([
  path.normalize('scripts/check-sentinel-isolation.js'),
  path.normalize('scripts/check-sentinel-isolation.test.js'),
]);
export function scanContentForViolations(content, relativePath) {
  const violations = [];
  for (const { name, regex } of FORBIDDEN_PATTERNS) {
    if (regex.test(content)) violations.push({ file: relativePath, pattern: name, source: regex.source });
  }
  return violations;
}

export function isForbiddenFilename(fileName) {
  return FORBIDDEN_FILENAMES.has(fileName.toLowerCase());
}

async function walk(directory, root, files, state, depth = 0) {
  if (depth > MAX_DEPTH) {
    state.errors.push({ path: path.relative(root, directory), error: 'max_depth_exceeded' });
    return;
  }
  let entries;
  try {
    entries = await readdir(directory, { withFileTypes: true });
  } catch (error) {
    if (error?.code !== 'ENOENT') state.errors.push({ path: path.relative(root, directory), error: error?.code ?? 'read_directory_failed' });
    return;
  }
  for (const entry of entries) {
    if (IGNORED_DIR_NAMES.has(entry.name)) continue;
    state.entries += 1;
    if (state.entries > MAX_ENTRIES) {
      state.errors.push({ path: path.relative(root, directory), error: 'max_entries_exceeded' });
      return;
    }
    const full = path.join(directory, entry.name);
    const relative = path.relative(root, full);
    let info;
    try {
      info = await lstat(full);
    } catch (error) {
      state.errors.push({ path: relative, error: error?.code ?? 'stat_failed' });
      continue;
    }
    if (info.isSymbolicLink()) {
      state.errors.push({ path: relative, error: 'symlink_not_allowed' });
      state.discovered += 1;
      continue;
    }
    if (isForbiddenFilename(entry.name)) {
      files.push({ path: full, forbiddenFilename: true });
      state.discovered += 1;
      continue;
    }
    if (info.isDirectory()) {
      await walk(full, root, files, state, depth + 1);
      if (state.entries > MAX_ENTRIES) return;
      continue;
    }
    if (info.isFile() && ALLOWED_TEXT.has(path.extname(entry.name).toLowerCase())) {
      if (state.discovered >= MAX_FILES) {
        state.errors.push({ path: relative, error: 'max_files_exceeded' });
        return;
      }
      files.push({ path: full });
      state.discovered += 1;
    }
  }
}

export async function checkSentinelIsolation(root = ROOT) {
  const files = [];
  const state = { discovered: 0, entries: 0, errors: [] };
  await walk(root, root, files, state);
  const violations = state.errors.map((error) => ({ file: error.path, pattern: `scan-error:${error.error}` }));
  const readErrors = [];
  for (const entry of files) {
    const relativePath = path.normalize(path.relative(root, entry.path));
    if (SELF_FILES.has(relativePath)) continue;
    if (entry.forbiddenFilename) {
      violations.push({ file: relativePath, pattern: 'forbidden-filename-present' });
      continue;
    }
    let content;
    try {
      const buffer = await readFile(entry.path);
      if (buffer.byteLength > MAX_FILE_BYTES) {
        violations.push({ file: relativePath, pattern: 'scan-error:file_too_large' });
        continue;
      }
      content = buffer.toString('utf8');
    } catch (error) {
      readErrors.push({ file: relativePath, error: error?.code ?? 'unknown' });
      violations.push({ file: relativePath, pattern: 'scan-error:unreadable' });
      continue;
    }
    violations.push(...scanContentForViolations(content, relativePath));
  }
  return { passed: violations.length === 0, files_scanned: files.length, entries_examined: state.entries, violations, read_errors: readErrors, generated_outputs_scanned: SCAN_GENERATED };
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const result = await checkSentinelIsolation();
  console.log(JSON.stringify(result, null, 2));
  if (!result.passed) process.exitCode = 1;
}
