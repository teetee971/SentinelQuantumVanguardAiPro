# Sentinel — Security Architecture

## Objective

Keep Sentinel deterministic, auditable and fail-closed. Security claims must be backed by executable controls or observed evidence.

## Trust boundaries

`UNTRUSTED -> VALIDATED -> TRUSTED`

External feeds, user input and imported artifacts are untrusted. They must be normalized and bounded before reaching decision, storage or execution-sensitive code.

## Mandatory controls

1. Input size, depth, count and execution-time budgets at trust boundaries.
2. HTTPS-only network access and explicit source/domain allowlists.
3. Redirects disabled unless explicitly required and validated.
4. XML/JSON parsing must reject unsafe constructs and oversized payloads.
5. Security decisions fail closed when validation or policy evaluation cannot complete.
6. No security result may fabricate confidence, live status, certification or operational protection.
7. Release artifacts require successful validation, signing and provenance evidence.
8. Sentinel and A KI PRI SA YÉ remain separate projects and namespaces; negative isolation fixtures are intentional test assets.

## Release evidence

A release is not considered validated until the relevant checks have actually executed and their results are recorded. A configured workflow, queued job or documentation statement is not evidence of success.

Minimum release chain:

`source -> governance tests -> fuzzing -> isolation -> static links -> public-claim checks -> Android manifest checks -> action pinning -> build -> signed artifact -> checksum/provenance`

## Observability

Logs must be bounded, avoid sensitive values, and never become a dependency for security correctness. A logging failure must not disable a security control.

## Scope discipline

The project does not claim autonomous incident response, continuous human-equivalent monitoring, zero risk, legal certification or a published APK unless the corresponding implementation and evidence exist.
