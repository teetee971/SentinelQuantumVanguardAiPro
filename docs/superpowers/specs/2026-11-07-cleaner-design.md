# Sentinel Android cleaner

User: implement a cleaning system inspired by the uploaded ZHPCleaner, continue autonomously. Archive SHA2561e7994f82239711c1d61dd3851ed0d8080cf7e2f64180f65c7453d5789b58eef contains only Windows PE x86 ZHPCleaner.exe; inspected statically, never executed or imported. No source, UI or behavior replication claim.

Decision: reuse Android cleanup engine with scan, selection, explicit permanent-deletion confirmation and verified report. Separate branch from v22; no production installation or MMS v23 merge. No Windows runtime, registry emulation, root, global app-data cleaner or malware-removal claims.

Alternatives: copying the Windows binary cannot run natively on Android; broad cache deletion threatens active MMS; allowlisted stale Sentinel temporary copies is the selected safe implementation judgment. User's Continue authorizes inline implementation without further design approval gates.

Only immediate regular files in sentinel_log_export/sentinel_sms_export and root sentinel_apk_inspection_*.apk, at least24h old. Recent/unknown/future files and all other cache entries protected. No symlink traversal. Bounded scan, partial/error coverage explicit. Executor independently rechecks eligibility and freshness. Existing result verifiedFreedBytes used, never claims whole-device cleanup. No restore promise; confirmation states permanent deletion of selected temporary copies.

New cleaner screen reachable from Home and Doctor. No selection by default, no effects during scan, no main-thread filesystem work, no double execution. Share text report only after explicit tap. No new Android permission, no external network.
