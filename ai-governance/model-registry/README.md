# Sentinel Model Registry

Security-governed registry for AI models used by Sentinel Quantum Vanguard AI Pro.

## Rules

- A model is untrusted until explicitly approved.
- Model eligibility is evaluated against data classification and task risk.
- Provider identity does not imply trust.
- Every production inference must be attributable to a registered model/version.
- Remote models are forbidden for data classes that policy marks as local-only.
- The registry contains metadata and policy decisions, never API keys or credentials.
- Model output is untrusted data and must pass schema validation and Policy Guardian checks before any security action.

## Separation boundary

This registry belongs exclusively to Sentinel Quantum Vanguard AI Pro. It must not import, deploy, authenticate against, or depend on A KI PRI SA YÉ, Firebase, or another application's credentials.
