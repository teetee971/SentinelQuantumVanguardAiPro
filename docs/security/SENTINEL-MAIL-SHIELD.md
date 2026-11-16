# Sentinel Mail Shield — pipeline v1

Status: foundation only. This document does not claim mailbox integration is active.

Pipeline:
1. bounded raw-message ingest;
2. local header / Received-chain analysis;
3. observed SPF, DKIM and DMARC result analysis;
4. local IOC extraction (URLs, public IP literals, email addresses, phone numbers);
5. attachment-name/type indicators and lookalike-domain signals;
6. deterministic disposition: NORMAL, REVIEW, SUSPICIOUS, or QUARANTINE_CANDIDATE.

Privacy and safety boundaries:
- no Gmail, Microsoft 365, IMAP or SMTP credentials in this module;
- no mailbox mutation, deletion or automatic quarantine;
- no DNS, IP geolocation, ASN or reputation network lookup yet;
- no URL opening and no attachment execution;
- QUARANTINE_CANDIDATE is a recommendation, not an automatic destructive action;
- remote enrichment must later be gated by ProtectionMode.ENHANCED plus feature-specific consent.

Planned follow-up stages:
- OAuth provider adapters with least-privilege scopes;
- normalized message envelope and provider-neutral mailbox connector;
- provenance graph for Received hops and observable public IPs;
- separately consented IP/ASN/country/reputation enrichment with confidence/provenance;
- reversible quarantine and audit journal;
- provider-specific webhook/polling ingestion with replay protection and bounded rate limits.
