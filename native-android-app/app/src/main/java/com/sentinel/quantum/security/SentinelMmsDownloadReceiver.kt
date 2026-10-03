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
        val persistence = persistPrivatePdu(context, data, safePreview)
        if (persistence == PrivatePduPersistence.FAILED) return
        val replay = persistence == PrivatePduPersistence.EXISTING
        if (replay) {
            LocalLogger(context).log(
                LocalLogger.LogLevel.INFO,
                "MmsDownload",
                "Replay MMS reconnu par identité SHA-256; aucune copie privée ni chronologie dupliquée"
            )
        } else {
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
            notificationId = IncomingMmsIdentity.sha256Hex(data)?.take(16)?.hashCode()
                ?: fileName.hashCode()
        )
    }

    private enum class PrivatePduPersistence {
        CREATED,
        EXISTING,
        FAILED
    }

    /**
     * Persist one immutable private PDU under its complete SHA-256 identity.
     *
     * A `.part` file is fsynced before rename so process death cannot leave a truncated file at the
     * stable `.pdu` identity. The worker is single-threaded, so one digest has at most one writer in
     * this process. Replayed callbacks return EXISTING and do not duplicate private storage.
     */
    private fun persistPrivatePdu(
        context: Context,
        data: ByteArray,
        safePreview: MmsDecodePipeline.Result
    ): PrivatePduPersistence {
        val digest = IncomingMmsIdentity.sha256Hex(data) ?: return PrivatePduPersistence.FAILED
        val targetName = IncomingMmsIdentity.persistedFileName(digest)
            ?: return PrivatePduPersistence.FAILED
        val partialName = IncomingMmsIdentity.partialFileName(digest)
            ?: return PrivatePduPersistence.FAILED

        val directory = File(context.filesDir, "mms-inbox")
        if (!directory.exists() && !directory.mkdirs()) return PrivatePduPersistence.FAILED
        val canonicalRoot = runCatching { context.filesDir.canonicalFile }.getOrNull()
            ?: return PrivatePduPersistence.FAILED
        val canonicalDirectory = runCatching { directory.canonicalFile }.getOrNull()
            ?: return PrivatePduPersistence.FAILED
        if (canonicalDirectory.parentFile != canonicalRoot || !canonicalDirectory.isDirectory) {
            return PrivatePduPersistence.FAILED
        }

        val canonicalTarget = runCatching { File(canonicalDirectory, targetName).canonicalFile }.getOrNull()
            ?: return PrivatePduPersistence.FAILED
        val canonicalPartial = runCatching { File(canonicalDirectory, partialName).canonicalFile }.getOrNull()
            ?: return PrivatePduPersistence.FAILED
        if (
            canonicalTarget.parentFile != canonicalDirectory ||
            canonicalPartial.parentFile != canonicalDirectory
        ) return PrivatePduPersistence.FAILED

        if (canonicalTarget.exists()) {
            return if (
                canonicalTarget.isFile &&
                canonicalTarget.length() == data.size.toLong() &&
                digestFile(canonicalTarget) == digest
            ) {
                PrivatePduPersistence.EXISTING
            } else {
                LocalLogger(context).log(
                    LocalLogger.LogLevel.SECURITY,
                    "DefaultSms",
                    "Collision ou corruption détectée sur une identité MMS privée existante"
                )
                PrivatePduPersistence.FAILED
            }
        }

        // The receiver worker is serialized. Any `.part` here is therefore residue from a previous
        // crash, not a concurrently valid writer. Refuse to continue if residue cannot be removed.
        for (partial in canonicalDirectory.listFiles().orEmpty().filter { it.isFile && it.extension == "part" }) {
            if (!runCatching { partial.delete() }.getOrDefault(false)) {
                return PrivatePduPersistence.FAILED
            }
        }

        // Leave at most MAX_STORED_MMS - 1 completed files before creating a new one. Storage bound
        // is fail-closed: a deletion failure cannot silently grow the private inbox indefinitely.
        val files = canonicalDirectory.listFiles()
            ?.filter { it.isFile && it.extension == "pdu" }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        for (old in files.drop(MAX_STORED_MMS - 1)) {
            if (!runCatching { old.delete() }.getOrDefault(false)) {
                return PrivatePduPersistence.FAILED
            }
        }

        if (canonicalPartial.exists() && !runCatching { canonicalPartial.delete() }.getOrDefault(false)) {
            return PrivatePduPersistence.FAILED
        }

        return try {
            FileOutputStream(canonicalPartial).use { stream ->
                stream.write(data)
                stream.fd.sync()
            }
            if (!canonicalPartial.renameTo(canonicalTarget)) {
                val replayWonRace = canonicalTarget.isFile &&
                    canonicalTarget.length() == data.size.toLong() &&
                    digestFile(canonicalTarget) == digest
                runCatching { canonicalPartial.delete() }
                if (replayWonRace) PrivatePduPersistence.EXISTING else PrivatePduPersistence.FAILED
            } else {
                LocalLogger(context).log(
                    LocalLogger.LogLevel.SECURITY,
                    "DefaultSms",
                    "MMS téléchargé conservé localement; taille=${data.size}; preview=" +
                        if (safePreview is MmsDecodePipeline.Result.Accepted) "SAFE" else "QUARANTINED"
                )
                PrivatePduPersistence.CREATED
            }
        } catch (_: Exception) {
            runCatching { canonicalPartial.delete() }
            PrivatePduPersistence.FAILED
        }
    }

    private fun digestFile(file: File): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }.getOrNull()

    private companion object {
        val WORKER = Executors.newSingleThreadExecutor { task ->
            Thread(task, "sentinel-mms-download").apply { isDaemon = true }
        }
        val TOKEN = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        const val MAX_STORED_MMS = 50
    }
}