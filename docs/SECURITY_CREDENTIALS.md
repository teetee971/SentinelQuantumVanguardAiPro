# Credential Handling and Security Incident #259

This document records the repository-side handling of security incident
[#259](https://github.com/teetee971/SentinelQuantumVanguardAiPro/issues/259) and the
rules that apply to credentials in this repository.

Repository changes cannot revoke a credential. Revocation and rotation are external
actions performed by the maintainer on each provider. This document therefore separates
what the repository enforces from what remains a manual, evidence-required action.

## 1. Incident summary

GitHub Secret Scanning reported 10 open alerts marked **Public leak** on the repository
history. The exposed credential classes named in issue #259 are:

| Class | Provider | Repository-side status |
|---|---|---|
| Personal access tokens | GitHub | Not present in `HEAD` |
| SSH private key | GitHub account key | Not present in `HEAD` |
| OAuth access/refresh tokens | Google | Not present in `HEAD` |
| API keys | Google | Not present in `HEAD` |
| Bot tokens | Telegram | Not present in `HEAD` |

No secret value is reproduced here, in the issue, or in any tracked file.

## 2. Repository state

`npm run test:credential-hygiene` verifies, on every tracked file of the current tree:

1. no local credential artifact is tracked (`.git-credentials`, `.ssh/`, SSH private keys,
   shell history, Firebase CLI credential/config files, `*.pem`, `*.key`, keystores,
   `.env`, `.npmrc`, `.netrc`, `secrets/`, service-account JSON);
2. `.gitignore` still declares the required credential ignore patterns;
3. no tracked text file contains high-signal credential material (GitHub tokens,
   Google API keys and OAuth tokens, Telegram bot tokens, private key blocks,
   AWS access key identifiers, Slack tokens).

The gate fails closed: any finding fails the check.

This gate covers the current tree only. It makes no statement about the Git history,
about forks and clones already created, or about whether an exposed credential is still
valid at its provider.

## 3. Manual revocation checklist (maintainer action required)

The agent has no access to provider consoles. Each item below must be performed and
verified manually. Do not close issue #259 before every item is confirmed.

- [ ] GitHub PATs — revoke every exposed token in
      *Settings → Developer settings → Personal access tokens*, then re-issue with the
      narrowest scope required.
- [ ] GitHub SSH key — delete the exposed public key in *Settings → SSH and GPG keys*,
      generate a new key pair locally, and register only the new public key.
- [ ] Google OAuth tokens — revoke the client's access in the Google account permissions
      page and invalidate the refresh token, then re-authorize through a clean flow.
- [ ] Google API keys — regenerate the key and apply application/API restrictions, or
      delete the key if it is no longer used.
- [ ] Telegram bot tokens — issue `/revoke` then `/token` through BotFather for each
      affected bot.
- [ ] Replacement secrets — store them only in GitHub Actions secrets or an approved
      secret manager, never in a tracked file.
- [ ] Secret Scanning alerts — triage each of the 10 alerts individually and close an
      alert only after revocation or invalidity is confirmed.
- [ ] History rewrite — consider only after revocation is complete, and coordinate with
      all collaborators beforehand. History rewriting is not a substitute for rotation;
      existing clones and forks may retain the exposed values.

## 4. Rules for contributors

- Never commit a credential, private key, keystore, `.env` file, or provider
  configuration containing a secret value.
- Local values go in `.env` (ignored by Git). Use `.env.example` for placeholder names
  only, with obvious dummy values such as `replace_me_*`.
- Deployment secrets live in GitHub Actions secrets. The active Android signing secrets
  are listed in `SECURITY.md`.
- Test fixtures must use clearly fake values (`dummy_`, `test_`, `replace_me_`).
- Run `npm run test:credential-hygiene` before pushing. It can also be wired locally as a
  pre-commit hook:

  ```bash
  printf '#!/bin/sh\nnpm run test:credential-hygiene\n' > .git/hooks/pre-commit
  chmod +x .git/hooks/pre-commit
  ```

  Local hooks are not tracked by Git and are a convenience, not an enforced control.

## 5. Reporting

If a credential is exposed again, treat it as compromised immediately: revoke first,
clean the repository second, and record the incident in this document.
Do not include any secret value in an issue or pull request.
