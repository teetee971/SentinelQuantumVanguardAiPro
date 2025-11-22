# SentinelQuantumVanguardAiPro

[![Deploy to Cloudflare Pages](https://github.com/teetee971/SentinelQuantumVanguardAiPro/actions/workflows/deploy-pages.yml/badge.svg)](https://github.com/teetee971/SentinelQuantumVanguardAiPro/actions/workflows/deploy-pages.yml)
[![Site Health Check](https://github.com/teetee971/SentinelQuantumVanguardAiPro/actions/workflows/site-health.yml/badge.svg)](https://github.com/teetee971/SentinelQuantumVanguardAiPro/actions/workflows/site-health.yml)

## Overview

SentinelQuantumVanguardAiPro is a modern web application built with React and Vite, deployed on Cloudflare Pages.

**Production URL:** https://sentinelquantumvanguardaipro.pages.dev

## CI/CD Workflows

### Deploy to Cloudflare Pages
Automatically deploys the application to Cloudflare Pages on:
- Push to `main` branch (when frontend files change)
- Daily schedule (2 AM UTC)
- Manual dispatch

### Site Health Check
Weekly automated health checks (Monday 8 AM UTC) that verify:
- robots.txt accessibility and content-type
- sitemap.xml accessibility and content-type
- Security headers (Referrer-Policy, X-Content-Type-Options)
- SPA routes (/about, /pricing)

## Local Development

### Prerequisites
- Node.js >= 20
- npm

### Setup
```bash
# Install dependencies
cd frontend
npm ci

# Run development server
npm run dev

# Build for production
npm run build
```

## Manual Deployment

### Prerequisites
1. Install wrangler CLI:
   ```bash
   npm install -g wrangler@3
   ```

2. Authenticate with Cloudflare:
   ```bash
   wrangler login
   ```
   
   Or set environment variables:
   ```bash
   export CF_ACCOUNT_ID="your-cloudflare-account-id"
   export CF_API_TOKEN="your-cloudflare-api-token"
   ```

### Deploy
```bash
# From repository root
npm run deploy:pages
```

The deployment script will:
1. Build the frontend with Vite
2. Deploy to Cloudflare Pages project `sentinelquantumvanguardaipro`
3. Verify robots.txt and sitemap.xml
4. Check security headers
5. Test SPA routes

## GitHub Secrets for CI/CD

To enable automated deployments via GitHub Actions, configure the following repository secrets:

| Secret Name | Description | Required Permissions |
|-------------|-------------|---------------------|
| `CF_ACCOUNT_ID` | Your Cloudflare Account ID | - |
| `CF_API_TOKEN` | Cloudflare API Token | **Cloudflare Pages: Edit** |

### How to Get Your Cloudflare Credentials

1. **Account ID:**
   - Log in to [Cloudflare Dashboard](https://dash.cloudflare.com)
   - Select your account
   - Find Account ID in the right sidebar

2. **API Token:**
   - Go to [API Tokens](https://dash.cloudflare.com/profile/api-tokens)
   - Click "Create Token"
   - Use "Edit Cloudflare Pages" template or create custom token with:
     - Permissions: `Account` → `Cloudflare Pages` → `Edit`
     - Account Resources: Include your specific account
   - Copy the token (you'll only see it once)

3. **Add to GitHub:**
   - Go to repository Settings → Secrets and variables → Actions
   - Click "New repository secret"
   - Add `CF_ACCOUNT_ID` and `CF_API_TOKEN`

**Note:** The first workflow run may fail if secrets are not yet configured. This is expected behavior.

## Project Structure

```
├── frontend/                 # React + Vite frontend
│   ├── public/              # Static assets
│   │   ├── _headers         # Cloudflare Pages headers
│   │   ├── _redirects       # Cloudflare Pages redirects
│   │   ├── robots.txt       # SEO robots file
│   │   └── sitemap.xml      # SEO sitemap
│   ├── src/                 # React source code
│   └── package.json         # Frontend dependencies
├── scripts/                 # Deployment and utility scripts
│   └── deploy_cf_pages.sh   # Cloudflare Pages deployment script
├── .github/workflows/       # GitHub Actions workflows
└── package.json             # Root package.json for deployment
```

## Static Assets

The following static assets are served from Cloudflare Pages:

- **/_headers** - Sets security headers (Referrer-Policy, X-Content-Type-Options)
- **/_redirects** - Configures SPA fallback routing
- **/robots.txt** - Search engine crawling instructions
- **/sitemap.xml** - Site structure for search engines

## License

[Your License Here]
