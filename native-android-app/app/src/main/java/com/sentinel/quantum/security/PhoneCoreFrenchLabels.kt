package com.sentinel.quantum.security

/**
 * French presentation layer for Phone Core internal codes.
 *
 * Internal protocol/rule tokens remain stable in English for storage and tests. User-facing
 * surfaces must use these labels instead of exposing raw enum/code values.
 */
object PhoneCoreFrenchLabels {
    fun action(raw: String): String = when (raw.trim().uppercase()) {
        "ALLOW" -> "Autorisé"
        "BLOCK" -> "Bloqué"
        "SILENCE" -> "Mis en sourdine"
        "WARN" -> "Avertissement"
        "FLAG_SUSPICIOUS" -> "Suspect à vérifier"
        "REQUIRE_CONFIRMATION" -> "Confirmation requise"
        "QUARANTINE" -> "Mis en quarantaine"
        "UNKNOWN", "" -> "Indéterminé"
        else -> "État non traduit"
    }

    fun source(raw: String): String = when (raw.trim().uppercase()) {
        "NONE" -> "Aucune règle spécifique"
        "USER" -> "Règle définie par l’utilisateur"
        "SIGNED_REPUTATION" -> "Réputation signée Sentinel"
        "LOCAL" -> "Données locales"
        "NETWORK" -> "Réseau opérateur"
        "UNKNOWN", "" -> "Source indéterminée"
        else -> "Source technique non traduite"
    }

    fun reason(raw: String): String = when (raw.trim().uppercase()) {
        "NO_MATCHING_RULE" -> "Aucune règle de blocage correspondante"
        "INVALID_OR_UNAVAILABLE_NUMBER" -> "Numéro indisponible ou format non exploitable"
        "USER_EXACT_BLOCK" -> "Numéro placé dans la liste de blocage"
        "USER_PREFIX_BLOCK" -> "Préfixe placé dans la liste de blocage"
        "SIGNED_REPUTATION_PREFIX" -> "Préfixe signalé par la réputation signée Sentinel"
        "NONE", "UNKNOWN", "" -> "Motif indéterminé"
        else -> "Motif technique non traduit"
    }

    fun diagnosticState(state: PhoneCoreDiagnostics.State): String = when (state) {
        PhoneCoreDiagnostics.State.READY -> "PRÊT"
        PhoneCoreDiagnostics.State.LIMITED -> "LIMITÉ"
        PhoneCoreDiagnostics.State.LOCKED -> "BLOQUÉ"
    }

    fun smsState(state: SmsActivationDiagnostics.State): String = when (state) {
        SmsActivationDiagnostics.State.READY -> "PRÊT"
        SmsActivationDiagnostics.State.LIMITED -> "LIMITÉ"
        SmsActivationDiagnostics.State.LOCKED -> "BLOQUÉ"
    }

    fun capability(raw: String): String = when (raw.trim().uppercase()) {
        "DIALER" -> "Téléphone"
        "CALL_SCREENING" -> "Filtrage des appels"
        "CONTACTS" -> "Contacts"
        "CALL_HISTORY" -> "Historique des appels"
        "SMS_SEND" -> "Envoi de SMS"
        "SMS_CONVERSATIONS" -> "Conversations SMS"
        "NOTIFICATIONS" -> "Notifications"
        "MMS_ATTACHMENTS" -> "MMS et pièces jointes"
        "WIFI_SCAN" -> "Scanner Wi-Fi"
        "PHYSICAL_DEVICE" -> "Appareil physique"
        else -> "Fonction Phone Core"
    }

    fun communityIntelligence(raw: String): String = when (raw.trim().lowercase()) {
        "enabled" -> "Disponible"
        "disabled" -> "Désactivée"
        "available" -> "Disponible"
        "unavailable" -> "Indisponible"
        "unknown", "" -> "Indéterminée"
        else -> "État non traduit"
    }

    fun reputationFlag(raw: String): String = when (raw.trim().uppercase()) {
        "INTERNATIONAL" -> "Appel international"
        "WANGIRI" -> "Risque d’appel très court"
        "SPOOFING" -> "Risque d’usurpation du numéro"
        "PREMIUM_RATE" -> "Numéro potentiellement surtaxé"
        "ROBOCALL" -> "Appel automatisé"
        "COMMUNITY_REPORTS" -> "Signalements communautaires"
        "VERIFICATION_FAILED" -> "Échec de vérification réseau"
        else -> "Signal technique à vérifier"
    }
}
