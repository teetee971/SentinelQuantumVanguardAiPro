# SentinelQuantumVanguardAiPro

<a href="https://github.com/teetee971/SentinelQuantumVanguardAiPro/actions/workflows/deploy-pages.yml"><img src="https://github.com/teetee971/SentinelQuantumVanguardAiPro/actions/workflows/deploy-pages.yml/badge.svg"></a>
<a href="https://github.com/teetee971/SentinelQuantumVanguardAiPro/actions/workflows/site-health.yml"><img src="https://github.com/teetee971/SentinelQuantumVanguardAiPro/actions/workflows/site-health.yml/badge.svg"></a>

Déploiement: Cloudflare Pages (script automatisé + vérifications robots/sitemap/headers/SPA).

## Déploiement manuel
```
chmod +x scripts/deploy_cf_pages.sh
npm run deploy:pages
```

## CI: secrets requis
- CF_ACCOUNT_ID
- CF_API_TOKEN (permission "Cloudflare Pages: Edit")
