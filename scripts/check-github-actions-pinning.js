import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

const WORKFLOW_DIR = join(process.cwd(), '.github', 'workflows');
const SHA_REF = /^[0-9a-f]{40}$/;
const USES_PATTERN = /^\s*uses:\s*([^\s#]+)\s*(?:#.*)?$/;

function fail(message) {
  console.error(`::error::${message}`);
  process.exitCode = 1;
}

function workflowFiles() {
  if (!statSync(WORKFLOW_DIR, { throwIfNoEntry: false })?.isDirectory()) {
    fail('GitHub Actions workflow directory is missing or unreadable.');
    return [];
  }
  return readdirSync(WORKFLOW_DIR, { withFileTypes: true })
    .filter((entry) => entry.isFile() && /\\.(ya?ml)$/i.test(entry.name))
    .map((entry) => join(WORKFLOW_DIR, entry.name));
}

let violations = 0;
for (const file of workflowFiles()) {
  let content;
  try {
    content = readFileSync(file, 'utf8');
  } catch (error) {
    fail(`Unable to read workflow ${file}: ${error.message}`);
    continue;
  }

  const lines = content.split(/\r?\n/);
  lines.forEach((line, index) => {
    const match = line.match(USES_PATTERN);
    if (!match) return;
    const reference = match[1];
    if (reference.startsWith('./') || reference.startsWith('../')) return;

    const at = reference.lastIndexOf('@');
    const ref = at === -1 ? '' : reference.slice(at + 1);
    if (!at || !SHA_REF.test(ref)) {
      violations += 1;
      console.error(`::error file=${file},line=${index + 1}::Unpinned GitHub Action: ${reference}. Require a full 40-character commit SHA.`);
    }
  });
}

if (violations) {
  console.error(`GitHub Actions pinning gate failed: ${violations} violation(s).`);
  process.exitCode = 1;
} else if (process.exitCode !== 1) {
  console.log('GitHub Actions pinning gate passed: all external actions use full commit SHAs.');
}
