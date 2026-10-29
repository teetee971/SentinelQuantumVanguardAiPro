package com.sentinel.quantum.voice

/** User-facing transport truth, without displaying arbitrary exceptions or ephemeral credentials. */
internal object VoiceSessionFailurePresentation {
    fun message(failure: Throwable): String = when (failure.message) {
        "VOICE_SESSION_HTTP_503" -> "Service PTT indisponible (HTTP 503). Aucune connexion PTT confirmée. Réessayez plus tard."
        "VOICE_SESSION_HTTP_401" -> "Session PTT refusée : authentification à renouveler."
        "VOICE_SESSION_HTTP_403" -> "Accès PTT non autorisé pour ce compte."
        else -> "La connexion PTT n’a pas été confirmée. Vérifiez le réseau et réessayez."
    }
}
