#!/usr/bin/env node
import { createHash } from 'node:crypto';
import { existsSync, lstatSync, readFileSync } from 'node:fs';
import { basename, isAbsolute, relative, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const EXPECTED_REPOSITORY = 'teetee971/SentinelQuantumVanguardAiPro';
const HASH = /^[a-f0-9]{64}$/i;
const COMMIT = /^[a-f0-9]{40}$/i;

function fail(code) {
  throw new Error(code);
}

function sha256(path) {
  return createHash('sha256').update(readFileSync(path)).digest('hex');
}

function safeFile(root, declaredPath) {
  if (typeof declaredPath !== 'string' || !declaredPath || isAbsolute(declaredPath)) fail('INVALID_ARTIFACT_PATH');
  const exact = resolve(root, declaredPath);
  if (relative(root, exact).startsWith('..')) fail('ARTIFACT_OUTSIDE_ROOT');
  const candidate = existsSync(exact) ? exact : resolve(root, basename(declaredPath));
  if (relative(root, candidate).startsWith('..')) fail('ARTIFACT_OUTSIDE_ROOT');
  if (!existsSync(candidate)) fail(`ARTIFACT_MISSING:${basename(declaredPath)}`);
  const stat = lstatSync(candidate);
  if (!stat.isFile() || stat.isSymbolicLink()) fail(`ARTIFACT_NOT_REGULAR:${basename(declaredPath)}`);
  return candidate;
}

function validateRecord(record, path) {
  if (!record || typeof record !== 'object' || Array.isArray(record)) fail('INVALID_ARTIFACT_RECORD');
  if (!HASH.test(record.sha256 || '')) fail('INVALID_RECORDED_HASH');
  const stat = lstatSync(path);
  if (Number.isInteger(record.bytes) && record.bytes !== stat.size) fail(`SIZE_MISMATCH:${basename(path)}`);
  if (sha256(path) !== record.sha256.toLowerCase()) fail(`HASH_MISMATCH:${basename(path)}`);
}

function parseArguments(args) {
  let root = '.';
  let evidence = 'release-evidence.json';
  for (let index = 0; index < args.length; index += 2) {
    const key = args[index];
    const value = args[index + 1];
    if (!value || !['--root', '--evidence'].includes(key)) fail('INVALID_ARGUMENTS');
    if (key === '--root') root = value;
    else evidence = value;
  }
  return { root, evidence };
}

export function verifyAndroidReleaseEvidence({ root = '.', evidence = 'release-evidence.json' } = {}) {
  const rootPath = resolve(root);
  if (!existsSync(rootPath) || !lstatSync(rootPath).isDirectory() || lstatSync(rootPath).isSymbolicLink()) fail('INVALID_RELEASE_ROOT');
  const evidencePath = safeFile(rootPath, evidence);
  if (lstatSync(evidencePath).size > 1_000_000) fail('EVIDENCE_TOO_LARGE');

  let document;
  try {
    document = JSON.parse(readFileSync(evidencePath, 'utf8'));
  } catch {
    fail('INVALID_EVIDENCE_JSON');
  }
  if (document?.schema_version !== 1) fail('UNSUPPORTED_EVIDENCE_SCHEMA');
  const provenance = document.provenance;
  if (provenance?.repository !== EXPECTED_REPOSITORY) fail('REPOSITORY_MISMATCH');
  if (!COMMIT.test(provenance.commit || '')) fail('INVALID_COMMIT');
  if (provenance.workflow !== 'Android Release APK') fail('WORKFLOW_MISMATCH');
  if (!/^refs\/tags\/v\d+\.\d+\.\d+(?:[.-][0-9A-Za-z.-]+)?$/.test(provenance.ref || '')) fail('INVALID_RELEASE_REF');
  const tagRef = provenance.ref;
  const workflowRef = `${EXPECTED_REPOSITORY}/.github/workflows/android-release.yml@${tagRef}`;
  if (provenance.workflow_ref !== workflowRef) fail('WORKFLOW_REF_MISMATCH');
  if (!/^\d+$/.test(provenance.run_id || '') || !/^\d+$/.test(provenance.run_attempt || '')) fail('INVALID_RUN_IDENTITY');

  if (!document.sbom || !HASH.test(document.sbom.sha256 || '')) fail('INVALID_SBOM_RECORD');
  const sbomPath = safeFile(rootPath, document.sbom.path);
  if (sha256(sbomPath) !== document.sbom.sha256.toLowerCase()) fail('SBOM_HASH_MISMATCH');

  if (!Array.isArray(document.artifacts) || document.artifacts.length !== 3) fail('INVALID_ARTIFACT_SET');
  const names = document.artifacts.map((entry) => basename(entry?.path || ''));
  if (new Set(names).size !== names.length) fail('DUPLICATE_ARTIFACT_NAME');
  const apkRecord = document.artifacts.find((entry) => typeof entry?.path === 'string' && entry.path.endsWith('.apk'));
  const checksumRecord = document.artifacts.find((entry) => typeof entry?.path === 'string' && entry.path.endsWith('.apk.sha256'));
  const certificateRecord = document.artifacts.find((entry) => typeof entry?.path === 'string' && entry.path.endsWith('.apk.certificates.txt'));
  if (!apkRecord || !checksumRecord || !certificateRecord) fail('REQUIRED_ARTIFACT_MISSING');

  const apkPath = safeFile(rootPath, apkRecord.path);
  const checksumPath = safeFile(rootPath, checksumRecord.path);
  const certificatePath = safeFile(rootPath, certificateRecord.path);
  validateRecord(apkRecord, apkPath);
  validateRecord(checksumRecord, checksumPath);
  validateRecord(certificateRecord, certificatePath);

  if (lstatSync(checksumPath).size > 4096) fail('CHECKSUM_REPORT_TOO_LARGE');
  const checksum = readFileSync(checksumPath, 'utf8').trim().match(/^([a-f0-9]{64})\s+\*?(.+)$/i);
  if (!checksum || checksum[1].toLowerCase() !== sha256(apkPath) || basename(checksum[2]) !== basename(apkPath)) fail('APK_CHECKSUM_MISMATCH');

  if (lstatSync(certificatePath).size > 65_536) fail('CERTIFICATE_REPORT_TOO_LARGE');
  const certificate = readFileSync(certificatePath, 'utf8');
  const certificateDigest = certificate.match(/Signer #1 certificate SHA-256 digest:\s*([a-f0-9]{64})/i)?.[1]?.toLowerCase();
  if (!certificateDigest || /BEGIN [A-Z ]*PRIVATE KEY/i.test(certificate)) fail('INVALID_CERTIFICATE_REPORT');

  return Object.freeze({
    verified: true,
    repository: provenance.repository,
    commit: provenance.commit,
    ref: provenance.ref,
    apk: basename(apkPath),
    apk_sha256: sha256(apkPath),
    certificate_sha256: certificateDigest,
  });
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {
    console.log(JSON.stringify(verifyAndroidReleaseEvidence(parseArguments(process.argv.slice(2))), null, 2));
  } catch (error) {
    console.error(`Android release verification: ${error.message}`);
    process.exitCode = 1;
  }
}
