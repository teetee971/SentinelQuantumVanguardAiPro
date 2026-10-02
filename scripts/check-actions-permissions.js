import fs from 'node:fs';
import path from 'node:path';

const ROOT = '.github/workflows';
const ALLOW = new Map([
  ['cisa-kev-refresh.yml', new Set(['contents:write','pull-requests:write'])],
  ['arcep-numbering-refresh.yml', new Set(['contents:write','pull-requests:write'])],
  ['ofcom-numbering-refresh.yml', new Set(['contents:write','pull-requests:write'])],
  ['acm-numbering-refresh.yml', new Set(['contents:write','pull-requests:write'])],
  ['ctu-numbering-refresh.yml', new Set(['contents:write','pull-requests:write'])],
  // Autonomous numbering jobs publish one validated data/report file, with no PR or other write scope.
  ['rtr-numbering-refresh.yml', new Set(['contents:write'])],
  ['international-numbering-refresh.yml', new Set(['contents:write'])],
  ['international-numbering-watch.yml', new Set(['contents:write'])],
  ['scheduled-vulnerability-watch.yml', new Set(['contents:write','pull-requests:write'])],
  ['android-release.yml', new Set(['contents:write'])],
  ['codeql-analysis.yml', new Set(['actions:read','contents:read','security-events:write'])]
]);
const ALWAYS_FORBIDDEN_WRITE = new Set([
  'actions','checks','deployments','discussions','id-token','issues','packages','pages','repository-projects','statuses'
]);

export function inspectWorkflow(source, filename) {
  const errors = [];
  const allowed = ALLOW.get(filename) ?? new Set();
  const lines = source.split(/\r?\n/);
  let inPermissions = false;
  let baseIndent = -1;

  for (let i=0;i<lines.length;i++) {
    const raw=lines[i];
    const trimmed=raw.trim();
    const indent=raw.length-raw.trimStart().length;
    if (/^permissions:\s*\{/.test(trimmed)) {
      errors.push(`${filename}:${i+1}: inline permissions maps are forbidden; use explicit block permissions`);
      inPermissions=false; continue;
    }
    if (/^permissions:\s*(write-all|read-all)\s*$/.test(trimmed)) {
      if (trimmed.endsWith('write-all')) errors.push(`${filename}:${i+1}: permissions: write-all is forbidden`);
      inPermissions=false; continue;
    }
    if (trimmed === 'permissions:') { inPermissions=true; baseIndent=indent; continue; }
    if (!inPermissions) continue;
    if (!trimmed || trimmed.startsWith('#')) continue;
    if (indent <= baseIndent) { inPermissions=false; i--; continue; }
    const m=trimmed.match(/^([a-z-]+):\s*(read|write|none)\s*(?:#.*)?$/);
    if (!m) continue;
    const [,scope,level]=m;
    if (level !== 'write') continue;
    const key=`${scope}:write`;
    if (ALWAYS_FORBIDDEN_WRITE.has(scope)) errors.push(`${filename}:${i+1}: ${key} is forbidden`);
    else if (!allowed.has(key)) errors.push(`${filename}:${i+1}: unexpected write permission ${key}`);
  }
  return errors;
}

export function inspectDirectory(root=ROOT) {
  const errors=[];
  for (const file of fs.readdirSync(root).filter(f=>/\.ya?ml$/.test(f)).sort()) {
    errors.push(...inspectWorkflow(fs.readFileSync(path.join(root,file),'utf8'),file));
  }
  return errors;
}

if (process.argv[1] && path.resolve(process.argv[1]) === path.resolve(new URL(import.meta.url).pathname)) {
  const errors=inspectDirectory();
  if (errors.length) {
    for (const e of errors) console.error(`::error::${e}`);
    process.exit(1);
  }
  console.log('GitHub Actions least-privilege gate passed.');
}
