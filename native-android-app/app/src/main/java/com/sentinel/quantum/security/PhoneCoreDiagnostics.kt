package com.sentinel.quantum.security

/**
 * Pure diagnostic model for Phone Core.
 *
 * Software readiness and physical-device validation are intentionally separate:
 * a build can be 100% software-ready without claiming that carrier/device behavior was tested.
 */
object PhoneCoreDiagnostics {
    enum class State { READY, LIMITED, LOCKED }

    data class RuntimeFacts(
        val dialerRoleHeld: Boolean,
        val callScreeningRoleHeld: Boolean,
        val smsRoleHeld: Boolean,
        val callPermissionGranted: Boolean,
        val readPhoneStatePermissionGranted: Boolean = false,
        val callLineAvailable: Boolean = false,
        val sendSmsPermissionGranted: Boolean,
        val readSmsPermissionGranted: Boolean,
        val receiveSmsPermissionGranted: Boolean = false,
        val notificationsReady: Boolean = false,
        val contactsPermissionGranted: Boolean = false,
        val callLogPermissionGranted: Boolean = false,
        val activeSimVerified: Boolean = false,
        val receiveMmsPermissionGranted: Boolean = false,
        val receiveWapPushPermissionGranted: Boolean = false,
        val mmsSafePreviewValidated: Boolean,
        val physicalDeviceValidated: Boolean
    )

    data class Capability(val id: String, val state: State, val reason: String)

    data class Readiness(
        val capabilities: List<Capability>,
        val softwarePrerequisitesReady: Boolean,
        val physicalDeviceValidated: Boolean,
        val fullyValidated: Boolean
    )

    fun evaluate(f: RuntimeFacts): List<Capability> = readiness(f).capabilities

    fun readiness(f: RuntimeFacts): Readiness {
        val capabilities = listOf(
            Capability(
                "DIALER",
                if (
                    f.dialerRoleHeld &&
                    f.callPermissionGranted &&
                    f.readPhoneStatePermissionGranted &&
                    f.callLineAvailable
                ) State.READY else if (f.dialerRoleHeld) State.LIMITED else State.LOCKED,
                buildList {
                    if (!f.dialerRoleHeld) add("Rôle Téléphone non attribué")
                    if (!f.callPermissionGranted) add("Autorisation d’appel manquante")
                    if (!f.readPhoneStatePermissionGranted) add("Autorisation de détection des lignes manquante")
                    if (!f.callLineAvailable) add("Aucune ligne d’appel active vérifiée")
                }.ifEmpty { listOf("Téléphone et ligne d’appel prêts") }.joinToString(" · ")
            ),
            capability("CALL_SCREENING", f.callScreeningRoleHeld, f.callScreeningRoleHeld, "Rôle de filtrage des appels non attribué"),
            capability("CONTACTS", f.contactsPermissionGranted, f.contactsPermissionGranted, "Autorisation d’accès aux contacts non accordée"),
            capability("CALL_HISTORY", f.dialerRoleHeld && f.callLogPermissionGranted, f.dialerRoleHeld, "Rôle Téléphone ou autorisation d’historique manquant"),
            capability("SMS_SEND", f.smsRoleHeld && f.sendSmsPermissionGranted && f.activeSimVerified, f.smsRoleHeld, "Rôle SMS, autorisation d’envoi ou SIM active manquante"),
            capability("SMS_CONVERSATIONS", f.smsRoleHeld && f.readSmsPermissionGranted && f.receiveSmsPermissionGranted, f.smsRoleHeld, "Rôle SMS ou autorisation de lecture/réception manquante"),
            capability("NOTIFICATIONS", f.notificationsReady, f.notificationsReady, "Notifications appels/SMS non disponibles"),
            Capability(
                "MMS_ATTACHMENTS",
                if (
                    f.smsRoleHeld &&
                    f.receiveMmsPermissionGranted &&
                    f.receiveWapPushPermissionGranted &&
                    f.mmsSafePreviewValidated
                ) State.READY else State.LOCKED,
                buildList {
                    if (!f.smsRoleHeld) add("Rôle SMS non attribué")
                    if (!f.receiveMmsPermissionGranted) add("Autorisation de réception MMS manquante")
                    if (!f.receiveWapPushPermissionGranted) add("Autorisation de réception WAP Push manquante")
                    if (!f.mmsSafePreviewValidated) add("Décodage sécurisé non validé")
                }.ifEmpty { listOf("Réception et décodage MMS validés") }.joinToString(" · ")
            ),
            Capability(
                "PHYSICAL_DEVICE",
                if (f.physicalDeviceValidated) State.READY else State.LIMITED,
                if (f.physicalDeviceValidated) "Validation appareil observée" else "Validation sur appareil physique requise"
            )
        )
        /*
         * Software readiness contains only prerequisites the app/user can configure.
         * A currently active carrier line or SIM is an environment/physical-test condition,
         * not a software prerequisite. Keeping those separate prevents the UI from
         * contradicting the 8-step activation wizard on Wi-Fi-only/test devices.
         */
        val softwareReady =
            f.dialerRoleHeld &&
                f.callPermissionGranted &&
                f.readPhoneStatePermissionGranted &&
                f.callScreeningRoleHeld &&
                f.contactsPermissionGranted &&
                f.callLogPermissionGranted &&
                f.smsRoleHeld &&
                f.sendSmsPermissionGranted &&
                f.readSmsPermissionGranted &&
                f.receiveSmsPermissionGranted &&
                f.notificationsReady &&
                f.receiveMmsPermissionGranted &&
                f.receiveWapPushPermissionGranted &&
                f.mmsSafePreviewValidated
        val operationalEnvironmentReady =
            f.callLineAvailable &&
                f.activeSimVerified

        return Readiness(
            capabilities = capabilities,
            softwarePrerequisitesReady = softwareReady,
            physicalDeviceValidated = f.physicalDeviceValidated,
            fullyValidated =
                softwareReady &&
                    operationalEnvironmentReady &&
                    f.physicalDeviceValidated
        )
    }

    private fun capability(id: String, ready: Boolean, partiallyAvailable: Boolean, missing: String): Capability =
        when {
            ready -> Capability(id, State.READY, "Prérequis système observés")
            partiallyAvailable -> Capability(id, State.LIMITED, missing)
            else -> Capability(id, State.LOCKED, missing)
        }
}
