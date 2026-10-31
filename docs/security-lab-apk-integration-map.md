# Security Lab Pro — Android integration map (WIP)

Issue: #1749. Branch: feat/security-lab-apk-discovery-20261030.

## Verified from repository source

- MainActivity.kt mounts navigation.NavGraph and does not directly render Security Lab Pro.
- navigation/NavGraph.kt does not expose a named Security Lab Pro / APK route in the inspected version.
- UserSelectedApkDiscovery.kt is a read-only SAF tree scanner, not a UI or installer.
- VisibleInstalledPackageInventory.kt reads only Android-visible installed packages; it does not guarantee access to their APK files.
- ApkDocumentPickerContract.kt prepares user-consented file/folder picker intents, but does not launch them.

## Release gate

Do not claim the screenshot's Security Lab Pro UI is wired to these classes without identifying its exact activity/composable and its provenance in the installed APK.

Before connecting an install action:
1. Locate the real screen and its navigation entry, or establish whether v18 was built from a different branch.
2. Map the existing APK analysis/install implementation; reuse it rather than introducing a parallel installer.
3. Add a user-visible inventory with explicit loading, error, permission-revoked and empty states.
4. Review URI grants and content provider failure modes, including cursor exceptions and scan limits.
5. Run Android compilation and tests, then test on the Galaxy S24+ using a harmless test APK.
6. Keep Phone Core and SMS/MMS role behavior untouched; do not merge to main without evidence.

Current status: backend scaffolding only. No verified functional UI, installer, or build.
