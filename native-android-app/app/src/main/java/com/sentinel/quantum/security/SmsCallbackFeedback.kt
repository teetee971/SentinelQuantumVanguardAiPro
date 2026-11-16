package com.sentinel.quantum.security

/** Radio acknowledgements never establish successful local storage. */
internal object SmsCallbackFeedback {
    fun message(state: SmsCallbackProgress.State, providerPersistenceFailed: Boolean): String = when {
        providerPersistenceFailed ->
            "État réseau reçu, mais l’enregistrement local du statut a échoué. Vérifiez la conversation avant toute nouvelle tentative."
        state.sentFailed.isNotEmpty() ->
            "Échec d’envoi Android sur ${state.sentFailed.size}/${state.partCount} partie(s)."
        state.deliveryFailed.isNotEmpty() ->
            "Échec de livraison sur ${state.deliveryFailed.size}/${state.partCount} partie(s)."
        state.sentOk.size == state.partCount && state.deliveredOk.size == state.partCount ->
            "Accusé de livraison reçu pour toutes les parties."
        state.sentOk.size == state.partCount ->
            "Android signale l’envoi réussi de toutes les parties ; livraison à confirmer."
        state.deliveredOk.isNotEmpty() ->
            "Retour de livraison reçu pour ${state.deliveredOk.size}/${state.partCount} partie(s) ; envoi complet à confirmer."
        else ->
            "Envoi confirmé par Android pour ${state.sentOk.size}/${state.partCount} partie(s)."
    }
}

