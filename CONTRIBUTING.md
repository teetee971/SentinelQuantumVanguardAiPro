# Contributing — Sentinel Quantum Vanguard AI Pro

## One rule rules them all

**No change is pushed directly to `main`.** Every modification, including documentation, flows through a Pull Request (PR).

## Why

The repository contains security-sensitive workflows, public claims, and shared design systems. A reviewed PR history is the simplest, cheapest control against accidental drift, broken `main`, and unproven statements.

## Branch convention

- Feature / fix : `topic/short-description` or `fix/issue-xxx`
- Docs : `docs/short-description`
- Release / policy : `prep/short-description`

## Before opening a PR

- Keep the change focused. One concern per PR.
- Run the relevant test and audit scripts locally if you can:
  ```bash
  npm ci
  npm run test:security-governance
  npm run test:frontend-accessibility
  npm audit
  ```
- Update `ROADMAP_REALISTIC.md` only when the underlying state is actually changing.
- Do not make unverified public claims (e.g. WCAG AA, Lighthouse score, "zero vulnerabilities", "military-grade").

## PR description template

```markdown
## What changed
One sentence summary.

## Why
Reference issue or roadmap item.

## How to verify
Commands or pages to check.

## Evidence
Links to CI runs, screenshots, audit reports.
```

## Review expectations

- At least one review is required.
- Required CI checks must be green before merge.
- The author resolves conflicts and re-requests review.

## Branch protection is not a replacement for discipline

Even if GitHub branch protection is temporarily absent, every contributor follows this policy.

## Emergency fix process

If a critical fix must reach `main` immediately, use a PR with the label `emergency` and request expedited review. If no reviewer is available in time, document the bypass reason in the PR before merging.
