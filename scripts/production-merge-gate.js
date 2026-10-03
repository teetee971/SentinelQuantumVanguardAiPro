import fs from 'node:fs';
import { pathToFileURL } from 'node:url';

export const UNIVERSAL_WORKFLOWS = Object.freeze([
  'security-governance-validation.yml',
  'ai-governance-validation.yml',
  'sentinel-isolation.yml',
  'integrity-check.yml',
  'preproduction-final-gate.yml',
  'product-capability-truth.yml'
]);

export const ANDROID_WORKFLOWS = Object.freeze([
  'build-native-android.yml',
  'build-aab-playconsole.yml',
  'android-instrumentation.yml'
]);

export const WEB_WORKFLOWS = Object.freeze([
  'frontend-validation.yml',
  'lighthouse-preproduction.yml'
]);

export const SECURITY_FUZZ_WORKFLOWS = Object.freeze(['security-fuzz.yml']);

const ANDROID_WORKFLOW_FILES = new Set([
  '.github/workflows/build-native-android.yml',
  '.github/workflows/build-aab-playconsole.yml',
  '.github/workflows/android-instrumentation.yml',
  '.github/workflows/android-release.yml',
  '.github/workflows/codeql-analysis.yml'
]);

const WEB_WORKFLOW_FILES = new Set([
  '.github/workflows/frontend-validation.yml',
  '.github/workflows/lighthouse-preproduction.yml',
  '.github/workflows/cloudflare-pages.yml'
]);

function isSharedRuntimePath(file) {
  return file.startsWith('config/') ||
    file.startsWith('scripts/') ||
    file === 'package.json' ||
    file === 'package-lock.json' ||
    file === '.node-version';
}

