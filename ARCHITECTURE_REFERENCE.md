# Sentinel Quantum Vanguard AI Pro — Architecture Reference

**Version:** 2.2  
**Status:** Current architecture reference  
**Scope:** Sentinel Quantum Vanguard AI Pro only

## 1. Boundary

Sentinel is an independent security project. It must not import, bundle, authenticate against, deploy, or depend on another application or project. Firebase components and configuration not required by Sentinel are forbidden on operational surfaces.

Documentation may mention forbidden integrations when documenting the isolation policy; executable code, build configuration, manifests and deployment configuration must remain clean.

## 2. Current architecture

### Web surface

- Static web/PWA surface built from the repository root.
- Cloudflare Pages is the intended web deployment surface.
- No Firebase dependency.
- No backend service is required by the current static deployment path.

### Security and governance core

The repository contains local security-governance components covering model registration and policy, model evaluation, trust scoring, evidence provenance, red-team simulation, model routing, action safety, impact simulation and immutable audit events.

AI output is treated as untrusted input. Sensitive actions require policy approval, evidence integrity, sufficient trust, a safe simulation and, for critical actions, target authorization plus human validation.

### Android

`native-android-app/` is the canonical native Android application/build surface. The active Android build and signed-release workflows target this directory.

The previous `android-app/` React Native release/build workflows are no longer part of the active CI/CD path and have been removed from `.github/workflows` to prevent parallel legacy releases.

## 3. CI/CD security model

Active workflows use least-privilege permissions and immutable SHA-pinned third-party GitHub Actions. Security gates include:

- Sentinel isolation validation;
- AI governance validation;
- security-governance validation;
- deterministic security fuzzing;
- OSINT validation;
- CodeQL analysis;
- repository integrity checks;
- frontend validation;
- native Android build validation;
- tag-controlled signed Android releases.

Release publication is restricted to version tags whose commit is reachable from `main`. Signing material is supplied only through GitHub Actions secrets and is removed after the release job.

The repository uses one canonical Sentinel isolation scanner, `scripts/check-sentinel-isolation.js`, invoked by the dedicated isolation workflow and the integrity gate. Redundant legacy isolation workflow/script pairs must not be reintroduced without distinct security coverage.

## 4. Deployment policy

The repository must not contain an active GitHub Pages deployment workflow for the production web surface. Production web deployment remains separate from Android release publication.

No workflow may introduce credentials, dependencies or deployment configuration belonging to another project.

## 5. Validation status

A code change is not considered security-validated merely because it was committed. Validation requires:

1. the relevant workflow to start successfully;
2. its actual steps to execute;
3. all required gates to pass;
4. dependency and supply-chain checks to pass;
5. the Sentinel isolation gate to pass;
6. no unexplained CI infrastructure failure to be treated as a security pass.

This distinction is mandatory: **corrected ≠ tested ≠ CI-passed ≠ security-proven**.

## 6. Non-goals

Sentinel does not claim immunity from attacks, autonomous unrestricted defensive action, or detection of every real-world threat. Simulation and red-team results are evidence about tested controls, not proof of universal security.
