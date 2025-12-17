# Cloudflare Pages Build Process

This document explains how the Cloudflare Pages deployment works for this project.

## Quick Start

To build the project for Cloudflare Pages deployment:

```bash
npm install
npm run build
```

This will create a `frontend/dist/` directory with all static files ready for deployment.

## How It Works

1. **Build Script**: `scripts/build-for-cloudflare.js`
   - Copies all static files (HTML, CSS, JS, assets) to `frontend/dist/`
   - Preserves directory structure
   - Required files: `index.html`, `public/` directory
   - Optional files: `assets/`, CSS and JS files

2. **Cloudflare Configuration**: `wrangler.toml`
   - Specifies `pages_build_output_dir = "frontend/dist"`
   - Tells Cloudflare where to find the built files

3. **Package Scripts**: `package.json`
   - `npm run build` - Runs the Cloudflare build script
   - `npm run build:vite` - Alternative Vite build (outputs to `dist/`)
   - `npm run dev` - Development server

## Cloudflare Pages Dashboard Configuration

**Required settings in Cloudflare Pages:**

- **Build command**: `npm install && npm run build`
- **Build output directory**: `frontend/dist`
- **Node.js version**: 18.x or higher

## Directory Structure

```
project-root/
├── index.html              # Main entry point
├── public/                 # Public pages
├── assets/                 # Static assets
├── scripts/
│   └── build-for-cloudflare.js  # Build script
├── wrangler.toml          # Cloudflare config
├── package.json           # NPM config
└── frontend/
    └── dist/              # Build output (gitignored)
        ├── index.html
        ├── public/
        └── assets/
```

## Troubleshooting

### Error: "Output directory 'frontend/dist' not found"

**Solution**: Make sure the build command is set in Cloudflare Pages dashboard:
- Build command: `npm install && npm run build`
- Build output directory: `frontend/dist`

### Build fails locally

**Check**:
1. Node.js version 18.x or higher: `node --version`
2. Required files exist: `index.html`, `public/` directory
3. Run: `npm run build` and check for errors

### Files not deployed

**Check**:
1. Files are copied to `frontend/dist/` after build
2. Files are not in `.gitignore` (except `frontend/dist/` itself)
3. Cloudflare build logs show successful build

## Development

For local development, you can use either:

1. **Direct file opening**: Open `index.html` in a browser
2. **Vite dev server**: `npm run dev`
3. **Vite preview**: `npm run build:vite && npm run preview`

## Files Added/Modified

- ✅ `scripts/build-for-cloudflare.js` - Build script
- ✅ `wrangler.toml` - Updated `pages_build_output_dir`
- ✅ `package.json` - Updated `build` script
- ✅ `.gitignore` - Added `frontend/dist/`
- ✅ `CLOUDFLARE_PAGES_CONFIG.md` - Updated documentation

## More Information

See `CLOUDFLARE_PAGES_CONFIG.md` for complete Cloudflare Pages configuration guide.
