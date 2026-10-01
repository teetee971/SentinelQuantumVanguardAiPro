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
            .commit()
    }

    fun clearAttempted() {
        prefs.edit()
            .remove(KEY_ATTEMPTED_STEP)
            .remove(KEY_ATTEMPTED_TARGET)
            .commit()
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

        /**
         * Persisted completion is historical UX state, never runtime proof.
         * A completed setup must reopen when Android facts later regress.
         */
        fun shouldOpenSetup(persistedCompleted: Boolean, facts: Facts): Boolean =
            !persistedCompleted || !softwarePrerequisitesReady(facts)

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

        fun shouldAutoLaunch(
            targetKey: String,
            lastAttemptedTargetKey: String?,
            allowTargetAdvance: Boolean = true
        ): Boolean =
            lastAttemptedTargetKey == null ||
                (allowTargetAdvance && targetKey != lastAttemptedTargetKey)

        /**
         * When the user changes a permission/role from Android settings, the first-run
         * assistant intentionally does not pop the next system dialog automatically.
         * It must still expose an explicit Continue action or the wizard appears stuck.
         */
        fun shouldOfferManualContinue(
            targetKey: String,
            lastAttemptedTargetKey: String?,
            actionable: Boolean
        ): Boolean =
            actionable && targetKey != lastAttemptedTargetKey

        fun permissionLabel(permission: String): String = when (permission) {
            "android.permission.CALL_PHONE" -> "Passer et gérer les appels"
            "android.permission.READ_PHONE_STATE" -> "État du téléphone et SIM"
            "android.permission.READ_CONTACTS" -> "Contacts"
            "android.permission.POST_NOTIFICATIONS" -> "Notifications"
            "android.permission.READ_CALL_LOG" -> "Journal d’appels"
            "android.permission.SEND_SMS" -> "Envoyer des SMS"
            "android.permission.READ_SMS" -> "Lire les SMS"
            "android.permission.RECEIVE_SMS" -> "Recevoir les SMS"
            "android.permission.RECEIVE_MMS" -> "Recevoir les MMS"
            "android.permission.RECEIVE_WAP_PUSH" -> "Recevoir les MMS (WAP Push)"
            else -> "Autorisation Android"
        }

        fun stepProgress(step: Step): Pair<Int, Int> {
            val actionable = Step.entries.filterNot { it == Step.COMPLETE }
            val position = actionable.indexOf(step)
            return if (position >= 0) (position + 1) to actionable.size else actionable.size to actionable.size
        }

        fun stepRationale(step: Step): String = when (step) {
            Step.CORE_PERMISSIONS ->
                "Permettre à Sentinel de lancer un appel, connaître l’état téléphonique nécessaire au multi-SIM, afficher vos contacts localement et vous notifier."
            Step.DIALER_ROLE ->
                "Le rôle Téléphone permet d’utiliser le composeur Sentinel et les contrôles d’appel intégrés."
            Step.CALL_SCREENING_ROLE ->
                "Le rôle de filtrage permet à Android de demander à Sentinel une décision locale avant certains appels entrants."
            Step.CALL_LOG_PERMISSION ->
                "L’historique permet d’afficher vos appels récents dans Sentinel lorsque le rôle Téléphone est réellement détenu."
            Step.SMS_ROLE ->
                "Le rôle SMS permet à Sentinel d’envoyer, recevoir et organiser les SMS dans l’application."
            Step.SMS_PERMISSIONS ->
                "Ces autorisations servent uniquement aux opérations SMS que le rôle Android permet réellement à Sentinel d’exécuter."
            Step.MMS_PERMISSIONS ->
                "Ces autorisations permettent la réception MMS. La capacité opérationnelle reste distincte tant qu’elle n’est pas validée sur appareil réel."
            Step.NOTIFICATION_CHANNELS ->
                "Les notifications rendent visibles les appels et messages ; le plein écran d’appel dépend aussi des réglages Android."
            Step.COMPLETE ->
                "Tous les prérequis logiciels suivis par cet assistant sont actuellement présents."
        }

        fun stepPrivacyNote(step: Step): String = when (step) {
            Step.CORE_PERMISSIONS ->
                "Les contacts restent traités localement par ce parcours. Les autorisations sont accordées ou refusées par Android."
            Step.DIALER_ROLE, Step.CALL_SCREENING_ROLE, Step.SMS_ROLE ->
                "Changer une application par défaut ou un rôle est une décision Android réversible dans les paramètres système."
            Step.CALL_LOG_PERMISSION ->
                "L’accès au journal reste local et dépend simultanément du rôle Téléphone et de l’autorisation Android."
            Step.SMS_PERMISSIONS, Step.MMS_PERMISSIONS ->
                "Aucun message n’est transmis à un service distant par le seul fait d’accorder ces autorisations."
            Step.NOTIFICATION_CHANNELS ->
                "Le contenu sensible des notifications reste gouverné par les préférences Sentinel et les réglages système."
            Step.COMPLETE ->
                "Cet état décrit des prérequis logiciels, pas une certification physique de toutes les fonctions."
        }

        fun stepLabel(step: Step): String = when (step) {
            Step.CORE_PERMISSIONS -> "Autoriser les fonctions essentielles"
            Step.DIALER_ROLE -> "Définir Sentinel comme application Téléphone"
            Step.CALL_SCREENING_ROLE -> "Activer l’identification et le filtrage des appels"
            Step.CALL_LOG_PERMISSION -> "Autoriser l’historique des appels"
            Step.SMS_ROLE -> "Définir Sentinel comme application SMS"
            Step.SMS_PERMISSIONS -> "Autoriser l’envoi et la réception des SMS"
            Step.MMS_PERMISSIONS -> "Autoriser la réception des MMS"
            Step.NOTIFICATION_CHANNELS -> "Activer les notifications et le plein écran des appels"
            Step.COMPLETE -> "Prérequis logiciels prêts"
        }
    }
}
