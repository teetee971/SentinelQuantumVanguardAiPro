package com.sentinel.quantum.security

/** Customer wording for Android MMS transport callbacks; recipient delivery is never inferred. */
internal object MmsTransportFeedback {
    fun message(successful: Boolean, providerWriteSucceeded: Boolean): String = when {
        !providerWriteSucceeded ->
            "Résultat MMS reçu, mais l’enregistrement local n’est pas confirmé. Vérifiez la conversation avant tout nouvel envoi."
        successful ->
            "Android confirme la transmission MMS ; la réception par le destinataire n’est pas confirmée."
        else ->
            "Android signale l’échec de la transmission MMS. Aucun succès destinataire n’est déduit."
    }
}