export function classifyChangedPath(file) {
  const normalized = String(file || '').replace(/^\.\//, '');
  const shared = isSharedRuntimePath(normalized);
  const security = normalized.startsWith('security/') || normalized.startsWith('decision-plane/');

  const android = normalized.startsWith('native-android-app/') ||
    normalized.startsWith('security/phone-intelligence/') ||
    ANDROID_WORKFLOW_FILES.has(normalized) ||
    shared;

  const web = normalized === 'index.html' ||
    normalized === 'robots.txt' ||
    normalized === 'sitemap.xml' ||
    normalized === '_headers' ||
    normalized === '_redirects' ||
    normalized.startsWith('public/') ||
    normalized.startsWith('frontend/') ||
    normalized.startsWith('assets/') ||
    normalized.startsWith('ai-governance/') ||
    normalized.startsWith('ai-orchestrator/') ||
    normalized.startsWith('osint-intelligence/') ||
    normalized.startsWith('security-digital-twin/') ||
    WEB_WORKFLOW_FILES.has(normalized) ||
    shared;

  return { android, web, securityFuzz: security };
}

export function requiredWorkflowsForPaths(files) {
  const required = new Set(UNIVERSAL_WORKFLOWS);
  let needsAndroid = false;
  let needsWeb = false;
  let needsSecurityFuzz = false;

  for (const file of files || []) {
    const scope = classifyChangedPath(file);
    needsAndroid ||= scope.android;
    needsWeb ||= scope.web;
    needsSecurityFuzz ||= scope.securityFuzz;
  }

  if (needsAndroid) ANDROID_WORKFLOWS.forEach((workflow) => required.add(workflow));
  if (needsWeb) WEB_WORKFLOWS.forEach((workflow) => required.add(workflow));
  if (needsSecurityFuzz) SECURITY_FUZZ_WORKFLOWS.forEach((workflow) => required.add(workflow));

  return [...required];
}

export function selectLatestExactHeadRun(runs, expectedSha) {
  const matching = (runs || []).filter((run) =>
    run &&
    run.head_sha === expectedSha &&
    run.event === 'pull_request'
  );
  matching.sort((a, b) =>
    Number(a.run_number || 0) - Number(b.run_number || 0) ||
    Number(a.run_attempt || 0) - Number(b.run_attempt || 0) ||
    Number(a.id || 0) - Number(b.id || 0)
  );
  return matching.at(-1) || null;
}

export function evaluateWorkflowRun(run) {
  if (!run) return { state: 'wait', reason: 'MISSING_EXACT_HEAD_RUN' };
  if (run.status !== 'completed') {
    return { state: 'wait', reason: `RUN_${String(run.status || 'UNKNOWN').toUpperCase()}` };
  }
  if (run.conclusion !== 'success') {
    return { state: 'fail', reason: `CONCLUSION_${String(run.conclusion || 'UNKNOWN').toUpperCase()}` };
  }
  return { state: 'pass', reason: 'SUCCESS' };
}

async function githubJson(url, token) {
  const response = await fetch(url, {
    headers: {
      Accept: 'application/vnd.github+json',
      Authorization: `Bearer ${token}`,
      'X-GitHub-Api-Version': '2022-11-28'
    }
  });
  if (!response.ok) {
    throw new Error(`GitHub API ${response.status} for ${url}`);
  }
  return response.json();
}

async function fetchChangedFiles(repository, pullNumber, token) {
  const files = [];
  for (let page = 1; page <= 30; page += 1) {
    const url = `https://api.github.com/repos/${repository}/pulls/${pullNumber}/files?per_page=100&page=${page}`;
    const payload = await githubJson(url, token);
    if (!Array.isArray(payload)) throw new Error('Unexpected pull files response');
    files.push(...payload.map((entry) => entry.filename).filter(Boolean));
    if (payload.length < 100) return files;
  }
  throw new Error('Pull request changed-file list exceeds supported bound');
}

async function fetchWorkflowRuns(repository, workflow, sha, token) {
  const params = new URLSearchParams({
    head_sha: sha,
    event: 'pull_request',
    per_page: '20'
  });
  const url = `https://api.github.com/repos/${repository}/actions/workflows/${encodeURIComponent(workflow)}/runs?${params}`;
  const payload = await githubJson(url, token);
  if (!Array.isArray(payload.workflow_runs)) throw new Error(`Unexpected workflow response for ${workflow}`);
  return payload.workflow_runs;
}

export async function runProductionMergeGate({
  repository,
  eventPath,
  token,
  timeoutMs = 55 * 60 * 1000,
  pollMs = 15 * 1000
}) {
  if (!repository || !eventPath || !token) throw new Error('Missing production merge gate environment');
  const event = JSON.parse(fs.readFileSync(eventPath, 'utf8'));
  const pullNumber = Number(event?.pull_request?.number);
  const expectedSha = String(event?.pull_request?.head?.sha || '');
  if (!Number.isSafeInteger(pullNumber) || pullNumber <= 0 || !/^[0-9a-f]{40}$/i.test(expectedSha)) {
    throw new Error('Invalid pull request identity in event payload');
  }

  const changedFiles = await fetchChangedFiles(repository, pullNumber, token);
  const required = requiredWorkflowsForPaths(changedFiles);
  console.log(`Production gate head: ${expectedSha}`);
  console.log(`Changed files: ${changedFiles.length}`);
  console.log(`Required workflows (${required.length}): ${required.join(', ')}`);

  const deadline = Date.now() + timeoutMs;
  while (true) {
    let pending = false;
    for (const workflow of required) {
      const runs = await fetchWorkflowRuns(repository, workflow, expectedSha, token);
      const latest = selectLatestExactHeadRun(runs, expectedSha);
      const evaluation = evaluateWorkflowRun(latest);
      const runLabel = latest ? `run ${latest.id}` : 'no exact-head run';

      if (evaluation.state === 'fail') {
        throw new Error(`${workflow}: ${runLabel} failed closed (${evaluation.reason})`);
      }
      if (evaluation.state === 'wait') {
        pending = true;
        console.log(`WAIT ${workflow}: ${runLabel} (${evaluation.reason})`);
      } else {
        console.log(`PASS ${workflow}: ${runLabel}`);
      }
    }

    if (!pending) {
      console.log(`Production Merge Gate passed for exact head ${expectedSha}.`);
      return { expectedSha, changedFiles, required };
    }
    if (Date.now() >= deadline) {
      throw new Error(`Timed out waiting for exact-head production evidence for ${expectedSha}`);
    }
    await new Promise((resolve) => setTimeout(resolve, pollMs));
  }
}

const invokedDirectly = process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href;
if (invokedDirectly) {
  runProductionMergeGate({
    repository: process.env.GITHUB_REPOSITORY,
    eventPath: process.env.GITHUB_EVENT_PATH,
    token: process.env.GH_TOKEN
  }).catch((error) => {
    console.error(`ERROR: ${error.message}`);
    process.exitCode = 1;
  });
}
