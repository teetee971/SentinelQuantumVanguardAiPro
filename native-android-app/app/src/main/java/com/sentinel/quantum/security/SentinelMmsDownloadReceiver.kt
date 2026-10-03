package com.sentinel.quantum.security

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Handles the explicit callback from Android's MMS download transport.
 *
 * The callback identity and BroadcastReceiver result code are captured synchronously. File reads,
 * decode/quarantine work, fsync, timeline persistence and notifications are then serialized on a
 * private worker under goAsync(). The SMS-role boundary is revalidated on that worker immediately
 * before the downloaded PDU is touched.
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
        if (!persistPrivatePdu(context, data, safePreview)) return

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

        SmsNotificationHelper.notifyMessage(
            context,
            title = "MMS reçu",
            preview = when (safePreview) {
                is MmsDecodePipeline.Result.Accepted ->
                    "Téléchargé par Android · ${safePreview.parts.size} partie(s) validée(s) pour aperçu sécurisé."
                is MmsDecodePipeline.Result.Rejected ->
                    "Téléchargé puis conservé en quarantaine locale."
            },
            notificationId = fileName.hashCode()
        )
    }

    private fun persistPrivatePdu(
        context: Context,
        data: ByteArray,
        safePreview: MmsDecodePipeline.Result
    ): Boolean {
        val directory = File(context.filesDir, "mms-inbox")
        if (!directory.exists() && !directory.mkdirs()) return false
        val canonicalRoot = runCatching { context.filesDir.canonicalFile }.getOrNull() ?: return false
        val canonicalDirectory = runCatching { directory.canonicalFile }.getOrNull() ?: return false
        if (canonicalDirectory.parentFile != canonicalRoot || !canonicalDirectory.isDirectory) return false

        val files = canonicalDirectory.listFiles()
            ?.filter { it.isFile && it.extension == "pdu" }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        files.drop(MAX_STORED_MMS - 1).forEach { runCatching { it.delete() } }

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(data)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            .take(24)
        val target = File(canonicalDirectory, "${System.currentTimeMillis()}-$digest.pdu")
        val canonicalTarget = runCatching { target.canonicalFile }.getOrNull() ?: return false
        if (canonicalTarget.parentFile != canonicalDirectory || canonicalTarget.exists()) return false

        return runCatching {
            FileOutputStream(canonicalTarget).use { stream ->
                stream.write(data)
                stream.fd.sync()
            }
            LocalLogger(context).log(
                LocalLogger.LogLevel.SECURITY,
                "DefaultSms",
                "MMS téléchargé conservé localement; taille=${data.size}; preview=" +
                    if (safePreview is MmsDecodePipeline.Result.Accepted) "SAFE" else "QUARANTINED"
            )
            true
        }.getOrElse {
            runCatching { canonicalTarget.delete() }
            false
        }
    }

    private companion object {
        val WORKER = Executors.newSingleThreadExecutor { task ->
            Thread(task, "sentinel-mms-download").apply { isDaemon = true }
        }
        val TOKEN = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        const val MAX_STORED_MMS = 50
    }
}
