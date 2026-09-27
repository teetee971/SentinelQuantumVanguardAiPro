package com.sentinel.quantum

import android.content.Context

/**
 * Persistent first-run Phone Core setup state.
 *
 * Runtime truth remains authoritative: [nextStep] is recomputed after every Android return.
 * Persistence only records whether a step has already been presented, so a refusal never
 * becomes a success and the app never loops system dialogs automatically.
 */
internal class PhoneCoreSetupWizardStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun attemptedStep(): Step? =
        prefs.getString(KEY_ATTEMPTED_STEP, null)?.let { runCatching { Step.valueOf(it) }.getOrNull() }

    fun markAttempted(step: Step) {
        prefs.edit().putString(KEY_ATTEMPTED_STEP, step.name).commit()
    }

    fun clearAttempted() {
        prefs.edit().remove(KEY_ATTEMPTED_STEP).commit()
    }

    fun markCompleted() {
        prefs.edit()
            .putBoolean(KEY_COMPLETED, true)
            .remove(KEY_ATTEMPTED_STEP)
            .commit()
    }

    fun isCompleted(): Boolean = prefs.getBoolean(KEY_COMPLETED, false)

    enum class Step {
        CORE_PERMISSIONS,
        DIALER_ROLE,
        CALL_SCREENING_ROLE,
        CALL_LOG_PERMISSION,
        SMS_ROLE,
        SMS_PERMISSIONS,
        MMS_PERMISSIONS,
        NOTIFICATION_CHANNELS,
        COMPLETE
    }

    data class Facts(
        val corePermissionsReady: Boolean,
        val dialerRoleHeld: Boolean,
        val callScreeningRoleHeld: Boolean,
        val callLogPermissionGranted: Boolean,
        val smsRoleHeld: Boolean,
        val smsRuntimePermissionsReady: Boolean,
        val mmsPermissionsReady: Boolean,
        val notificationChannelsReady: Boolean
    )

    companion object {
        private const val PREFS = "phone_core_setup_wizard_v2"
        private const val KEY_ATTEMPTED_STEP = "attempted_step"
        private const val KEY_COMPLETED = "completed"

        fun nextStep(facts: Facts): Step = when {
            !facts.corePermissionsReady -> Step.CORE_PERMISSIONS
            !facts.dialerRoleHeld -> Step.DIALER_ROLE
            !facts.callScreeningRoleHeld -> Step.CALL_SCREENING_ROLE
            !facts.callLogPermissionGranted -> Step.CALL_LOG_PERMISSION
            !facts.smsRoleHeld -> Step.SMS_ROLE
            !facts.smsRuntimePermissionsReady -> Step.SMS_PERMISSIONS
            !facts.mmsPermissionsReady -> Step.MMS_PERMISSIONS
            !facts.notificationChannelsReady -> Step.NOTIFICATION_CHANNELS
            else -> Step.COMPLETE
        }
    }
}
