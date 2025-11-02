# Documentation PDF Files

This directory contains official documentation files for Sentinel Quantum Vanguard AI Pro.

## Current Files

- **Sentinel_Quantum_Vanguard_AI_Pro_Executive_Brief.pdf** - Executive brief document

## Access URLs

### Local Development
```
http://localhost:5173/assets/docs/Sentinel_Quantum_Vanguard_AI_Pro_Executive_Brief.pdf
```

### Production
```
https://sentinelquantumvanguardaipro.pages.dev/assets/docs/Sentinel_Quantum_Vanguard_AI_Pro_Executive_Brief.pdf
```

## Adding New Documents

1. Place PDF files in this directory: `frontend/public/assets/docs/`
2. Files will be automatically copied to the build output (`dist/assets/docs/`)
3. Access them via `/assets/docs/filename.pdf` in the browser

## Verification

Run the verification script to check the setup:

```bash
cd frontend
node verify-docs.js
```
