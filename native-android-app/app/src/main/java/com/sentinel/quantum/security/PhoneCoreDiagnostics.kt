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
        val sendSmsPermissionGranted: Boolean,
        val readSmsPermissionGranted: Boolean,
        val contactsPermissionGranted: Boolean = false,
        val callLogPermissionGranted: Boolean = false,
        val activeSimVerified: Boolean = false,
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
            capability("DIALER", f.dialerRoleHeld && f.callPermissionGranted, f.dialerRoleHeld, "Rôle Dialer ou permission d'appel manquant"),
            capability("CALL_SCREENING", f.callScreeningRoleHeld, f.callScreeningRoleHeld, "Rôle Call Screening non attribué"),
            capability("CONTACTS", f.contactsPermissionGranted, f.contactsPermissionGranted, "Permission Contacts non attribuée"),
            capability("CALL_HISTORY", f.dialerRoleHeld && f.callLogPermissionGranted, f.dialerRoleHeld, "Rôle Dialer ou permission historique manquant"),
            capability("SMS_SEND", f.smsRoleHeld && f.sendSmsPermissionGranted && f.activeSimVerified, f.smsRoleHeld, "Rôle SMS, permission d'envoi ou SIM active manquant"),
            capability("SMS_CONVERSATIONS", f.smsRoleHeld && f.readSmsPermissionGranted, f.smsRoleHeld, "Rôle SMS ou permission de lecture manquant"),
            Capability(
                "MMS_ATTACHMENTS",
                if (f.mmsSafePreviewValidated) State.READY else State.LOCKED,
                if (f.mmsSafePreviewValidated) "Décodage sécurisé validé" else "Décodage sécurisé non validé"
            ),
            Capability(
                "PHYSICAL_DEVICE",
                if (f.physicalDeviceValidated) State.READY else State.LIMITED,
                if (f.physicalDeviceValidated) "Validation appareil observée" else "Validation sur appareil physique requise"
            )
        )
        val softwareIds = setOf("DIALER", "CALL_SCREENING", "CONTACTS", "CALL_HISTORY", "SMS_SEND", "SMS_CONVERSATIONS", "MMS_ATTACHMENTS")
        val softwareReady = capabilities.filter { it.id in softwareIds }.all { it.state == State.READY }
        return Readiness(
            capabilities = capabilities,
            softwarePrerequisitesReady = softwareReady,
            physicalDeviceValidated = f.physicalDeviceValidated,
            fullyValidated = softwareReady && f.physicalDeviceValidated
        )
    }

    private fun capability(id: String, ready: Boolean, partiallyAvailable: Boolean, missing: String): Capability =
        when {
            ready -> Capability(id, State.READY, "Prérequis runtime observés")
            partiallyAvailable -> Capability(id, State.LIMITED, missing)
            else -> Capability(id, State.LOCKED, missing)
        }
}
