# Roadmap finalization execution plan

Goal: integrate the already reviewed Doctor cleaner into the isolated Play candidate and close every locally verifiable gate without modifying installed v22 or claiming external acceptance.

Constraints: no main merge, production install, data/role/key changes, fabricated account service or JPEG success. Parent 8169dc50a33376ed157513667c1686b194def765; candidate remains provisional 24/1.0.10 pending Play maximum.

1. Import exact cleaner regression tests from reviewed source 7f0db292; execute targeted tests against current candidate to demonstrate missing integration.
2. Apply only the 17 Android cleaner files from that source. Merge the single added string with account lifecycle strings. Preserve all Play and isolated-test changes.
3. Run targeted cleaner tests, complete JVM regression, compile AndroidTest, build debug and separately installable labPreview, lint and manifest/security checks. Never install automatically. Build releaseUnsigned if available disk permits; retain existing artifacts and label new source separately.
4. Prepare operational Play declaration/application-access/physical-test checklists without guessing owner data, account deletion endpoint, keys or Play status. Reconcile both roadmaps and canonical capability states; unavailable capabilities remain unavailable.
5. Independently review integration, record exact commands/results/hashes, publish draft stacked PR without main merge.

JPEG blocker: existing Android decoder can silently recover truncated entropy despite partial-input rejection. A marker-only or size heuristic is not an acceptable fix. Preserve original failing test and isolated branch unless a strict validated decoder becomes available.
