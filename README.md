# SentinelQuantumVanguardAiPro

<a href="https://github.com/teetee971/SentinelQuantumVanguardAiPro/actions/workflows/deploy-pages.yml"><img src="https://github.com/teetee971/SentinelQuantumVanguardAiPro/actions/workflows/deploy-pages.yml/badge.svg"></a>
<a href="https://github.com/teetee971/SentinelQuantumVanguardAiPro/actions/workflows/site-health.yml"><img src="https://github.com/teetee971/SentinelQuantumVanguardAiPro/actions/workflows/site-health.yml/badge.svg"></a>

Deployment: Cloudflare Pages with automated checks (robots/sitemap/headers/SPA).

## Manual deploy
```
chmod +x scripts/deploy_cf_pages.sh
npm run deploy:pages
```

## CI Secrets
- CF_ACCOUNT_ID
- CF_API_TOKEN (permission: Cloudflare Pages: Edit)
