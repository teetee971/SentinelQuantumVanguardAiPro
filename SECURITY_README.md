# Security README - Sentinel Quantum Vanguard AI Pro

**Date :** 2 septembre 2026  
**Version :** security-baseline-2026  
**Security Level :** HARDENED

## Security boundary

Sentinel Quantum Vanguard AI Pro is an independent project.

**Mandatory isolation rule:** Sentinel must not import, bundle, deploy, authenticate against, or depend on A KI PRI SA YÉ. Project identifiers, package names, Firebase configuration, API credentials, assets and deployment configuration belonging to A KI PRI SA YÉ are prohibited.

**Cloud deployment:** Cloudflare Pages only.  
**Firebase deployment:** disabled.  
**Third-party project configuration:** prohibited.

## Secrets and credentials

Secrets must be supplied through CI/CD secret storage or the runtime environment and must never be hardcoded. Private keys, passwords, bearer tokens, signing material, database credentials and credentials/configuration belonging to another project are prohibited in source.

There is intentionally no Firebase deployment credential in the Sentinel build configuration.

## Network security

HTTPS is mandatory for remote communications. Cleartext HTTP must be disabled unless a narrowly scoped exception is explicitly documented and justified. Remote endpoints must be reviewed and Sentinel must not call endpoints belonging to A KI PRI SA YÉ or another unrelated project.

## Dependency security

Dependencies must be inventoried and reviewed for known vulnerabilities before release. Unused SDKs and transitive services must be removed rather than merely disabled in documentation. The current architecture requires Firebase independence.

## Fuzzing / robustness

Security testing must include malformed and oversized input, invalid encodings, unexpected JSON/schema fields, path traversal attempts, authorization boundary checks, null/empty values, concurrency/race conditions where applicable, and crash/DoS-oriented cases.

## CI/CD security gates

A release must be blocked when a cross-project identifier, Firebase configuration/deployment activation, hardcoded credential, unacceptable dependency vulnerability, failed security test, unauthorized endpoint, integrity mismatch, or unexpected artifact is detected.

## Android permissions

Permissions must follow least privilege. Dangerous permissions must be justified by implemented features and verified against the actual release manifest; documentation alone is not evidence.

## Incident response

If a credential or signing material is exposed, revoke/rotate it through the relevant provider immediately. Removing a file from the current branch does not remove it from Git history; historical exposure must be assessed separately.

## Release checklist

- [ ] Tests pass
- [ ] Security/fuzz tests pass
- [ ] Dependencies checked
- [ ] No hardcoded secrets
- [ ] No A KI PRI SA YÉ identifiers or artifacts
- [ ] No Firebase dependency or deployment path
- [ ] Network endpoints reviewed
- [ ] Android permissions reviewed against the actual manifest
- [ ] Release artifact integrity verified
- [ ] CI/CD gates pass

**Last Updated :** 2 September 2026  
**Status :** Hardened baseline; implementation claims require CI verification.
