package com.sentinel.quantum.security

import android.app.Activity
import android.telephony.SmsManager

/**
 * Converts Android MMS transport callbacks into stable, factual Phone Core states.
 *
 * These outcomes describe the Android/carrier transport callback only. A successful callback must
 * never be presented as proof that the recipient device displayed or read the MMS.
 */
object MmsSendResultClassifier {
    data class Outcome(
        val success: Boolean,
        val signal: String,
        val title: String,
        val preview: String,
        val diagnostic: String
    )

    fun classify(resultCode: Int, httpStatus: Int? = null): Outcome = when (resultCode) {
        Activity.RESULT_OK -> Outcome(
            success = true,
            signal = PhoneCorePhysicalValidation.SIGNAL_MMS_SENT_OK,
            title = "MMS transmis",
            preview = "Android a confirmé la transmission par la pile MMS. La réception par le destinataire n'est pas confirmée.",
            diagnostic = "transport_success"
        )
        SmsManager.MMS_ERROR_UNSPECIFIED -> failure(
            "MMS_SEND_UNSPECIFIED_ERROR",
            "Échec MMS non précisé",
            "Android a signalé un échec MMS sans cause plus précise.",
            "unspecified"
        )
        SmsManager.MMS_ERROR_INVALID_APN -> failure(
            "MMS_SEND_INVALID_APN",
            "Configuration MMS invalide",
            "L'APN MMS n'a pas pu être utilisé.",
            "invalid_apn"
        )
        SmsManager.MMS_ERROR_UNABLE_CONNECT_MMS -> failure(
            "MMS_SEND_CONNECTION_FAILED",
            "Connexion MMS impossible",
            "La connexion au service MMS de l'opérateur n'a pas pu être établie.",
            "unable_connect_mms"
        )
        SmsManager.MMS_ERROR_HTTP_FAILURE -> {
            val suffix = httpStatus?.takeIf { it in 100..599 }?.let { ":HTTP_$it" }.orEmpty()
            failure(
                "MMS_SEND_HTTP_FAILURE$suffix",
                "Échec HTTP MMS",
                httpStatus?.takeIf { it in 100..599 }?.let {
                    "Le transport MMS a échoué pendant l'échange HTTP (statut $it)."
                } ?: "Le transport MMS a échoué pendant l'échange HTTP.",
                "http_failure${httpStatus?.takeIf { it in 100..599 }?.let { ":$it" }.orEmpty()}"
            )
        }
        SmsManager.MMS_ERROR_IO_ERROR -> failure(
            "MMS_SEND_IO_ERROR",
            "Lecture MMS impossible",
            "Android n'a pas pu lire correctement les données MMS préparées pour l'envoi.",
            "io_error"
        )
        SmsManager.MMS_ERROR_RETRY -> failure(
            "MMS_SEND_RETRY_FAILED",
            "Nouvelle tentative MMS échouée",
            "Android a signalé l'échec d'une tentative de renvoi MMS.",
            "retry_failed"
        )
        SmsManager.MMS_ERROR_CONFIGURATION_ERROR -> failure(
            "MMS_SEND_CONFIGURATION_ERROR",
            "Configuration opérateur indisponible",
            "Les paramètres MMS dépendant de l'opérateur n'ont pas pu être chargés.",
            "configuration_error"
        )
        SmsManager.MMS_ERROR_NO_DATA_NETWORK -> failure(
            "MMS_SEND_NO_DATA_NETWORK",
            "Réseau de données indisponible",
            "Aucun réseau de données utilisable pour le MMS n'est disponible.",
            "no_data_network"
        )
        SmsManager.MMS_ERROR_INVALID_SUBSCRIPTION_ID -> failure(
            "MMS_SEND_INVALID_SUBSCRIPTION",
            "Abonnement MMS invalide",
            "L'abonnement sélectionné pour cet envoi MMS n'est pas valide.",
            "invalid_subscription"
        )
        SmsManager.MMS_ERROR_INACTIVE_SUBSCRIPTION -> failure(
            "MMS_SEND_INACTIVE_SUBSCRIPTION",
            "Abonnement MMS inactif",
            "L'abonnement sélectionné pour cet envoi MMS n'est pas actif.",
            "inactive_subscription"
        )
        SmsManager.MMS_ERROR_DATA_DISABLED -> failure(
            "MMS_SEND_DATA_DISABLED",
            "Données MMS désactivées",
            "Les données nécessaires à l'APN MMS sont désactivées.",
            "data_disabled"
        )
        SmsManager.MMS_ERROR_MMS_DISABLED_BY_CARRIER -> failure(
            "MMS_SEND_DISABLED_BY_CARRIER",
            "MMS désactivé par l'opérateur",
            "L'opérateur indique que le service MMS est désactivé pour cet envoi.",
            "disabled_by_carrier"
        )
        else -> failure(
            signal = "MMS_SEND_UNKNOWN_ERROR:$resultCode",
            title = "Échec MMS non reconnu",
            preview = "Android a renvoyé un code MMS non reconnu ($resultCode). Aucun succès n'est déduit.",
            diagnostic = "unknown:$resultCode"
        )
    }

    private fun failure(
        signal: String,
        title: String,
        preview: String,
        diagnostic: String
    ) = Outcome(
        success = false,
        signal = signal,
        title = title,
        preview = preview,
        diagnostic = diagnostic
    )
}
