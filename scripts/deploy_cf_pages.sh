#!/usr/bin/env bash
set -euo pipefail

PROJECT="sentinelquantumvanguardaipro"
BRANCH="main"
DIST="frontend/dist"
BASE="https://${PROJECT}.pages.dev"
B="?b=$(date +%s)"

section() { echo; echo "== $* =="; }

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || { echo "Missing required command: $1"; exit 1; }
}

# 1) Pre-checks & install
need_cmd node
need_cmd npm
need_cmd curl
need_cmd awk

NODE_MAJOR="$(node -v | sed -E 's/^v([0-9]+).*/\1/')"
if [ "${NODE_MAJOR}" -lt 20 ]; then
  echo "Node ${NODE_MAJOR} detected — require Node >= 20"; exit 1
fi

if ! command -v wrangler >/dev/null 2>&1; then
  echo "wrangler not found — installing wrangler@3 globally"
  npm i -g wrangler@3
fi

# 2) Auth Cloudflare via wrangler login OR CF_ACCOUNT_ID + CF_API_TOKEN
if ! wrangler whoami >/dev/null 2>&1; then
  if [ -n "${CF_ACCOUNT_ID:-}" ] && [ -n "${CF_API_TOKEN:-}" ]; then
    export CLOUDFLARE_ACCOUNT_ID="$CF_ACCOUNT_ID"
    export CLOUDFLARE_API_TOKEN="$CF_API_TOKEN"
    echo "Using CF_ACCOUNT_ID/CF_API_TOKEN from environment."
  else
    echo "Wrangler not authenticated. Run 'wrangler login' OR set CF_ACCOUNT_ID and CF_API_TOKEN, then retry."
    exit 1
  fi
fi

# 3) Build
pushd frontend >/dev/null
npm ci
npm run build
popd >/dev/null

# 4) Deploy to Cloudflare Pages
wrangler pages deploy "${DIST}" \
  --project-name="${PROJECT}" \
  --branch="${BRANCH}"

echo "Production URL: ${BASE}"

echo "Note: Using cache-buster for verification."

# 5) Automatic verifications
fail=false

section "robots.txt"
robots_headers="$(curl -sI "${BASE}/robots.txt${B}" | tr -d '\r')"
echo "${robots_headers}" | awk 'BEGIN{IGNORECASE=1}/^HTTP\//|^content-type|^referrer-policy|^x-content-type-options/'
echo "${robots_headers}" | grep -iq '^content-type: *text/plain' || { echo "ERROR: robots.txt content-type is not text/plain"; fail=true; }

section "sitemap.xml"
sitemap_headers="$(curl -sI "${BASE}/sitemap.xml${B}" | tr -d '\r')"
echo "${sitemap_headers}" | awk 'BEGIN{IGNORECASE=1}/^HTTP\//|^content-type|^referrer-policy|^x-content-type-options/'
echo "${sitemap_headers}" | grep -iq '^content-type: *application/xml' || { echo "ERROR: sitemap.xml content-type is not application/xml"; fail=true; }

section "homepage headers"
home_headers="$(curl -sI "${BASE}/${B}" | tr -d '\r')"
echo "${home_headers}" | awk 'BEGIN{IGNORECASE=1}/^HTTP\//|^referrer-policy|^x-content-type-options/'
echo "${home_headers}" | grep -iq '^referrer-policy: *strict-origin-when-cross-origin' || { echo "ERROR: Missing/incorrect Referrer-Policy on /"; fail=true; }
echo "${home_headers}" | grep -iq '^x-content-type-options: *nosniff' || { echo "ERROR: Missing/incorrect X-Content-Type-Options on /"; fail=true; }

section "SPA fallback smoke test"
about_headers="$(curl -sI "${BASE}/about${B}"   | tr -d '\r')"
pricing_headers="$(curl -sI "${BASE}/pricing${B}" | tr -d '\r')"
echo "${about_headers}"   | awk 'BEGIN{IGNORECASE=1}/^HTTP\//|^content-type/'
echo "${pricing_headers}" | awk 'BEGIN{IGNORECASE=1}/^HTTP\//|^content-type/'

echo "${about_headers}" | grep -q '^HTTP/2 200' || { echo "ERROR: /about did not return HTTP/2 200"; fail=true; }
echo "${pricing_headers}" | grep -q '^HTTP/2 200' || { echo "ERROR: /pricing did not return HTTP/2 200"; fail=true; }

echo "${about_headers}"   | grep -iq '^content-type: *text/html; *charset=utf-8' || { echo "ERROR: /about content-type is not text/html; charset=utf-8"; fail=true; }
echo "${pricing_headers}" | grep -iq '^content-type: *text/html; *charset=utf-8' || { echo "ERROR: /pricing content-type is not text/html; charset=utf-8"; fail=true; }

# 6) Exit summary
if [ "${fail}" = true ]; then
  echo
  echo "One or more checks failed."
  echo "Hints:"
  echo "- If sitemap.xml returns text/html → ensure frontend/public/sitemap.xml exists and is copied to dist with correct content-type."
  echo "- If robots.txt returns text/html → ensure frontend/public/robots.txt exists."
  echo "- If SPA routes 404 → ensure frontend/public/_redirects contains '/* /index.html 200'."
  echo "- If security headers missing → ensure frontend/public/_headers sets Referrer-Policy and X-Content-Type-Options globally."
  exit 1
fi

echo
echo "✅ Prod OK — robots/sitemap headers corrects + SPA reachable (about, pricing)."