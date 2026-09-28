# Zero Trust Orchestrator

The orchestrator is a meta-control. It does not replace CodeQL, fuzzing, isolation, build, release, or physical Android validation.

## Evidence levels

1. **Repository and CI integrity** — immutable revision identity, least-privilege workflow permissions, secret and repository integrity checks.
2. **Inter-module boundaries** — Sentinel project isolation and domain-specific boundaries such as Social Intelligence versus Phone Core.
3. **Artifact integrity and provenance** — build outputs tied to a revision; validation artifacts are not promoted to production artifacts; signed release evidence remains owned by the dedicated Android release workflow.
4. **Observed Android runtime state** — software prerequisites and the Phone Core physical certification matrix remain runtime evidence. CI success does not imply 14/14 physical validation.

## Fail-closed invariants

- A missing required workflow or required control marker is a failure.
- Validation APK/AAB workflows must not be represented as production signing.
- The AAB validation workflow may remain intentionally unsigned.
- Production signing remains isolated in `.github/workflows/android-release.yml` and its protected environment.
- The orchestrator does not consume signing secrets.
- Runtime certification cannot be synthesized from CI status.
- Hardware-backed device attestation is reported only when a concrete supported attestation mechanism is implemented and verified; it is never inferred from package or OS checks.
