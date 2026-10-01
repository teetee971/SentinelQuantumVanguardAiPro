package com.sentinel.quantum.security

/** Missing Telecom controls are not evidence that no system call exists. */
internal object InCallPresencePolicy {
    enum class MissingSession { CONNECTING, CALL_UNAVAILABLE, ENDED, IDLE, UNKNOWN }
    fun resolve(telecomInCall: Boolean?, hadSession: Boolean, awaitingInitialSession: Boolean): MissingSession =
        when {
            telecomInCall == true -> MissingSession.CALL_UNAVAILABLE
            awaitingInitialSession && !hadSession -> MissingSession.CONNECTING
            telecomInCall == false && hadSession -> MissingSession.ENDED
            telecomInCall == false -> MissingSession.IDLE
            else -> MissingSession.UNKNOWN
        }

    fun title(state: MissingSession): String = when (state) {
        MissingSession.CONNECTING -> "Connexion à l’appel…"
        MissingSession.CALL_UNAVAILABLE -> "Appel en cours · liaison indisponible"
        MissingSession.ENDED -> "Appel terminé"
        MissingSession.IDLE -> "Aucun appel détecté"
        MissingSession.UNKNOWN -> "État de l’appel indisponible"
    }
}
