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

    fun attemptedTargetKey(): String? =
        prefs.getString(KEY_ATTEMPTED_TARGET, null)
            ?: prefs.getString(KEY_ATTEMPTED_STEP, null)?.also {
                // Legacy macro-step keys intentionally do not equal new atomic target keys.
            }

    fun markAttemptedTarget(targetKey: String) {
        prefs.edit()
            .putString(KEY_ATTEMPTED_TARGET, targetKey)
            .remove(KEY_ATTEMPTED_STEP)
            .remove(KEY_ATTEMPTED_TARGET)
            .commit()
    }

    fun clearAttempted() {
        prefs.edit().remove(KEY_ATTEMPTED_STEP).commit()
    }

    fun markCompleted() {
        prefs.edit()
            .putBoolean(KEY_COMPLETED, true)
            .remove(KEY_ATTEMPTED_STEP)
            .remove(KEY_ATTEMPTED_TARGET)
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
        val dialerRoleAvailable: Boolean = true,
        val callScreeningRoleHeld: Boolean,
        val callScreeningRoleAvailable: Boolean = true,
        val callLogPermissionGranted: Boolean,
        val smsRoleHeld: Boolean,
        val smsRoleAvailable: Boolean = true,
        val smsRuntimePermissionsReady: Boolean,
        val mmsPermissionsReady: Boolean,
        val notificationChannelsReady: Boolean
    )

    companion object {
        private const val PREFS = "phone_core_setup_wizard_v2"
        private const val KEY_ATTEMPTED_STEP = "attempted_step"
        private const val KEY_ATTEMPTED_TARGET = "attempted_target"
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

        /**
         * Single pure truth for the setup prerequisites represented by [Facts].
         * A completed wizard preference is never used as proof: runtime facts remain authoritative.
         */
        fun softwarePrerequisitesReady(facts: Facts): Boolean =
            nextStep(facts) == Step.COMPLETE

        fun isStepActionable(step: Step, facts: Facts): Boolean = when (step) {
            Step.DIALER_ROLE -> facts.dialerRoleAvailable && !facts.dialerRoleHeld
            Step.CALL_SCREENING_ROLE -> facts.callScreeningRoleAvailable && !facts.callScreeningRoleHeld
            Step.SMS_ROLE -> facts.smsRoleAvailable && !facts.smsRoleHeld
            Step.COMPLETE -> false
            else -> true
        }


        fun firstMissingPermission(candidates: List<Pair<String, Boolean>>): String? =
            candidates.firstOrNull { (_, granted) -> !granted }?.first

        fun targetKey(step: Step, atomicId: String? = null): String =
            if (atomicId == null) step.name else "${step.name}:$atomicId"

        fun shouldAutoLaunch(targetKey: String, lastAttemptedTargetKey: String?): Boolean =
            targetKey != lastAttemptedTargetKey

        fun stepLabel(step: Step): String = when (step) {
            Step.CORE_PERMISSIONS -> "Autoriser les fonctions essentielles"
            Step.DIALER_ROLE -> "Définir Sentinel comme application Téléphone"
            Step.CALL_SCREENING_ROLE -> "Activer l’identification et le filtrage des appels"
            Step.CALL_LOG_PERMISSION -> "Autoriser l’historique des appels"
            Step.SMS_ROLE -> "Définir Sentinel comme application SMS"
            Step.SMS_PERMISSIONS -> "Autoriser l’envoi et la réception des SMS"
            Step.MMS_PERMISSIONS -> "Autoriser la réception des MMS"
            Step.NOTIFICATION_CHANNELS -> "Activer les notifications appels et messages"
            Step.COMPLETE -> "Prérequis logiciels prêts"
        }
    }
}
