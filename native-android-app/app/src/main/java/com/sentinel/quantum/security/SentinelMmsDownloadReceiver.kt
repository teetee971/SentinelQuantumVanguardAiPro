package com.sentinel.quantum.security

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.io.File
import java.util.concurrent.Executors

/**
 * Handles the explicit callback from Android's MMS download transport.
 *
 * The callback identity and BroadcastReceiver result code are captured synchronously. File reads,
 * decode/quarantine work, durable private persistence, provider projection and notifications are
 * serialized on a private worker under goAsync(). The SMS-role boundary is revalidated immediately
 * before downloaded bytes are touched.
 */
class SentinelMmsDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MmsDownloadCoordinator.ACTION_DOWNLOAD_COMPLETE) return

        val callbackUri = intent.data ?: return
        if (callbackUri.scheme != "sentinel-mms-download" || callbackUri.host != "result") return
        val token = callbackUri.pathSegments.singleOrNull()
            ?.takeIf { TOKEN.matches(it) }
            ?: return
        val fileName = intent.getStringExtra(MmsDownloadCoordinator.EXTRA_FILE_NAME)
            ?.takeIf {
                it == "$token.pdu" && MmsDownloadCoordinator.isValidStagedFileName(it)
            }
            ?: return
        val subscriptionId = intent.getIntExtra(
            MmsDownloadCoordinator.EXTRA_SUBSCRIPTION_ID,
            android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID
        )
        if (subscriptionId == android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID) return

        val transactionId = intent.getStringExtra(MmsDownloadCoordinator.EXTRA_TRANSACTION_ID)
            ?.takeIf(IncomingMmsProviderJournal::validTransactionId)
        val verifiedSender = intent.getStringExtra(MmsDownloadCoordinator.EXTRA_VERIFIED_SENDER)
            ?.takeIf(::isVerifiedSender)
        val senderUnavailableReason = intent
            .getStringExtra(MmsDownloadCoordinator.EXTRA_SENDER_UNAVAILABLE_REASON)
            ?.take(MAX_REASON_CHARS)

        // BroadcastReceiver.resultCode is callback-scoped state. Capture it before onReceive exits;
        // the worker must never read resultCode after the broadcast callback has returned.
        val deliveredResultCode = resultCode
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val submitted = runCatching {
            WORKER.execute {
                try {
                    processDownload(
                        context = appContext,
                        token = token,
                        fileName = fileName,
                        subscriptionId = subscriptionId,
                        transactionId = transactionId,
                        verifiedSender = verifiedSender,
                        senderUnavailableReason = senderUnavailableReason,
                        deliveredResultCode = deliveredResultCode
                    )
                } catch (_: Exception) {
                    LocalLogger(appContext).log(
                        LocalLogger.LogLevel.WARNING,
                        "MmsDownload",
                        "Échec inattendu du traitement du callback MMS"
                    )
                } finally {
                    pendingResult.finish()
                }
            }
        }.isSuccess

        if (!submitted) {
            LocalLogger(appContext).log(
                LocalLogger.LogLevel.WARNING,
                "MmsDownload",
                "Callback MMS non planifié : worker indisponible"
            )
            pendingResult.finish()
        }
    }

    private fun processDownload(
        context: Context,
        token: String,
        fileName: String,
        subscriptionId: Int,
        transactionId: String?,
        verifiedSender: String?,
        senderUnavailableReason: String?,
        deliveredResultCode: Int
    ) {
        if (
            !TOKEN.matches(token) ||
            fileName != "$token.pdu" ||
            !MmsDownloadCoordinator.isValidStagedFileName(fileName)
        ) return
        if (subscriptionId == android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID) return

        val canonicalCache = runCatching { context.cacheDir.canonicalFile }.getOrNull() ?: return
        val directory = File(canonicalCache, MmsDownloadCoordinator.DOWNLOAD_DIRECTORY)
        val canonicalDirectory = runCatching { directory.canonicalFile }.getOrNull() ?: return
        if (canonicalDirectory.parentFile != canonicalCache) return

        val target = runCatching { File(canonicalDirectory, fileName).canonicalFile }.getOrNull() ?: return
        if (target.parentFile != canonicalDirectory || !target.isFile) return

        if (
            context.readSmsRoleStateFailClosed() !=
                SmsActivationDiagnostics.SmsRoleState.HELD
        ) {
            MmsDownloadCoordinator.delete(context, fileName)
            LocalLogger(context).log(
                LocalLogger.LogLevel.WARNING,
                "MmsDownload",
                "Callback MMS ignoré : Sentinel n’est plus l’application SMS par défaut"
            )
            return
        }

        if (deliveredResultCode != Activity.RESULT_OK) {
            MmsDownloadCoordinator.delete(context, fileName)
            LocalLogger(context).log(
                LocalLogger.LogLevel.WARNING,
                "DefaultSms",
                "Téléchargement MMS Android échoué; code=$deliveredResultCode"
            )
            SmsNotificationHelper.notifyMessage(
                context,
                title = "MMS non téléchargé",
                preview = "Android n’a pas pu récupérer le MMS sur le réseau opérateur.",
                notificationId = fileName.hashCode()
            )
            return
        }

        val size = target.length()
        if (size !in 1..MmsDownloadCoordinator.MAX_DOWNLOADED_PDU_BYTES) {
            MmsDownloadCoordinator.delete(context, fileName)
            LocalLogger(context).log(
                LocalLogger.LogLevel.SECURITY,
                "DefaultSms",
                "MMS téléchargé rejeté pour taille hors limite"
            )
            return
        }

        val data = runCatching { target.readBytes() }.getOrNull()
        val temporaryDeleted = MmsDownloadCoordinator.delete(context, fileName)
        if (!temporaryDeleted) {
            LocalLogger(context).log(
                LocalLogger.LogLevel.WARNING,
                "MmsDownload",
                "Échec de suppression du PDU MMS temporaire; le nettoyage durable reste planifié"
            )
        }
        if (data == null || data.isEmpty()) return

        val safePreview = MmsDecodePipeline.decodeAndValidate(data, SentinelMmsPduDecoder)
        val privateStore = IncomingMmsPrivateStore.store(context, data)
        if (privateStore !is IncomingMmsPrivateStore.Result.Stored) {
            LocalLogger(context).log(
                LocalLogger.LogLevel.WARNING,
                "MmsDownload",
                "MMS téléchargé non publié : stockage privé durable indisponible"
            )
            return
        }

        val providerStatus = projectProviderIfEligible(
            context = context,
            fingerprint = privateStore.fingerprint,
            transactionId = transactionId,
            verifiedSender = verifiedSender,
            senderUnavailableReason = senderUnavailableReason,
            subscriptionId = subscriptionId,
            rawPduSize = data.size.toLong(),
            safePreview = safePreview
        )

        runCatching {
            PhonePrivateTimelineStore(context).append(
                PhonePrivateTimeline.Event(
                    kind = PhonePrivateTimeline.Kind.MMS,
                    timestampMs = System.currentTimeMillis(),
                    direction = "INCOMING",
                    signal = when {
                        providerStatus == ProviderStatus.READY &&
                            safePreview is MmsDecodePipeline.Result.Accepted ->
                            "MMS_DOWNLOAD_PROVIDER_SAFE_READY"
                        providerStatus == ProviderStatus.READY ->
                            "MMS_DOWNLOAD_PROVIDER_QUARANTINED"
                        safePreview is MmsDecodePipeline.Result.Accepted ->
                            "MMS_DOWNLOAD_SAFE_PREVIEW_READY"
                        else -> "MMS_DOWNLOAD_QUARANTINED"
                    }
                )
            )
        }.onFailure {
            LocalLogger(context).log(
                LocalLogger.LogLevel.WARNING,
                "MmsDownload",
                "Chronologie privée indisponible; le traitement MMS téléchargé continue"
            )
        }

        SmsNotificationHelper.notifyMessage(
            context,
            title = "MMS reçu",
            preview = when (safePreview) {
                is MmsDecodePipeline.Result.Accepted ->
                    "Téléchargé par Android · ${safePreview.parts.size} partie(s) validée(s) pour aperçu sécurisé."
                is MmsDecodePipeline.Result.Rejected ->
                    "Téléchargé puis conservé en quarantaine locale."
            },
            notificationId = privateStore.fingerprint.hashCode()
        )
    }

    private fun projectProviderIfEligible(
        context: Context,
        fingerprint: String,
        transactionId: String?,
        verifiedSender: String?,
        senderUnavailableReason: String?,
        subscriptionId: Int,
        rawPduSize: Long,
        safePreview: MmsDecodePipeline.Result
    ): ProviderStatus {
        if (transactionId == null) {
            LocalLogger(context).log(
                LocalLogger.LogLevel.WARNING,
                "MmsProvider",
                "Projection MMS Inbox ignorée : Transaction-ID absent ou invalide"
            )
            return ProviderStatus.PRIVATE_ONLY
        }
        if (verifiedSender == null) {
            LocalLogger(context).log(
                LocalLogger.LogLevel.WARNING,
                "MmsProvider",
                "Projection MMS Inbox ignorée : identité expéditeur non vérifiée" +
                    senderUnavailableReason?.let { "; raison=$it" }.orEmpty()
            )
            return ProviderStatus.PRIVATE_ONLY
        }

        val store = IncomingMmsConversationStore(context)
        return when (
            val result = store.persistDownloadedInbox(
                fingerprint = fingerprint,
                transactionId = transactionId,
                sender = verifiedSender,
                subscriptionId = subscriptionId,
                rawPduSize = rawPduSize,
                preview = safePreview
            )
        ) {
            is IncomingMmsConversationStore.PersistResult.Ready,
            is IncomingMmsConversationStore.PersistResult.Duplicate -> ProviderStatus.READY
            is IncomingMmsConversationStore.PersistResult.Rejected -> {
                LocalLogger(context).log(
                    if (result.cleanupConfirmed) LocalLogger.LogLevel.WARNING
                    else LocalLogger.LogLevel.SECURITY,
                    "MmsProvider",
                    "Projection MMS Inbox différée; raison=${result.reason}; " +
                        "cleanupConfirmed=${result.cleanupConfirmed}"
                )
                if (!result.cleanupConfirmed) runCatching { store.repairJournal() }
                ProviderStatus.DEFERRED
            }
        }
    }

    private enum class ProviderStatus { READY, PRIVATE_ONLY, DEFERRED }

    private companion object {
        val WORKER = Executors.newSingleThreadExecutor { task ->
            Thread(task, "sentinel-mms-download").apply { isDaemon = true }
        }
        val TOKEN = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        const val MAX_REASON_CHARS = 96

        fun isVerifiedSender(value: String): Boolean {
            val normalized = CallRuleEngine.normalizeNumber(value) ?: return false
            return normalized == value && normalized.startsWith('+')
        }
    }
}
