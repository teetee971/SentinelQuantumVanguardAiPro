# Evidence / Trust Model

Sentinel reports only the verification level demonstrated by its evidence.

## Levels

- `UNVERIFIED`: required evidence is missing, malformed, or cannot be attributed.
- `STATIC_VERIFIED`: repository evidence is structurally valid, but execution provenance is absent.
- `CI_VERIFIED`: GitHub Actions executed deterministic checks and the evidence matches the exact workflow, commit, ref, run, and attempt.
- `PRODUCTION_VERIFIED`: reserved for independently authenticated deployment evidence; CI cannot produce this level.
- `BLOCKED`: a failed control prevents a positive claim.

## Boundaries

Code existing does not prove it was tested; a workflow existing does not prove it ran; and a successful build does not prove a production deployment. A valid authorization record also does not authenticate its issuer, and an anti-replay implementation does not prove that its durable store was exercised.

Evidence hashes protect report integrity, not the truth of the underlying observations. Release attestations can bind artifacts to repository provenance but do not establish security by themselves.
