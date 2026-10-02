# Local ITU prefix lookup foundation

`public/phone-numbering-global.js` exposes `createGlobalNumberingLookup(manifest)`.
It builds a small prefix index once; each synchronous lookup examines at most seven
prefixes. It performs no fetch and never loads national allocation datasets. This
module is a foundation for enriched Phone Intelligence; it is not yet connected to
Android CallScreening or the production UI.

The generated manifest derives from the ITU National Numbering Plans catalogue
snapshot fetched on 2026-10-02, identified by its source SHA-256 and registry
SHA-256. Its 239 catalogue records are not a complete inventory of allocated
E.164 resources. `coverageComplete` remains false. The assigned-code bulletin,
global services and shared-plan subareas require reconciliation. An unlisted code
returns `REFERENCE_ENTRY_NOT_FOUND`, never an invalid-plan or fraud verdict.

A matching prefix provides catalogue evidence only. National lengths, assignment
status, current carrier, subscriber identity and spoofing are not established.
+1 remains ambiguous between the US and Canada unless an explicit catalogue NPA
identifies another NANP area. +7 and +599 remain ambiguous. Catalogue global
network prefixes retain `SHARED_NETWORK` scope. Original ITU labels are preserved.

Regenerate after the global source registry PR is available:

```sh
node scripts/generate-numbering-lookup-manifest.js \
  config/global-numbering-source-registry.json \
  public/data/numbering-lookup-manifest.json
node --test public/phone-numbering-global.test.js
```

The registry is introduced by PR #1461; this PR carries a generated snapshot to
remain independently reviewable on current main. Refresh automation must update
this manifest together with the registry in a reviewed PR. Read-only CI compares the generated manifest with the registry when the registry
is present; the source snapshot remains explicit until then.
No automatic network dependency is introduced into Phone Core. Allocation and
portability enrichment remain separate future layers.
