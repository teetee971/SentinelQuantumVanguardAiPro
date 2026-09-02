# Sentinel Security Digital Twin

The Security Digital Twin is a deterministic, security-scoped representation of assets, identities, services, vulnerabilities, observations, incidents and controls. It is not an enterprise data lake and is not a source of truth for arbitrary AI output.

## Security invariants

- Every state-changing fact has provenance and a timestamp.
- Observations, inferences, hypotheses and decisions remain distinct.
- AI output is untrusted input; it cannot mutate the twin directly.
- Updates are applied only after schema and policy validation.
- Sources must be public or explicitly authorized.
- Data minimisation is mandatory; secrets and credentials are excluded.
- Sentinel has no dependency or data path to A KI PRI SA YÉ.

## Flow

```text
Authorized Sources
      ↓
Normalized Observation
      ↓
Schema + Provenance Validation
      ↓
Security Digital Twin
      ↓
Evidence Graph / Correlation
      ↓
AI Orchestrator
      ↓
Structured Proposal
      ↓
Policy Guardian
      ↓
Approved State Transition / Defensive Action
```

The twin should evolve toward event-sourced storage so that every material state transition can be reconstructed and audited.