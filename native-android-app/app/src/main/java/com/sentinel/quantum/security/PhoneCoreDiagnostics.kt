package com.sentinel.quantum.security

/**
 * Pure diagnostic model for the Phone Core. A capability is READY only from observed runtime facts.
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
        val mmsSafePreviewValidated: Boolean,
        val physicalDeviceValidated: Boolean
    )

    data class Capability(val id: String, val state: State, val reason: String)

    fun evaluate(f: RuntimeFacts): List<Capability> = listOf(
        capability("DIALER", f.dialerRoleHeld && f.callPermissionGranted, f.dialerRoleHeld, "Rôle Dialer ou permission d'appel manquant"),
        capability("CALL_SCREENING", f.callScreeningRoleHeld, f.callScreeningRoleHeld, "Rôle Call Screening non attribué"),
        capability("SMS_SEND", f.smsRoleHeld && f.sendSmsPermissionGranted, f.smsRoleHeld, "Rôle SMS ou permission d'envoi manquant"),
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

    private fun capability(id: String, ready: Boolean, partiallyAvailable: Boolean, missing: String): Capability =
        when {
            ready -> Capability(id, State.READY, "Prérequis runtime observés")
            partiallyAvailable -> Capability(id, State.LIMITED, missing)
            else -> Capability(id, State.LOCKED, missing)
        }
}
