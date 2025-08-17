#!/usr/bin/env bash
set -euo pipefail

ACCOUNT_ID="78642e56f72fff94c78e1ef87cb589a7"
PROJECT_NAME="sentinelquantumvanguardai"

if [ -z "${CLOUDFLARE_API_TOKEN:-}" ]; then
  echo "❌ CLOUDFLARE_API_TOKEN non défini"; exit 1
fi

echo "🚀 Déploiement Cloudflare Pages: $PROJECT_NAME"
curl -sS -X POST \
  -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" \
  -H "Content-Type: application/json" \
  "https://api.cloudflare.com/client/v4/accounts/$ACCOUNT_ID/pages/projects/$PROJECT_NAME/deployments" \
  --data '{}' | jq '.'
