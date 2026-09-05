#!/usr/bin/env node
import { createHash } from 'node:crypto';
import { existsSync, lstatSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { resolve, relative, isAbsolute } from 'node:path';
import { pathToFileURL } from 'node:url';

function fail(message) {
  throw new Error(message);
}

function pathInsideRoot(root, value) {
  if (typeof value !== 'string' || !value || isAbsolute(value)) fail('RELATIVE_PATH_REQUIRED');
  const fullPath = resolve(root, value);
  if (relative(root, fullPath).startsWith('..')) fail('PATH_OUTSIDE_REPOSITORY');
  return fullPath;
}

function parseArguments(args) {
  const values = new Map();
  for (let index = 0; index < args.length; index += 2) {
    const key = args[index];
    const value = args[index + 1];
    if (!['--sbom', '--output', '--artifact'].includes(key) || value === undefined) fail('INVALID_ARGUMENTS');
    if (key === '--artifact') {
      const artifacts = values.get(key) ?? [];
      artifacts.push(value);
      values.set(key, artifacts);
    } else if (values.has(key)) {
      fail('DUPLICATE_ARGUMENT');
    } else {
      values.set(key, value);
    }
  }
  if (!values.has('--sbom') || !values.has('--output') || !(values.get('--artifact')?.length)) fail('EVIDENCE_INPUT_REQUIRED');
  return values;
}

function sha256(file) {
  return createHash('sha256').update(readFileSync(file)).digest('hex');
}

export function generateReleaseEvidence({ sbom, output, artifacts }, env = process.env, root = resolve(process.cwd())) {
  const required = ['GITHUB_REPOSITORY', 'GITHUB_SHA', 'GITHUB_WORKFLOW', 'GITHUB_WORKFLOW_REF', 'GITHUB_RUN_ID', 'GITHUB_RUN_ATTEMPT', 'GITHUB_REF'];
  for (const name of required) {
    if (typeof env[name] !== 'string' || !env[name]) fail(`MISSING_CI_PROVENANCE:${name}`);
  }
  if (!/^[a-f0-9]{40}$/i.test(env.GITHUB_SHA)) fail('INVALID_CI_COMMIT');

  const sbomPath = pathInsideRoot(root, sbom);
  const outputPath = pathInsideRoot(root, output);
  const artifactPaths = artifacts.map((artifact) => pathInsideRoot(root, artifact));
  for (const file of [sbomPath, ...artifactPaths]) {
    if (!existsSync(file) || !lstatSync(file).isFile()) fail(`EVIDENCE_FILE_REQUIRED:${relative(root, file)}`);
  }

  const evidence = {
    schema_version: 1,
    generated_at: new Date().toISOString(),
    provenance: {
      repository: env.GITHUB_REPOSITORY,
      commit: env.GITHUB_SHA,
      workflow: env.GITHUB_WORKFLOW,
      workflow_ref: env.GITHUB_WORKFLOW_REF,
      run_id: env.GITHUB_RUN_ID,
      run_attempt: env.GITHUB_RUN_ATTEMPT,
      ref: env.GITHUB_REF,
    },
    sbom: { path: sbom, sha256: sha256(sbomPath) },
    artifacts: artifactPaths.map((file, index) => ({
      path: artifacts[index],
      sha256: sha256(file),
      bytes: lstatSync(file).size,
    })),
  };
  const temporary = `${outputPath}.tmp`;
  writeFileSync(temporary, `${JSON.stringify(evidence, null, 2)}\n`, { encoding: 'utf8', mode: 0o600 });
  renameSync(temporary, outputPath);
  return evidence;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {
    const argumentsMap = parseArguments(process.argv.slice(2));
    const result = generateReleaseEvidence({
      sbom: argumentsMap.get('--sbom'),
      output: argumentsMap.get('--output'),
      artifacts: argumentsMap.get('--artifact'),
    });
    console.log(JSON.stringify(result, null, 2));
  } catch (error) {
    console.error(`Release evidence: ${error.message}`);
    process.exitCode = 1;
  }
}
