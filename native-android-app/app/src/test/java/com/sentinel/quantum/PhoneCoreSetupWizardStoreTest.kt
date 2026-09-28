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

    @Test fun globalNotificationDisableBelongsToNotificationStepNotCorePermissions() {
        val runtimeFacts = facts(
            core = true,
            dialer = true,
            screening = true,
            callLog = true,
            smsRole = true,
            smsPermissions = true,
            mms = true,
            notifications = false
        )
        assertEquals(
            PhoneCoreSetupWizardStore.Step.NOTIFICATION_CHANNELS,
            PhoneCoreSetupWizardStore.nextStep(runtimeFacts)
        )
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


    @Test fun unavailableRoleRemainsBlockingButIsNotActionable() {
        val dialerUnavailable = facts(core = true).copy(dialerRoleAvailable = false)
        assertEquals(PhoneCoreSetupWizardStore.Step.DIALER_ROLE, PhoneCoreSetupWizardStore.nextStep(dialerUnavailable))
        assertEquals(false, PhoneCoreSetupWizardStore.isStepActionable(PhoneCoreSetupWizardStore.Step.DIALER_ROLE, dialerUnavailable))

        val screeningUnavailable = facts(core = true, dialer = true).copy(callScreeningRoleAvailable = false)
        assertEquals(PhoneCoreSetupWizardStore.Step.CALL_SCREENING_ROLE, PhoneCoreSetupWizardStore.nextStep(screeningUnavailable))
        assertEquals(false, PhoneCoreSetupWizardStore.isStepActionable(PhoneCoreSetupWizardStore.Step.CALL_SCREENING_ROLE, screeningUnavailable))

        val smsUnavailable = facts(core = true, dialer = true, screening = true, callLog = true).copy(smsRoleAvailable = false)
        assertEquals(PhoneCoreSetupWizardStore.Step.SMS_ROLE, PhoneCoreSetupWizardStore.nextStep(smsUnavailable))
        assertEquals(false, PhoneCoreSetupWizardStore.isStepActionable(PhoneCoreSetupWizardStore.Step.SMS_ROLE, smsUnavailable))
    }

    @Test fun availableMissingRoleRemainsActionable() {
        val dialerMissing = facts(core = true)
        assertEquals(true, PhoneCoreSetupWizardStore.isStepActionable(PhoneCoreSetupWizardStore.Step.DIALER_ROLE, dialerMissing))

        val screeningMissing = facts(core = true, dialer = true)
        assertEquals(true, PhoneCoreSetupWizardStore.isStepActionable(PhoneCoreSetupWizardStore.Step.CALL_SCREENING_ROLE, screeningMissing))

        val smsMissing = facts(core = true, dialer = true, screening = true, callLog = true)
        assertEquals(true, PhoneCoreSetupWizardStore.isStepActionable(PhoneCoreSetupWizardStore.Step.SMS_ROLE, smsMissing))
    }

    @Test fun firstMissingPermissionIsDeterministic() {
        assertEquals(
            "permission.b",
            PhoneCoreSetupWizardStore.firstMissingPermission(
                listOf(
                    "permission.a" to true,
                    "permission.b" to false,
                    "permission.c" to false
                )
            )
        )
    }

    @Test fun firstMissingPermissionReturnsNullWhenAllGranted() {
        assertEquals(
            null,
            PhoneCoreSetupWizardStore.firstMissingPermission(
                listOf("permission.a" to true, "permission.b" to true)
            )
        )
    }

    @Test fun atomicTargetChangesWhenNextPermissionBecomesCurrent() {
        val first = PhoneCoreSetupWizardStore.targetKey(
            PhoneCoreSetupWizardStore.Step.CORE_PERMISSIONS,
            "android.permission.CALL_PHONE"
        )
        val second = PhoneCoreSetupWizardStore.targetKey(
            PhoneCoreSetupWizardStore.Step.CORE_PERMISSIONS,
            "android.permission.READ_PHONE_STATE"
        )
        assertEquals(true, PhoneCoreSetupWizardStore.shouldAutoLaunch(second, first))
    }

    @Test fun refusedAtomicTargetDoesNotAutoLoop() {
        val target = PhoneCoreSetupWizardStore.targetKey(
            PhoneCoreSetupWizardStore.Step.CORE_PERMISSIONS,
            "android.permission.CALL_PHONE"
        )
        assertEquals(false, PhoneCoreSetupWizardStore.shouldAutoLaunch(target, target))
    }

    @Test fun externalSettingsReturnAdvancesVisualTargetWithoutAutoPrompt() {
        val persistedAttempt = PhoneCoreSetupWizardStore.targetKey(
            PhoneCoreSetupWizardStore.Step.CORE_PERMISSIONS,
            "android.permission.CALL_PHONE"
        )
        val recomputedTarget = PhoneCoreSetupWizardStore.targetKey(
            PhoneCoreSetupWizardStore.Step.CORE_PERMISSIONS,
            "android.permission.READ_PHONE_STATE"
        )
        assertEquals(
            false,
            PhoneCoreSetupWizardStore.shouldAutoLaunch(
                recomputedTarget,
                persistedAttempt,
                allowTargetAdvance = false
            )
        )
    }

    @Test fun wizardSequentialFlowAllowsAutoPromptWithinActiveSession() {
        val persistedAttempt = PhoneCoreSetupWizardStore.targetKey(
            PhoneCoreSetupWizardStore.Step.CORE_PERMISSIONS,
            "android.permission.CALL_PHONE"
        )
        val recomputedTarget = PhoneCoreSetupWizardStore.targetKey(
            PhoneCoreSetupWizardStore.Step.CORE_PERMISSIONS,
            "android.permission.READ_PHONE_STATE"
        )
        assertEquals(
            true,
            PhoneCoreSetupWizardStore.shouldAutoLaunch(
                recomputedTarget,
                persistedAttempt,
                allowTargetAdvance = true
            )
        )
    }

    @Test fun firstSetupTargetStillAutoLaunchesWithoutPriorAttempt() {
        val target = PhoneCoreSetupWizardStore.targetKey(
            PhoneCoreSetupWizardStore.Step.CORE_PERMISSIONS,
            "android.permission.CALL_PHONE"
        )
        assertEquals(
            true,
            PhoneCoreSetupWizardStore.shouldAutoLaunch(
                target,
                lastAttemptedTargetKey = null,
                allowTargetAdvance = false
            )
        )
    }

    @Test fun completeTargetNeverNeedsAutomaticLaunch() {
        val complete = PhoneCoreSetupWizardStore.targetKey(PhoneCoreSetupWizardStore.Step.COMPLETE)
        assertEquals(false, PhoneCoreSetupWizardStore.isStepActionable(PhoneCoreSetupWizardStore.Step.COMPLETE, facts()))
        assertEquals(false, PhoneCoreSetupWizardStore.shouldAutoLaunch(complete, complete))
    }

}
