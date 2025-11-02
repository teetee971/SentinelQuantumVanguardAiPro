# Deployment Guide - Sentinel Quantum Vanguard AI Pro

## Cloudflare Pages Deployment

### Automatic Deployment (Recommended)

The repository is configured for automatic deployment to Cloudflare Pages when changes are pushed to the `main` branch.

**Build Settings:**
- **Build command**: `cd frontend && npm install && npm run build`
- **Build output directory**: `frontend/dist`
- **Root directory**: `/`

### Manual Deployment

1. Install dependencies:
```bash
cd frontend
npm install
```

2. Build the project:
```bash
npm run build
```

3. Deploy to Cloudflare Pages:
```bash
npx wrangler pages publish frontend/dist --project-name=sentinelquantumvanguardaipro
```

### Configuration Files

- **wrangler.toml**: Cloudflare Pages configuration
  - `pages_build_output_dir`: Set to `frontend/dist`
  
- **.github/workflows/deploy.yml**: GitHub Actions workflow for automatic deployment

### Functions

The `functions/` directory contains Cloudflare Pages Functions:
- **vpnList.js**: API endpoint at `/vpnList` for fetching VPN nodes

**Note**: The root path (`/`) serves the React application, not API functions.

### Troubleshooting

**Issue**: Seeing JSON response instead of the website
- **Cause**: `functions/index.js` was intercepting the root path
- **Solution**: Removed `functions/index.js` to allow the static site to serve

**Issue**: No styling on the deployed site
- **Cause**: Missing `postcss.config.js` or wrong Tailwind version
- **Solution**: Ensure `postcss.config.js` exists and Tailwind CSS v3.x is installed

### Local Development

1. Install dependencies:
```bash
cd frontend
npm install
```

2. Run development server:
```bash
npm run dev
```

3. Access at: http://localhost:5173/

### Routes

- `/` - Home page with navigation cards
- `/diagnostic` - System diagnostic dashboard
- `/admin/vpn-console` - VPN console management

### Environment

- **Node.js**: v20.x
- **Package Manager**: npm
- **Framework**: React 18 + Vite 5
- **Styling**: Tailwind CSS v3.4
- **Deployment**: Cloudflare Pages
