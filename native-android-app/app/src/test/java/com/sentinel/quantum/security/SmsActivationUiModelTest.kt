package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsActivationUiModelTest {
    @Test
    fun readySnapshotOffersNoEscalationAction() {
        val model = SmsActivationUiModel.from(
            SmsActivationDiagnostics.Snapshot(
                state = SmsActivationDiagnostics.State.READY,
                blockers = emptySet(),
                activeSubscriptionIds = listOf(1)
            )
        )

        assertEquals(SmsActivationDiagnostics.State.READY, model.state)
        assertTrue(model.actions.isEmpty())
    }

    @Test
    fun lockedRoleAndSendPermissionOfferOnlyRequiredActions() {
        val model = SmsActivationUiModel.from(
            SmsActivationDiagnostics.Snapshot(
                state = SmsActivationDiagnostics.State.LOCKED,
                blockers = linkedSetOf(
                    SmsActivationDiagnostics.Blocker.SMS_ROLE_REQUIRED,
                    SmsActivationDiagnostics.Blocker.SEND_SMS_PERMISSION_REQUIRED
                ),
                activeSubscriptionIds = listOf(4)
            )
        )

        assertEquals(
            setOf(SmsActivationUiModel.Action.REQUEST_SMS_ROLE),
            model.actions
        )
    }

    @Test
    fun permissionsAreOfferedOnlyAfterSmsRoleIsHeld() {
        val model = SmsActivationUiModel.from(
            SmsActivationDiagnostics.Snapshot(
                state = SmsActivationDiagnostics.State.LOCKED,
                blockers = linkedSetOf(
                    SmsActivationDiagnostics.Blocker.SEND_SMS_PERMISSION_REQUIRED,
                    SmsActivationDiagnostics.Blocker.READ_PHONE_STATE_PERMISSION_REQUIRED
                ),
                activeSubscriptionIds = emptyList()
            )
        )

        assertEquals(setOf(SmsActivationUiModel.Action.REQUEST_RUNTIME_PERMISSIONS), model.actions)
    }

    @Test
    fun missingPhoneStateRequestsRuntimePermissionWithoutSimRetry() {
        val model = SmsActivationUiModel.from(
            SmsActivationDiagnostics.Snapshot(
                state = SmsActivationDiagnostics.State.LIMITED,
                blockers = setOf(SmsActivationDiagnostics.Blocker.READ_PHONE_STATE_PERMISSION_REQUIRED),
                activeSubscriptionIds = emptyList()
            )
        )

        assertEquals(setOf(SmsActivationUiModel.Action.REQUEST_RUNTIME_PERMISSIONS), model.actions)
    }

    @Test
    fun failedSubscriptionLookupOffersRetryWithoutPermissionEscalation() {
        val model = SmsActivationUiModel.from(
            SmsActivationDiagnostics.Snapshot(
                state = SmsActivationDiagnostics.State.LIMITED,
                blockers = setOf(SmsActivationDiagnostics.Blocker.SUBSCRIPTION_LOOKUP_FAILED),
                activeSubscriptionIds = emptyList()
            )
        )

        assertEquals(setOf(SmsActivationUiModel.Action.RETRY_SIM_LOOKUP), model.actions)
    }

    @Test
    fun noActiveSimDoesNotInventAnAction() {
        val model = SmsActivationUiModel.from(
            SmsActivationDiagnostics.Snapshot(
                state = SmsActivationDiagnostics.State.LIMITED,
                blockers = setOf(SmsActivationDiagnostics.Blocker.NO_ACTIVE_SIM),
                activeSubscriptionIds = emptyList()
            )
        )

        assertTrue(model.actions.isEmpty())
    }
    @Test
    fun missingReceiveSmsPermissionIsUserActivatable() {
        val model = SmsActivationUiModel.from(
            SmsActivationDiagnostics.Snapshot(
                state = SmsActivationDiagnostics.State.LOCKED,
                blockers = setOf(SmsActivationDiagnostics.Blocker.RECEIVE_SMS_PERMISSION_REQUIRED),
                activeSubscriptionIds = listOf(1)
            )
        )
        assertEquals(setOf(SmsActivationUiModel.Action.REQUEST_RUNTIME_PERMISSIONS), model.actions)
        assertTrue(model.detail.contains("Réception SMS"))
    }

    @Test
    fun missingReadSmsPermissionIsUserActivatable() {
        val model = SmsActivationUiModel.from(
            SmsActivationDiagnostics.Snapshot(
                state = SmsActivationDiagnostics.State.LOCKED,
                blockers = setOf(SmsActivationDiagnostics.Blocker.READ_SMS_PERMISSION_REQUIRED),
                activeSubscriptionIds = listOf(1)
            )
        )
        assertEquals(setOf(SmsActivationUiModel.Action.REQUEST_RUNTIME_PERMISSIONS), model.actions)
        assertTrue(model.detail.contains("Conversations SMS"))
    }
}
