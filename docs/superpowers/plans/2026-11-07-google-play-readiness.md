# Google Play qualification implementation plan

Goal: prepare a verifiable Android candidate without claiming Play acceptance.
Architecture: retain v22 Phone Core and isolated instrumentation tests; exclude the known failing JPEG experiment. Fail closed for optional account login until an HTTPS deletion request route is configured. Preserve logout. Keep unsigned qualification separate from upload signing.
Constraints: no installation, production keys, role changes, main merge or paid Actions. API 36 meets the official requirement retrieved 2026-11-07. Version 24/1.0.10 is provisional pending Play Console maximum.

1. Add failing pure JVM tests for account deletion URL validation (HTTPS, authority, credentials, malformed input). Implement policy and integrate only account actions in Settings. Add public Gradle property and reject signed release without a valid deletion URL.
2. Run targeted then full available JVM tests, compile AndroidTest, assemble debug, bundle releaseUnsigned, lint and manifest checks. Inspect native alignment and unsigned signature. No physical result inferred from host tests.
3. Realign the two roadmaps and store draft with actual primary SMS/dialer functions and explicitly blocked capabilities. Document public privacy/access/deletion, restricted permissions, upload signature, version and device tests as separate gates.
4. Record exact commands, counts, hashes, artifacts and residual external blockers; independent review before final delivery. No approval or publication claim.

Review focus: logged-in users retain logout; hostile URLs cannot start account flow; local Phone Core never needs login; missing config blocks signed release but permits unsigned analysis; historical evidence never becomes current physical PASS.
