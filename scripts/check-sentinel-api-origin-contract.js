import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const SECURITY_ROOT = 'native-android-app/app/src/main/java/com/sentinel/quantum/security';
const ORIGIN_FILE = 'SentinelApiOrigin.kt';
const LEGACY_BUILD_FIELD = 'BuildConfig.WANGIRI_API_BASE_URL';
const PROVIDER_HOST_MARKERS = ['.onrender.com'];

function kotlinFiles(root) {
  const files = [];
  const visit = (dir) => {
    for (const name of readdirSync(dir)) {
      const path = join(dir, name);
      const stat = statSync(path);
      if (stat.isDirectory()) visit(path);
      else if (name.endsWith('.kt')) files.push(path);
    }
  };
  visit(resolve(root));
  return files;
}

export function validateApiOriginSources(entries) {
  const errors = [];

  for (const { name, content } of entries) {
    const normalizedName = name.replaceAll('\\', '/');
    const isOriginFile = normalizedName.endsWith(`/${ORIGIN_FILE}`) || normalizedName === ORIGIN_FILE;

    if (!isOriginFile && content.includes(LEGACY_BUILD_FIELD)) {
      errors.push(`${name}: direct legacy API BuildConfig access outside ${ORIGIN_FILE}`);
    }

    for (const marker of PROVIDER_HOST_MARKERS) {
      if (content.toLowerCase().includes(marker)) {
        errors.push(`${name}: provider-specific API hostname marker exposed: ${marker}`);
      }
    }
  }

  const origin = entries.find(({ name }) =>
    name.replaceAll('\\', '/').endsWith(`/${ORIGIN_FILE}`) || name === ORIGIN_FILE
  );
  if (!origin) {
    errors.push(`${ORIGIN_FILE}: canonical API origin source is missing`);
  } else if (!origin.content.includes(LEGACY_BUILD_FIELD)) {
    errors.push(`${ORIGIN_FILE}: legacy BuildConfig compatibility access must remain centralized until gateway migration`);
  }

  return errors;
}

export function runApiOriginContractCheck(root = SECURITY_ROOT) {
  const entries = kotlinFiles(root).map((file) => ({
    name: file,
    content: readFileSync(file, 'utf8')
  }));
  const errors = validateApiOriginSources(entries);

  if (errors.length > 0) {
    throw new Error(`Sentinel API origin contract failed:\n- ${errors.join('\n- ')}`);
  }

  return { files: entries.length };
}

const invokedAsScript = process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href;
if (invokedAsScript) {
  try {
    const result = runApiOriginContractCheck();
    console.log(`Sentinel API origin contract OK across ${result.files} Kotlin security files.`);
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
