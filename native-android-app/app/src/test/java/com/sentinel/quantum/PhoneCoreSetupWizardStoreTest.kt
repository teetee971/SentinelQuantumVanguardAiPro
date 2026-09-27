package com.sentinel.quantum

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneCoreSetupWizardStoreTest {
    private fun facts(
        core: Boolean = false,
        dialer: Boolean = false,
        screening: Boolean = false,
        callLog: Boolean = false,
        smsRole: Boolean = false,
        smsPermissions: Boolean = false,
        mms: Boolean = false,
        notifications: Boolean = false
    ) = PhoneCoreSetupWizardStore.Facts(
        corePermissionsReady = core,
        dialerRoleHeld = dialer,
        callScreeningRoleHeld = screening,
        callLogPermissionGranted = callLog,
        smsRoleHeld = smsRole,
        smsRuntimePermissionsReady = smsPermissions,
        mmsPermissionsReady = mms,
        notificationChannelsReady = notifications
    )

    @Test fun firstMissingStateDeterminesStep() {
        assertEquals(PhoneCoreSetupWizardStore.Step.CORE_PERMISSIONS, PhoneCoreSetupWizardStore.nextStep(facts()))
        assertEquals(PhoneCoreSetupWizardStore.Step.DIALER_ROLE, PhoneCoreSetupWizardStore.nextStep(facts(core = true)))
        assertEquals(PhoneCoreSetupWizardStore.Step.CALL_SCREENING_ROLE, PhoneCoreSetupWizardStore.nextStep(facts(core = true, dialer = true)))
        assertEquals(PhoneCoreSetupWizardStore.Step.CALL_LOG_PERMISSION, PhoneCoreSetupWizardStore.nextStep(facts(core = true, dialer = true, screening = true)))
        assertEquals(PhoneCoreSetupWizardStore.Step.SMS_ROLE, PhoneCoreSetupWizardStore.nextStep(facts(core = true, dialer = true, screening = true, callLog = true)))
        assertEquals(PhoneCoreSetupWizardStore.Step.SMS_PERMISSIONS, PhoneCoreSetupWizardStore.nextStep(facts(core = true, dialer = true, screening = true, callLog = true, smsRole = true)))
        assertEquals(PhoneCoreSetupWizardStore.Step.MMS_PERMISSIONS, PhoneCoreSetupWizardStore.nextStep(facts(core = true, dialer = true, screening = true, callLog = true, smsRole = true, smsPermissions = true)))
        assertEquals(PhoneCoreSetupWizardStore.Step.NOTIFICATION_CHANNELS, PhoneCoreSetupWizardStore.nextStep(facts(core = true, dialer = true, screening = true, callLog = true, smsRole = true, smsPermissions = true, mms = true)))
    }

    @Test fun completeRequiresEverySequentialPrerequisite() {
        assertEquals(
            PhoneCoreSetupWizardStore.Step.COMPLETE,
            PhoneCoreSetupWizardStore.nextStep(facts(true, true, true, true, true, true, true, true))
        )
    }

    @Test fun earlierRegressionMovesWizardBackToRealMissingState() {
        assertEquals(
            PhoneCoreSetupWizardStore.Step.DIALER_ROLE,
            PhoneCoreSetupWizardStore.nextStep(facts(core = true, dialer = false, screening = true, callLog = true, smsRole = true, smsPermissions = true, mms = true, notifications = true))
        )
    }
    @Test fun softwareReadinessUsesRuntimeFactsNotPersistence() {
        assertEquals(
            false,
            PhoneCoreSetupWizardStore.softwarePrerequisitesReady(
                facts(core = true, dialer = true, screening = true, callLog = true, smsRole = true, smsPermissions = true, mms = true, notifications = false)
            )
        )
        assertEquals(
            true,
            PhoneCoreSetupWizardStore.softwarePrerequisitesReady(
                facts(true, true, true, true, true, true, true, true)
            )
        )
    }

    @Test fun everyRepairStepHasAUserFacingFrenchLabel() {
        PhoneCoreSetupWizardStore.Step.entries.forEach { step ->
            val label = PhoneCoreSetupWizardStore.stepLabel(step)
            assertEquals(false, label.isBlank())
            assertEquals(false, label.contains("_"))
        }
    }

}
