# Test-only reputation preference isolation

Continuation of review work on verified v22 base fafe9f5b, with production sources unchanged.

The Android runtime gate remains necessary for PhoneCoreSetupResumeInstrumentationTest because it clears production wizard history and opens real system setup UI. CommunicationReputationStorageInstrumentationTest now obtains a fresh IsolatedPreferencesContext for each @Before instead of altering production preferences. Its seven original @Test method bodies were moved verbatim into shared CommunicationReputationStorageCases, inherited by AndroidJUnit4 and a host Robolectric SDK35 runner. There is no @Ignore and no global disposable-device gate on these isolated cases.

The context returns itself from applicationContext, preventing nested SettingsStore and HMAC-secret lookups from escaping to defaults. Only the three preference stores used by this code are accepted, in MODE_PRIVATE; each maps to a fresh UUID-prefixed name. Unknown stores fail, closing clears only opened aliases and fails if cleanup fails, and use after close is rejected. The helper is test-only and does not claim to sandbox arbitrary future Android APIs. Current production SettingsStore/adapter paths were inspected: only these preferences are used, with no WorkManager/service/network effects from these setters.

TDD: five host isolation cases all failed with a forwarding ContextWrapper, then passed with scoped preferences. Tests verify transitive applicationContext identity, actual unmodified SettingsStore consent downgrade/purge and reputation/HMAC writes without changing seeded production-namespace maps, independence of contexts, rejection of unknown stores, cleanup and closed-context rejection. Production sentinels exist ONLY inside the host Robolectric sandbox, never on a phone. Shared Android cases do not read, seed, clear or restore production preferences.

Targeted host validation:43 tests,0 failures/errors/skips (5 isolation +7 real storage cases +6 gate/JUnit lifecycle +25 wizard logic). Robolectric4.16.1 is testImplementation only; runtime API35 SDK is pre-cached for --offline. This is not Samsung/API37 physical evidence.

Read-only independent review inspected applicationContext, nested consent purge/HMAC paths, cleanup and removing the reputation gate. No concrete Critical/Important issue found; reviewer ran no tests/device actions. Android execution remains blocked by no connected ADB device, no installed emulator/system image and no /dev/kvm. No install or destructive opt-in is performed.
