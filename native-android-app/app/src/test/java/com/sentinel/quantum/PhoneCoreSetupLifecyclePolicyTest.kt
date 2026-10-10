package com.sentinel.quantum

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneCoreSetupLifecyclePolicyTest {
    private fun facts(ready: Boolean) = PhoneCoreSetupWizardStore.Facts(
        corePermissionsReady = ready,
        dialerRoleHeld = ready,
        dialerRoleAvailable = ready,
        callScreeningRoleHeld = ready,
        callScreeningRoleAvailable = ready,
        callLogPermissionGranted = ready,
        smsRoleHeld = ready,
        smsRoleAvailable = ready,
        smsRuntimePermissionsReady = ready,
        mmsPermissionsReady = ready,
        mmsSafePreviewValidated = ready,
        notificationChannelsReady = ready
    )

    @Test fun deferredSetupDoesNotAutoReopenAndDoesNotBecomeReady() {
        val incomplete = facts(false)
        assertFalse(
            PhoneCoreSetupWizardStore.shouldAutoOpenSetup(
                PhoneCoreSetupWizardStore.LifecycleState.DEFERRED,
                incomplete
            )
        )
        assertFalse(PhoneCoreSetupWizardStore.softwarePrerequisitesReady(incomplete))
    }

    @Test fun interruptedSetupResumesAutomatically() {
        assertTrue(
            PhoneCoreSetupWizardStore.shouldAutoOpenSetup(
                PhoneCoreSetupWizardStore.LifecycleState.IN_PROGRESS,
                facts(false)
            )
        )
    }

    @Test fun completedSetupStaysQuietOnlyWhileRuntimeFactsRemainReady() {
        assertFalse(
            PhoneCoreSetupWizardStore.shouldAutoOpenSetup(
                PhoneCoreSetupWizardStore.LifecycleState.COMPLETED,
                facts(true)
            )
        )
        assertTrue(
            PhoneCoreSetupWizardStore.shouldAutoOpenSetup(
                PhoneCoreSetupWizardStore.LifecycleState.COMPLETED,
                facts(false)
            )
        )
    }

    @Test fun freshSetupIsOfferedEvenBeforeAnyPersistedSuccess() {
        assertTrue(
            PhoneCoreSetupWizardStore.shouldAutoOpenSetup(
                PhoneCoreSetupWizardStore.LifecycleState.NOT_STARTED,
                facts(false)
            )
        )
    }
}
