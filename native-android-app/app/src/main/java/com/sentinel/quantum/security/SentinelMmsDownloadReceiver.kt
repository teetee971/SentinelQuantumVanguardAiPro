package com.sentinel.quantum.security

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.concurrent.Executors

/**
 * Handles the explicit callback from Android's MMS download transport.
 *
 * The callback identity and BroadcastReceiver result code are captured synchronously. File reads,
 * decode/quarantine work, durable private persistence, timeline persistence and notifications are
 * then serialized on a private worker under goAsync(). The SMS-role boundary is revalidated on that
 * worker immediately before the downloaded PDU is touched.
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
        deliveredResultCode: Int
    ) {
        if (
            !TOKEN.matches(token) ||
            fileName != "$token.pdu" ||
            !MmsDownloadCoordinator.isValidStagedFileName(fileName)
        ) return
        if (subscriptionId == android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID) return

        val canonicalCache = runCatching { context.cacheDir.canonicalFile }.getOrNull() ?: return
        val directory = java.io.File(canonicalCache, MmsDownloadCoordinator.DOWNLOAD_DIRECTORY)
        val canonicalDirectory = runCatching { directory.canonicalFile }.getOrNull() ?: return
        if (canonicalDirectory.parentFile != canonicalCache) return

        val target = runCatching { java.io.File(canonicalDirectory, fileName).canonicalFile }.getOrNull() ?: return
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
        val persistence = IncomingMmsPrivateStore.persist(context.filesDir, data)
        if (persistence.state == IncomingMmsPrivateStore.State.FAILED) {
            LocalLogger(context).log(
                LocalLogger.LogLevel.SECURITY,
                "MmsDownload",
                "Persistance MMS privée refusée fail-closed"
            )
            return
        }

        val replay = persistence.state == IncomingMmsPrivateStore.State.EXISTING
        if (replay) {
            LocalLogger(context).log(
                LocalLogger.LogLevel.INFO,
                "MmsDownload",
                "Replay MMS reconnu par identité SHA-256; aucune copie privée ni chronologie dupliquée"
            )
        } else {
            LocalLogger(context).log(
                LocalLogger.LogLevel.SECURITY,
                "DefaultSms",
                "MMS téléchargé conservé localement; taille=${data.size}; preview=" +
                    if (safePreview is MmsDecodePipeline.Result.Accepted) "SAFE" else "QUARANTINED"
            )
            runCatching {
                PhonePrivateTimelineStore(context).append(
                    PhonePrivateTimeline.Event(
                        kind = PhonePrivateTimeline.Kind.MMS,
                        timestampMs = System.currentTimeMillis(),
                        direction = "INCOMING",
                        signal = if (safePreview is MmsDecodePipeline.Result.Accepted) {
                            "MMS_DOWNLOAD_SAFE_PREVIEW_READY"
                        } else {
                            "MMS_DOWNLOAD_QUARANTINED"
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
        }

        // The notification id is content-stable. Replays update the same notification instead of
        // creating duplicates, while still recovering the user-visible signal after process death.
        SmsNotificationHelper.notifyMessage(
            context,
            title = "MMS reçu",
            preview = when (safePreview) {
                is MmsDecodePipeline.Result.Accepted ->
                    "Téléchargé par Android · ${safePreview.parts.size} partie(s) validée(s) pour aperçu sécurisé."
                is MmsDecodePipeline.Result.Rejected ->
                    "Téléchargé puis conservé en quarantaine locale."
            },
            notificationId = persistence.digestHex?.take(16)?.hashCode() ?: fileName.hashCode()
        )
    }

    private companion object {
        val WORKER = Executors.newSingleThreadExecutor { task ->
            Thread(task, "sentinel-mms-download").apply { isDaemon = true }
        }
        val TOKEN = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    }
}
