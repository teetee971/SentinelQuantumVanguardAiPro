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
 * quarantine validation, durable private persistence, canonical provider projection, timeline
 * persistence and notifications are serialized on a private worker under goAsync(). The SMS-role
 * boundary is revalidated immediately before downloaded bytes and again by provider projection.
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
        if (!android.telephony.SubscriptionManager.isValidSubscriptionId(subscriptionId)) return

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
        if (!android.telephony.SubscriptionManager.isValidSubscriptionId(subscriptionId)) return

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

        // Private persistence is the recovery anchor. Canonical provider state is never attempted if
        // the durable local identity itself could not be established.
        val persistence = IncomingMmsPrivateStore.persist(context.filesDir, data)
        val digest = persistence.digestHex
        if (persistence.state == IncomingMmsPrivateStore.State.FAILED || digest == null) {
            LocalLogger(context).log(
                LocalLogger.LogLevel.SECURITY,
                "MmsDownload",
                "Persistance MMS privée refusée fail-closed"
            )
            return
        }

        val prepared = IncomingMmsProjectionPipeline.prepare(data, digest, subscriptionId)
        val safePreview: MmsDecodePipeline.Result = when (prepared) {
            is IncomingMmsProjectionPipeline.Result.Ready ->
                MmsDecodePipeline.Result.Accepted(prepared.safeParts)
            is IncomingMmsProjectionPipeline.Result.Quarantined ->
                MmsDecodePipeline.decodeAndValidate(data, SentinelMmsPduDecoder)
        }

        val providerResult = when (prepared) {
            is IncomingMmsProjectionPipeline.Result.Ready -> {
                val store = IncomingMmsConversationStore(context)
                runCatching { store.repairJournal() }
                store.project(prepared.plan)
            }
            is IncomingMmsProjectionPipeline.Result.Quarantined -> null
        }
        if (providerResult is IncomingMmsConversationStore.ProjectResult.Rejected) {
            LocalLogger(context).log(
                LocalLogger.LogLevel.WARNING,
                "MmsProvider",
                "MMS entrant conservé privé mais projection provider refusée; raison=${providerResult.reason}"
            )
        }

        val replay = persistence.state == IncomingMmsPrivateStore.State.EXISTING
        if (replay) {
            LocalLogger(context).log(
                LocalLogger.LogLevel.INFO,
                "MmsDownload",
                "Replay MMS reconnu par identité SHA-256; aucune copie privée ni chronologie dupliquée"
            )
        } else {
            val providerReady = providerResult is IncomingMmsConversationStore.ProjectResult.Ready
            LocalLogger(context).log(
                LocalLogger.LogLevel.SECURITY,
                "DefaultSms",
                "MMS téléchargé conservé localement; taille=${data.size}; preview=" +
                    if (safePreview is MmsDecodePipeline.Result.Accepted) "SAFE" else "QUARANTINED" +
                    "; provider=" + if (providerReady) "READY" else "PRIVATE_ONLY"
            )
            runCatching {
                PhonePrivateTimelineStore(context).append(
                    PhonePrivateTimeline.Event(
                        kind = PhonePrivateTimeline.Kind.MMS,
                        timestampMs = System.currentTimeMillis(),
                        direction = "INCOMING",
                        signal = when {
                            providerReady -> "MMS_DOWNLOAD_PROVIDER_READY"
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
        }

        val canonical = providerResult is IncomingMmsConversationStore.ProjectResult.Ready
        SmsNotificationHelper.notifyMessage(
            context,
            title = "MMS reçu",
            preview = when {
                canonical -> "MMS ajouté à la conversation Android."
                safePreview is MmsDecodePipeline.Result.Accepted ->
                    "Téléchargé et conservé localement · ${safePreview.parts.size} partie(s) validée(s)."
                else -> "Téléchargé puis conservé en quarantaine locale."
            },
            notificationId = digest.take(16).hashCode()
        )
    }

    private companion object {
        val WORKER = Executors.newSingleThreadExecutor { task ->
            Thread(task, "sentinel-mms-download").apply { isDaemon = true }
        }
        val TOKEN = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    }
}
