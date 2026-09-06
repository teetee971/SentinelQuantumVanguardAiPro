# Sentinel OWNER administrative access

## Purpose

The `OWNER` role is the highest product-administration role in Sentinel. It is intended for the software creator/owner and grants complete administrative control over the Sentinel product itself: configuration, diagnostics, inspection, recovery, feature management, policy management, audit access/export, user administration, simulation administration and deployment administration.

This role is deliberately distinct from operational authorization to act on external systems or third-party targets.

## OWNER principle

Product ownership grants full authority over Sentinel's own configuration and lifecycle. It does not create a universal authorization to act against arbitrary external targets.

The implementation therefore separates:

1. **Administrative authority over Sentinel** — granted to an authenticated, MFA-verified `OWNER` for every declared OWNER administrative capability.
2. **Operational mission authority** — still requires the existing Sentinel authorization chain for sensitive or consequential actions.

## Non-bypassable controls

Even the `OWNER` role does not silently bypass:

- target authorization;
- human validation for sensitive actions;
- evidence-integrity checks;
- trust thresholds;
- safe-simulation or separately authorized execution boundaries;
- proof authenticity and freshness;
- anti-replay controls;
- audit trail requirements;
- the kill switch.

These controls protect the owner account itself from credential theft, operator error and accidental execution outside an authorized mission.

## Identity binding

The repository currently contains policy and validation logic, not a production identity provider. A production deployment must bind the real owner identity to `OWNER` server-side through a trusted IdP or authentication backend. The binding must not rely on a browser flag, URL parameter, local-storage value, hard-coded public username or other client-side assertion.

Recommended production binding:

- unique immutable subject identifier;
- phishing-resistant MFA/passkey or hardware-backed MFA;
- short-lived authenticated sessions;
- explicit audit events for OWNER actions;
- recovery process with independent credentials;
- no shared OWNER account;
- immediate revocation capability.

## Break-glass administration

Emergency recovery may use a separately provisioned break-glass owner credential. It should be offline by default, strongly authenticated, independently auditable and reserved for loss of normal administration or incident recovery. Break-glass access still does not manufacture authorization for external targets.

## Security invariant

`OWNER` means **maximum control of Sentinel**, not **unconditional bypass of Sentinel's security boundary**.
