# Documents Directory

This directory contains official PDF documents for the Sentinel Quantum Vanguard AI Pro project.

## Required File

Place the following PDF file in this directory:
- **Sentinel_Quantum_Vanguard_AI_Pro_Executive_Brief.pdf**

## Access URLs

Once the PDF is placed here, it will be accessible at:

### Local Development
```
http://localhost:5173/assets/docs/Sentinel_Quantum_Vanguard_AI_Pro_Executive_Brief.pdf
```

### Production (Cloudflare Pages)
```
https://sentinelquantumvanguardaipro.pages.dev/assets/docs/Sentinel_Quantum_Vanguard_AI_Pro_Executive_Brief.pdf
```

## Verification

To verify the PDF setup, run from the `frontend` directory:
```bash
npm run verify-pdf
```

## Notes

- This folder is part of the Vite `public` directory, so files here are served as static assets
- Files in this directory are copied as-is to the build output without processing
- No import statements are needed - just reference the file path directly in your code
