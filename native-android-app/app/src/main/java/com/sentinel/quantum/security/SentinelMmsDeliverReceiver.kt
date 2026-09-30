package com.sentinel.quantum.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Bounded WAP/MMS intake for the staged default-SMS client.
 *
 * The raw PDU remains in app-private storage, is never uploaded, and is only accepted while
 * Sentinel is actually the user-selected default SMS handler. A bounded decoder is applied only
 * to derive a fail-closed safe-preview state; unsupported or malformed content remains quarantined.
 *
 * BroadcastReceiver.onReceive() performs only cheap envelope checks. Carrier coordination,
 * decoding, durable file I/O, fsync, timeline persistence and notifications run on the private
 * serial worker under goAsync(), so a slow device cannot stall the broadcast main thread.
 */
class SentinelMmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        if (intent.type.orEmpty() != MMS_MIME_TYPE) return

        val data = intent.getByteArrayExtra("data") ?: return
        if (data.isEmpty() || data.size > MAX_PDU_BYTES) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val deliveredIntent = Intent(intent).putExtra("data", data.copyOf())
        val submitted = runCatching {
            WORKER.execute {
                try {
                    processDelivery(appContext, deliveredIntent)
                } catch (_: Exception) {
                    LocalLogger(appContext).log(
                        LocalLogger.LogLevel.WARNING,
                        "MmsDeliver",
                        "Échec inattendu du traitement d’un MMS entrant"
                    )
                } finally {
                    pendingResult.finish()
                }
            }
        }.isSuccess

        if (!submitted) {
            LocalLogger(appContext).log(
                LocalLogger.LogLevel.WARNING,
                "MmsDeliver",
                "MMS entrant non planifié : worker indisponible"
            )
            pendingResult.finish()
        }
    }

    private fun processDelivery(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        if (intent.type.orEmpty() != MMS_MIME_TYPE) return
        if (!holdsSmsRole(context)) return

        val data = intent.getByteArrayExtra("data") ?: return
        if (data.isEmpty() || data.size > MAX_PDU_BYTES) return

        when (val download = MmsDownloadCoordinator.request(context, data, intent)) {
            is MmsDownloadCoordinator.Result.Requested -> {
                runCatching {
                    PhonePrivateTimelineStore(context).append(
                        PhonePrivateTimeline.Event(
                            kind = PhonePrivateTimeline.Kind.MMS,
                            timestampMs = System.currentTimeMillis(),
                            direction = "INCOMING",
                            signal = "MMS_DOWNLOAD_REQUESTED"
                        )
                    )
                }.onFailure {
                    LocalLogger(context).log(
                        LocalLogger.LogLevel.WARNING,
                        "MmsDeliver",
                        "Chronologie privée indisponible; le traitement MMS principal continue"
                    )
                }
                SmsNotificationHelper.notifyMessage(
                    context,
                    title = "MMS en cours",
                    preview = "Android récupère le contenu MMS sur le réseau opérateur.",
                    notificationId = download.fileName.hashCode()
                )
                return
            }
            is MmsDownloadCoordinator.Result.Rejected -> {
                LocalLogger(context).log(
                    LocalLogger.LogLevel.WARNING,
                    "DefaultSms",
                    "Notification MMS reçue mais téléchargement non démarré; raison=" + download.reason
                )
                SmsNotificationHelper.notifyMessage(
                    context,
                    title = "MMS à récupérer",
                    preview = "Le téléchargement MMS n’a pas pu être démarré. Vérifiez la SIM, les données mobiles et le rôle SMS.",
                    notificationId = data.contentHashCode()
                )
                return
            }
            MmsDownloadCoordinator.Result.NotNotification -> Unit
        }

        val safePreview = MmsDecodePipeline.decodeAndValidate(data, SentinelMmsPduDecoder)

        val directory = File(context.filesDir, "mms-inbox")
        if (!directory.exists() && !directory.mkdirs()) return
        val canonicalRoot = runCatching { context.filesDir.canonicalFile }.getOrNull() ?: return
        val canonicalDirectory = runCatching { directory.canonicalFile }.getOrNull() ?: return
        if (canonicalDirectory.parentFile != canonicalRoot || !canonicalDirectory.isDirectory) return
        prune(canonicalDirectory)

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(data)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            .take(24)

        val target = File(canonicalDirectory, "${System.currentTimeMillis()}-$digest.pdu")
        val canonicalTarget = runCatching { target.canonicalFile }.getOrNull() ?: return
        if (canonicalTarget.parentFile != canonicalDirectory || canonicalTarget.exists()) return

        runCatching {
            FileOutputStream(canonicalTarget).use { stream ->
                stream.write(data)
                stream.fd.sync()
            }
        }.onFailure {
            LocalLogger(context).log(
                LocalLogger.LogLevel.WARNING,
                "MmsDeliver",
                "Échec d’écriture du PDU MMS en stockage privé; aucun événement de réception n’est publié"
            )
        }.onSuccess {
            runCatching {
                PhonePrivateTimelineStore(context).append(
                    PhonePrivateTimeline.Event(
                        kind = PhonePrivateTimeline.Kind.MMS,
                        timestampMs = System.currentTimeMillis(),
                        direction = "INCOMING",
                        signal = if (safePreview is MmsDecodePipeline.Result.Accepted) {
                            "MMS_SAFE_PREVIEW_READY"
                        } else {
                            "MMS_LOCAL_QUARANTINE"
                        }
                    )
                )
            }.onFailure {
                LocalLogger(context).log(
                    LocalLogger.LogLevel.WARNING,
                    "MmsDeliver",
                    "Chronologie privée indisponible; le traitement MMS principal continue"
                )
            }
            SmsNotificationHelper.notifyMessage(
                context,
                title = "MMS reçu",
                preview = when (safePreview) {
                    is MmsDecodePipeline.Result.Accepted ->
                        "MMS conservé localement · aperçu sécurisé: ${safePreview.parts.size} partie(s) validée(s)."
                    is MmsDecodePipeline.Result.Rejected ->
                        "MMS conservé en quarantaine locale · aperçu refusé: ${safePreview.reason.take(48)}."
                },
                notificationId = digest.hashCode()
            )
            LocalLogger(context).log(
                LocalLogger.LogLevel.SECURITY,
                "DefaultSms",
                "MMS entrant conservé localement; taille=${data.size}; preview=" +
                    if (safePreview is MmsDecodePipeline.Result.Accepted) "SAFE" else "QUARANTINED"
            )
        }.onFailure {
            runCatching { canonicalTarget.delete() }
            LocalLogger(context).log(
                LocalLogger.LogLevel.WARNING,
                "DefaultSms",
                "Échec de conservation locale d'un MMS"
            )
        }
    }

    private fun prune(directory: File) {
        val files = directory.listFiles()
            ?.filter { it.isFile && it.extension == "pdu" }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

        files.drop(MAX_STORED_MMS - 1).forEach { file ->
            runCatching { file.delete() }
        }
    }

    private fun holdsSmsRole(context: Context): Boolean =
        context.readSmsRoleStateFailClosed() == SmsActivationDiagnostics.SmsRoleState.HELD

    companion object {
        private val WORKER = Executors.newSingleThreadExecutor { task ->
            Thread(task, "sentinel-mms-deliver").apply { isDaemon = true }
        }
        private const val MMS_MIME_TYPE = "application/vnd.wap.mms-message"
        private const val MAX_PDU_BYTES = 512 * 1024
        private const val MAX_STORED_MMS = 50
    }
}
