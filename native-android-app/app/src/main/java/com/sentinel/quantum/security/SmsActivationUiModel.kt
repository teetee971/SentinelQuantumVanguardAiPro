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

        val detail = if (snapshot.state == SmsActivationDiagnostics.State.READY) {
            "Sentinel est prêt à envoyer des SMS avec une SIM active vérifiée."
        } else {
            blockers.map { blocker ->
                when (blocker) {
                    SmsActivationDiagnostics.Blocker.SMS_ROLE_REQUIRED ->
                        "• Application SMS par défaut : Sentinel n’est pas encore sélectionné."
                    SmsActivationDiagnostics.Blocker.SEND_SMS_PERMISSION_REQUIRED ->
                        "• Envoi SMS : autorisation Android requise."
                    SmsActivationDiagnostics.Blocker.READ_PHONE_STATE_PERMISSION_REQUIRED ->
                        "• Détection SIM : accès à l’état téléphonique requis."
                    SmsActivationDiagnostics.Blocker.NO_ACTIVE_SIM ->
                        "• SIM active : aucune ligne active détectée."
                    SmsActivationDiagnostics.Blocker.SUBSCRIPTION_LOOKUP_FAILED ->
                        "• Détection SIM : vérification Android échouée ; réessayez."
                }
            }.joinToString("\n").ifBlank {
                when (snapshot.state) {
                    SmsActivationDiagnostics.State.LIMITED -> "Fonction SMS disponible de façon limitée."
                    SmsActivationDiagnostics.State.LOCKED -> "L’envoi SMS est verrouillé."
                    SmsActivationDiagnostics.State.READY -> error("READY handled above")
                }
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
