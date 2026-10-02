# Global numbering coverage — initial ITU catalogue

The source of geographic scope is the official ITU National Numbering Plans catalogue retrieved on 2 October 2026, with original designations, official record links and SHA-256 provenance. The current E.164 recommendation page identifies E.164 (02/26) as in force. The older assigned-country-code publication listed by ITU is dated 15 December 2016; it must be reconciled with subsequent Operational Bulletin amendments before a complete allocation scope can be claimed.

`config/global-numbering-source-registry.json` currently records every entry extracted from that NNP catalogue, including each NANP member and the international network entries published there. Shared calling codes, NPA lists and international network identifiers remain separate. ISO enrichment never defines the master list. Names remain the original ITU technical labels and express no sovereignty position.

This is an initial registry, not a worldwide-coverage claim. The explicit outstanding scopes are assigned-code/bulletin reconciliation, global services and shared-plan subareas. Jersey, Guernsey, Isle of Man, Vatican and territorial components of compound/shared entries must be reconciled from official documents; they must not be inferred from a political country list. The catalogue's 7 network entries are not assumed to be all current global network allocations. The secured ITU allocation database is not accessed or bypassed.

The registry reports source investigation as `SOURCE_NOT_YET_VERIFIED` until actual evidence supports another status. Lack of research is never classified as `NO_PUBLIC_BULK_DATA` or `NOT_FOUND`. Public-portability availability remains unknown when unverified. A repository audit establishes importer presence; it does not establish live source freshness or source reuse rights.

Main has committed France and Austria datasets. Ofcom, ACM and ČTÚ have importers/tests, but no committed snapshot was found in the audited main tree; they are `IMPORTER_READY`, not `INTEGRATED`. These distinctions are machine-readable. The five existing importers still need the complete global safety matrix and shared anti-rollback work; this registry does not certify those controls finished.

Run `node scripts/numbering-sources/common/itu-registry.js` to regenerate the registry/dashboard, then `node --test scripts/global-numbering-coverage.test.js`. Coverage tests derive the expected count from the independent ITU reference, reject missing/duplicate areas, altered prefixes, lost shared-code semantics and integrated entries without importer/test/snapshot. CI checks regeneration produces no difference.

`docs/data/global-numbering-coverage.json` is the computed dashboard. Its worldwide percentage remains null until scope reconciliation is complete. Continent counts await verified UN M49 enrichment. Catalogue enumeration and detailed allocation integration are separate metrics.

This pass adds no network dependency to Android call screening, caller-identity inference, current-carrier inference or fraud verdict. Subsequent work must keep plan, regulatory allocation, portability, reputation and subscriber identity separate.
