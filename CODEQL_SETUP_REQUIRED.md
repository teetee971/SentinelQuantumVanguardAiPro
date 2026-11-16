# CodeQL Configuration — External Repository Setting

## Status

Sentinel uses an **explicit CodeQL workflow** (`.github/workflows/codeql-analysis.yml`) that is now properly hardened for the repository's actual technology stack.

## Problem

GitHub may have **Default Setup enabled** for CodeQL in the repository's external settings. This causes:
1. **Duplicate CodeQL runs** (unnecessary redundancy)
2. **Configuration conflicts** between Default Setup and explicit workflow
3. **Wasted CI resources**

## Solution Required (Cannot be Done in Code)

**This action MUST be performed in the GitHub UI by a repository administrator:**

### Steps:
1. Navigate to: https://github.com/teetee971/SentinelQuantumVanguardAiPro/settings/security_analysis
2. Locate: **Code scanning**
3. Find: **CodeQL analysis** (or similar "Code scanning" section)
4. Look for: **Default Setup** toggle or similar
5. **DISABLE** Default Setup if enabled
6. Confirm that only the explicit workflow `.github/workflows/codeql-analysis.yml` is active

### Verification:
After disabling Default Setup:
- CodeQL should run **only** from the explicit workflow
- Check the **Actions** tab: only one CodeQL run should appear per push/PR, not multiples
- Workflow should target only `javascript-typescript` and `actions`

## Current Explicit Configuration

**File**: `.github/workflows/codeql-analysis.yml`

**Targets**:
- `javascript-typescript` — Web frontend (HTML/CSS/JS)
- `actions` — GitHub Actions workflow analysis

**No autobuild** — These languages need no compilation.

**Pinning**: All actions use full 40-character SHA commits for supply-chain integrity.

## Timeline

- Configuration added: 2026-09-04
- Simplification: Commit `8b3e7f0c8f166d42343394cfe95af945b2b56b33` removed unnecessary autobuild
- This document: Created to guide external configuration

## Questions?

Refer to official GitHub documentation:
- [CodeQL setup for compiled languages](https://docs.github.com/en/code-security/code-scanning/introduction-to-code-scanning/about-code-scanning-with-codeql)
- [Managing CodeQL workflow in your repository](https://docs.github.com/en/code-security/code-scanning/introduction-to-code-scanning/configuring-code-scanning)
