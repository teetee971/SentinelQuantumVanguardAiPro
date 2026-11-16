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

    @Test fun firstMissingStateDeterminesStepInLeastPrivilegeOrder() {
        assertEquals(PhoneCoreSetupWizardStore.Step.DIALER_ROLE, PhoneCoreSetupWizardStore.nextStep(facts()))
        assertEquals(PhoneCoreSetupWizardStore.Step.CALL_SCREENING_ROLE, PhoneCoreSetupWizardStore.nextStep(facts(dialer = true)))
        assertEquals(PhoneCoreSetupWizardStore.Step.CORE_PERMISSIONS, PhoneCoreSetupWizardStore.nextStep(facts(dialer = true, screening = true)))
        assertEquals(PhoneCoreSetupWizardStore.Step.CALL_LOG_PERMISSION, PhoneCoreSetupWizardStore.nextStep(facts(core = true, dialer = true, screening = true)))
        assertEquals(PhoneCoreSetupWizardStore.Step.SMS_ROLE, PhoneCoreSetupWizardStore.nextStep(facts(core = true, dialer = true, screening = true, callLog = true)))
        assertEquals(PhoneCoreSetupWizardStore.Step.SMS_PERMISSIONS, PhoneCoreSetupWizardStore.nextStep(facts(core = true, dialer = true, screening = true, callLog = true, smsRole = true)))
        assertEquals(PhoneCoreSetupWizardStore.Step.MMS_PERMISSIONS, PhoneCoreSetupWizardStore.nextStep(facts(core = true, dialer = true, screening = true, callLog = true, smsRole = true, smsPermissions = true)))
        assertEquals(PhoneCoreSetupWizardStore.Step.NOTIFICATION_CHANNELS, PhoneCoreSetupWizardStore.nextStep(facts(core = true, dialer = true, screening = true, callLog = true, smsRole = true, smsPermissions = true, mms = true)))
    }

    @Test fun runtimePermissionsNeverPrecedePhoneRoles() {
        assertEquals(
            PhoneCoreSetupWizardStore.Step.DIALER_ROLE,
            PhoneCoreSetupWizardStore.nextStep(facts(core = false, dialer = false, screening = false))
        )
        assertEquals(
            PhoneCoreSetupWizardStore.Step.CALL_SCREENING_ROLE,
            PhoneCoreSetupWizardStore.nextStep(facts(core = false, dialer = true, screening = false))
        )
        assertEquals(
            PhoneCoreSetupWizardStore.Step.CORE_PERMISSIONS,
            PhoneCoreSetupWizardStore.nextStep(facts(core = false, dialer = true, screening = true))
        )
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

    @Test fun completedSetupStaysClosedOnlyWhileRuntimeFactsRemainReady() {
        val ready = facts(true, true, true, true, true, true, true, true)
        assertEquals(false, PhoneCoreSetupWizardStore.shouldOpenSetup(true, ready))
    }

    @Test fun completedSetupReopensAfterRuntimeRevocation() {
        val revokedSmsRole = facts(true, true, true, true, false, true, true, true)
        assertEquals(true, PhoneCoreSetupWizardStore.shouldOpenSetup(true, revokedSmsRole))

        val revokedNotification = facts(true, true, true, true, true, true, true, false)
        assertEquals(true, PhoneCoreSetupWizardStore.shouldOpenSetup(true, revokedNotification))
    }

    @Test fun incompleteSetupStillOpensEvenWhenRuntimeFactsAreCurrentlyReady() {
        val ready = facts(true, true, true, true, true, true, true, true)
        assertEquals(true, PhoneCoreSetupWizardStore.shouldOpenSetup(false, ready))
    }

    @Test fun everyRepairStepHasAUserFacingFrenchLabel() {
        PhoneCoreSetupWizardStore.Step.entries.forEach { step ->
            val label = PhoneCoreSetupWizardStore.stepLabel(step)
            assertEquals(false, label.isBlank())
            assertEquals(false, label.contains("_"))
        }
    }

    @Test fun everySetupStepHasRationaleAndPrivacyCopy() {
        PhoneCoreSetupWizardStore.Step.entries.forEach { step ->
            assertEquals(false, PhoneCoreSetupWizardStore.stepRationale(step).isBlank())
            assertEquals(false, PhoneCoreSetupWizardStore.stepPrivacyNote(step).isBlank())
        }
    }

    @Test fun setupProgressMatchesRoleFirstFlowAndCompleteIsTerminal() {
        assertEquals(1 to 8, PhoneCoreSetupWizardStore.stepProgress(PhoneCoreSetupWizardStore.Step.DIALER_ROLE))
        assertEquals(2 to 8, PhoneCoreSetupWizardStore.stepProgress(PhoneCoreSetupWizardStore.Step.CALL_SCREENING_ROLE))
        assertEquals(3 to 8, PhoneCoreSetupWizardStore.stepProgress(PhoneCoreSetupWizardStore.Step.CORE_PERMISSIONS))
        assertEquals(5 to 8, PhoneCoreSetupWizardStore.stepProgress(PhoneCoreSetupWizardStore.Step.SMS_ROLE))
        assertEquals(8 to 8, PhoneCoreSetupWizardStore.stepProgress(PhoneCoreSetupWizardStore.Step.NOTIFICATION_CHANNELS))
        assertEquals(8 to 8, PhoneCoreSetupWizardStore.stepProgress(PhoneCoreSetupWizardStore.Step.COMPLETE))
    }

    @Test fun unavailableRoleRemainsBlockingButIsNotActionable() {
        val dialerUnavailable = facts().copy(dialerRoleAvailable = false)
        assertEquals(PhoneCoreSetupWizardStore.Step.DIALER_ROLE, PhoneCoreSetupWizardStore.nextStep(dialerUnavailable))
        assertEquals(false, PhoneCoreSetupWizardStore.isStepActionable(PhoneCoreSetupWizardStore.Step.DIALER_ROLE, dialerUnavailable))

        val screeningUnavailable = facts(dialer = true).copy(callScreeningRoleAvailable = false)
        assertEquals(PhoneCoreSetupWizardStore.Step.CALL_SCREENING_ROLE, PhoneCoreSetupWizardStore.nextStep(screeningUnavailable))
        assertEquals(false, PhoneCoreSetupWizardStore.isStepActionable(PhoneCoreSetupWizardStore.Step.CALL_SCREENING_ROLE, screeningUnavailable))

        val smsUnavailable = facts(core = true, dialer = true, screening = true, callLog = true).copy(smsRoleAvailable = false)
        assertEquals(PhoneCoreSetupWizardStore.Step.SMS_ROLE, PhoneCoreSetupWizardStore.nextStep(smsUnavailable))
        assertEquals(false, PhoneCoreSetupWizardStore.isStepActionable(PhoneCoreSetupWizardStore.Step.SMS_ROLE, smsUnavailable))
    }

    @Test fun availableMissingRoleRemainsActionable() {
        val dialerMissing = facts()
        assertEquals(true, PhoneCoreSetupWizardStore.isStepActionable(PhoneCoreSetupWizardStore.Step.DIALER_ROLE, dialerMissing))

        val screeningMissing = facts(dialer = true)
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

    @Test fun externalSettingsProgressOffersExplicitContinueInsteadOfStalling() {
        val persistedAttempt = PhoneCoreSetupWizardStore.targetKey(
            PhoneCoreSetupWizardStore.Step.CORE_PERMISSIONS,
            "android.permission.CALL_PHONE"
        )
        val recomputedTarget = PhoneCoreSetupWizardStore.targetKey(
            PhoneCoreSetupWizardStore.Step.CORE_PERMISSIONS,
            "android.permission.READ_CONTACTS"
        )
        assertEquals(
            true,
            PhoneCoreSetupWizardStore.shouldOfferManualContinue(
                targetKey = recomputedTarget,
                lastAttemptedTargetKey = persistedAttempt,
                actionable = true
            )
        )
        assertEquals(
            false,
            PhoneCoreSetupWizardStore.shouldOfferManualContinue(
                targetKey = persistedAttempt,
                lastAttemptedTargetKey = persistedAttempt,
                actionable = true
            )
        )
    }

    @Test fun permissionLabelsAreHumanReadableAndFrench() {
        assertEquals(
            "Passer et gérer les appels",
            PhoneCoreSetupWizardStore.permissionLabel("android.permission.CALL_PHONE")
        )
        assertEquals(
            "Contacts",
            PhoneCoreSetupWizardStore.permissionLabel("android.permission.READ_CONTACTS")
        )
        assertEquals(
            "Recevoir les MMS (WAP Push)",
            PhoneCoreSetupWizardStore.permissionLabel("android.permission.RECEIVE_WAP_PUSH")
        )
        assertEquals(
            "Autorisation Android",
            PhoneCoreSetupWizardStore.permissionLabel("android.permission.UNKNOWN")
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
        val target = PhoneCoreSetupWizardStore.targetKey(PhoneCoreSetupWizardStore.Step.DIALER_ROLE)
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
