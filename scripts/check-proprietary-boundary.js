import { readdirSync, readFileSync, statSync } from 'node:fs';
import { extname, join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const CLIENT_ROOTS = [
  'public',
  'frontend',
  'native-android-app/app/src/main'
];

const TEXT_EXTENSIONS = new Set([
  '.js', '.mjs', '.cjs', '.ts', '.tsx', '.kt', '.java', '.xml', '.html', '.json', '.yml', '.yaml', '.properties'
]);

const SERVER_ONLY_IDENTIFIERS = [
  'REPORT_API_KEY',
  'MODERATION_API_KEY',
  'EXPOSURE_API_KEY',
  'PHONE_HASH_PEPPER',
  'RATE_LIMIT_PEPPER',
  'INDICATOR_HASH_PEPPER',
  'PUBLIC_REPORT_PEPPER',
  'EXPOSURE_HASH_PEPPER'
];

const SERVER_ONLY_HEADERS = [
  'X-Moderation-Key',
  'X-Report-Key',
  'X-Exposure-Key'
];

const SERVER_ONLY_ROUTE_FRAGMENTS = [
  '/v1/moderation/pending',
  '/v1/moderation/decision',
  '/v1/intelligence/moderation/',
  '/v1/intelligence/relationships/report',
  '/v1/intelligence/graph/lookup',
  '/v1/intelligence/exposures/report',
  '/v1/intelligence/exposures/lookup'
];

export function stripCommentsPreserveStrings(input) {
  let output = '';
  let state = 'code';
  let quote = null;

  for (let i = 0; i < input.length; i += 1) {
    const ch = input[i];
    const next = input[i + 1];

    if (state === 'line-comment') {
      if (ch === '\n') {
        output += '\n';
        state = 'code';
      } else {
        output += ' ';
      }
      continue;
    }

    if (state === 'block-comment') {
      if (ch === '*' && next === '/') {
        output += '  ';
        i += 1;
        state = 'code';
      } else {
        output += ch === '\n' ? '\n' : ' ';
      }
      continue;
    }

    if (state === 'html-comment') {
      if (input.startsWith('-->', i)) {
        output += '   ';
        i += 2;
        state = 'code';
      } else {
        output += ch === '\n' ? '\n' : ' ';
      }
      continue;
    }

    if (state === 'string') {
      if (quote === '"""') {
        if (input.startsWith('"""', i)) {
          output += '"""';
          i += 2;
          state = 'code';
          quote = null;
        } else {
          output += ch;
        }
        continue;
      }

      output += ch;
      if (ch === '\\' && i + 1 < input.length) {
        output += input[i + 1];
        i += 1;
        continue;
      }
      if (ch === quote) {
        state = 'code';
        quote = null;
      }
      continue;
    }

    if (input.startsWith('<!--', i)) {
      output += '    ';
      i += 3;
      state = 'html-comment';
      continue;
    }
    if (ch === '/' && next === '/') {
      output += '  ';
      i += 1;
      state = 'line-comment';
      continue;
    }
    if (ch === '/' && next === '*') {
      output += '  ';
      i += 1;
      state = 'block-comment';
      continue;
    }
    if (input.startsWith('"""', i)) {
      output += '"""';
      i += 2;
      quote = '"""';
      state = 'string';
      continue;
    }
    if (ch === '"' || ch === "'" || ch === '`') {
      output += ch;
      quote = ch;
      state = 'string';
      continue;
    }

    output += ch;
  }

  return output;
}

export function validateDistributedText(text, file = '<memory>') {
  const errors = [];
  const activeText = stripCommentsPreserveStrings(text);

  for (const identifier of SERVER_ONLY_IDENTIFIERS) {
    if (activeText.includes(identifier)) errors.push(`${file}: server-only identifier exposed: ${identifier}`);
  }
  for (const header of SERVER_ONLY_HEADERS) {
    if (activeText.toLowerCase().includes(header.toLowerCase())) errors.push(`${file}: server-only authentication header exposed: ${header}`);
  }
  for (const route of SERVER_ONLY_ROUTE_FRAGMENTS) {
    if (activeText.includes(route)) errors.push(`${file}: internal/admin API route exposed: ${route}`);
  }
  return errors;
}

function walk(root) {
  const absolute = resolve(root);
  const entries = [];
  const visit = (path) => {
    for (const name of readdirSync(path)) {
      const child = join(path, name);
      const stat = statSync(child);
      if (stat.isDirectory()) {
        if (['build', 'dist', '.gradle', 'node_modules'].includes(name)) continue;
        visit(child);
      } else if (TEXT_EXTENSIONS.has(extname(name).toLowerCase())) {
        entries.push(child);
      }
    }
  };
  visit(absolute);
  return entries;
}

export function scanDistributedSurfaces(roots = CLIENT_ROOTS) {
  const errors = [];
  for (const root of roots) {
    let files;
    try {
      files = walk(root);
    } catch (error) {
      if (error?.code === 'ENOENT') continue;
      throw error;
    }
    for (const file of files) {
      errors.push(...validateDistributedText(readFileSync(file, 'utf8'), file));
    }
  }
  return errors;
}

export function runProprietaryBoundaryCheck() {
  const errors = scanDistributedSurfaces();
  if (errors.length > 0) {
    throw new Error(`Proprietary boundary validation failed:\n- ${errors.join('\n- ')}`);
  }
  return { ok: true };
}

const invokedAsScript = process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href;
if (invokedAsScript) {
  try {
    runProprietaryBoundaryCheck();
    console.log('Proprietary boundary OK: no active server-only credentials or internal routes in distributed surfaces.');
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
