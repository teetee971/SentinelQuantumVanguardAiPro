# Sentinel Quantum Vanguard AI Pro — coding agent instructions

These instructions apply to automated coding agents working in this repository.

## Repository scope

This repository is **Sentinel Quantum Vanguard AI Pro**. Keep it strictly isolated from **A KI PRI SA YÉ** and from unrelated retail/comparator functionality such as EAN product scanning, ticket OCR for shopping, Open Food Facts, Firestore-based retail features, commercial price comparison, newsletters, growth funnels, or retail analytics.

Do not introduce functionality from another project unless an issue in this repository explicitly establishes that scope.

## Change discipline

- Never push directly to `main`.
- Work on a dedicated branch and open a pull request.
- Keep changes minimal, reviewable, and traceable.
- Do not weaken CI, linting, CodeQL, fuzzing, isolation checks, module inventory checks, or security gates merely to obtain a green build.
- Do not hide defects with baselines, exclusions, disabled tests, or relaxed assertions unless the repository explicitly documents that behavior as intended.
- Before merge, validate the exact current PR HEAD SHA. Do not rely on stale checks from an earlier commit.
- Do not merge while any applicable required workflow is failed, cancelled, queued, pending, or stale.

## Security semantics

Sentinel security and governance code must fail closed.

Preserve the separation between:
- provenance;
- integrity;
- cryptographic authenticity;
- issuer authority;
- freshness;
- revocation;
- execution authorization.

A high model trust score must never bypass model approval, capability checks, data classification, locality constraints, exact model/version evaluation, or human authorization where required.

Authorization, approval, simulation, policy version, action, target, and execution binding must not be silently substituted after proof creation.

Do not bypass anti-replay protections. Do not convert a generic READY state directly into privileged execution when the bounded execution authorization path is required.

## Cryptographic proof handling

Structured authorization proofs currently use Ed25519 verification and repository-side trust configuration.

- Never commit real private signing keys.
- Never invent production keys, issuers, HSMs, KMSs, secrets, deployed producer identities, or production key-custody evidence.
- Verifier configuration may contain public verification material only.
- Treat private-key custody, real producer inventory, runtime issuer mapping, key rotation, and production revocation as deployment facts that require external evidence.
- Do not describe repository tests as proof that those deployment controls exist.

## Privileged execution boundary

Do not introduce autonomous privileged execution merely to connect a dormant module or reduce an orphan-module count.

`decision-plane/orchestration/bounded-execution-orchestrator.js` and related privileged orchestration paths may remain intentionally dormant until a separately reviewed runtime integration exists.

Do not add executor callbacks, shell execution, destructive actions, host isolation, account disabling, deletion, quarantine, or equivalent external side effects to an orchestration module without an explicit issue, security review, exact authorization checks, and dedicated adversarial tests.

## Module continuity

The module continuity inventory is a governance control, not a target to force to zero.

Valid classifications include repository-defined statuses such as:
- `INTENTIONALLY_DORMANT`
- `DORMANT_LIBRARY`
- `CI_TOOLING`
- `TOOLING_ENTRYPOINT`
- `DEAD_CODE_CANDIDATE` only when there is strong evidence

Do not connect or delete a module simply because it is reported as an orphan candidate. Classify it according to actual runtime/CI usage and provide a substantive reason.

Preserve local import/export contract checks, cycle detection, syntax validation, usage classification, and inventory enforcement.

## Android

Preserve the supported Android SDK range and API compatibility.

Do not raise `minSdk`, relax lint, or add lint baselines solely to silence a real compatibility defect. Prefer explicit SDK guards and tests.

Release signing material must never be committed. Debug/release separation must remain explicit.

## GitHub Actions and supply chain

- Keep third-party GitHub Actions pinned to immutable full commit SHAs when that is the repository policy.
- Keep workflow permissions minimal.
- Avoid `pull_request_target` for untrusted code unless a security review proves it necessary and safe.
- Do not expose secrets to untrusted PR code.
- Do not assume GitHub App tokens have a fixed length or a fixed `ghs_` representation.

## Production claims

Do not claim any of the following without matching evidence:
- production-ready;
- formally verified;
- zero vulnerabilities;
- 100% secure;
- full production cryptographic authenticity;
- deployed key custody;
- deployed HSM/KMS;
- production runtime enforcement.

Repository tests prove only what they directly exercise.

## Issue handling

- Keep issue #215 open until real deployment-side producer inventory, public-key provisioning, private-key custody, rotation/revocation, and deployed runtime trust mapping are evidenced.
- Keep issue #225 open until GitHub reports an active branch protection rule or ruleset protecting `main` with the intended checks.
- Treat old issues that conflict with current bounded-execution or isolation architecture as historical requirements until revalidated; do not implement them blindly.

## Validation before completion

For relevant changes, run the repository scripts that cover the touched area. Common gates include:

- `npm run test:syntax`
- `npm run test:module-continuity`
- `npm run test:module-interface-contracts`
- `npm run test:module-cycles`
- `npm run test:module-usage-report`
- `npm run test:module-inventory`
- `npm run test:security-governance`
- `npm run test:security-execution`
- `npm run test:security-fuzz`
- `npm run test:isolation`

When Android code is touched, also require unit tests, `lintDebug`, and the APK build workflow.

Do not report completion while a known applicable check is not green on the exact current HEAD.