package com.sentinel.quantum.security

/**
 * Pure presentation model for the SMS activation surface.
 *
 * Keeping this mapping free of Android side effects makes READY/LIMITED/LOCKED rendering
 * deterministic. The UI may offer only actions explicitly represented by the diagnostic snapshot.
 */
object SmsActivationUiModel {
    enum class Action { REQUEST_SMS_ROLE, REQUEST_RUNTIME_PERMISSIONS, RETRY_SIM_LOOKUP }

    data class Model(
        val state: SmsActivationDiagnostics.State,
        val title: String,
        val detail: String,
        val actions: Set<Action>
    )

    fun from(snapshot: SmsActivationDiagnostics.Snapshot): Model {
        val blockers = snapshot.blockers
        val actions = linkedSetOf<Action>()
        if (SmsActivationDiagnostics.Blocker.SMS_ROLE_REQUIRED in blockers) {
            actions += Action.REQUEST_SMS_ROLE
        }
        if (
            SmsActivationDiagnostics.Blocker.SEND_SMS_PERMISSION_REQUIRED in blockers ||
            SmsActivationDiagnostics.Blocker.READ_PHONE_STATE_PERMISSION_REQUIRED in blockers
        ) {
            actions += Action.REQUEST_RUNTIME_PERMISSIONS
        }
        if (SmsActivationDiagnostics.Blocker.SUBSCRIPTION_LOOKUP_FAILED in blockers) {
            actions += Action.RETRY_SIM_LOOKUP
        }

        val detail = when (snapshot.state) {
            SmsActivationDiagnostics.State.READY ->
                "Sentinel est prêt à envoyer des SMS avec une SIM active vérifiée."
            SmsActivationDiagnostics.State.LIMITED -> when {
                SmsActivationDiagnostics.Blocker.READ_PHONE_STATE_PERMISSION_REQUIRED in blockers ->
                    "Accès à l’état téléphonique requis pour vérifier les SIM actives."
                SmsActivationDiagnostics.Blocker.SUBSCRIPTION_LOOKUP_FAILED in blockers ->
                    "La vérification des SIM actives a échoué. Réessayez avant l’envoi."
                SmsActivationDiagnostics.Blocker.NO_ACTIVE_SIM in blockers ->
                    "Aucune SIM active n’est détectée. L’envoi reste indisponible."
                else -> "Fonction SMS disponible de façon limitée."
            }
            SmsActivationDiagnostics.State.LOCKED -> when {
                SmsActivationDiagnostics.Blocker.SMS_ROLE_REQUIRED in blockers ->
                    "Choisissez Sentinel comme application SMS par défaut pour déverrouiller l’envoi."
                SmsActivationDiagnostics.Blocker.SEND_SMS_PERMISSION_REQUIRED in blockers ->
                    "Autorisez l’envoi SMS pour déverrouiller l’envoi."
                else -> "L’envoi SMS est verrouillé."
            }
        }

        return Model(
            state = snapshot.state,
            title = "SMS ${snapshot.state.name}",
            detail = detail,
            actions = actions
        )
    }
}
