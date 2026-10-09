package com.sentinel.quantum.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Bounded WAP/MMS intake for the default-SMS client.
 *
 * Notification.ind messages are handed to Android's public MMS download transport. A direct
 * M-Retrieve.conf takes the same private-persistence + fail-closed provider-projection path as the
 * later download callback, so the two ingress routes cannot diverge in product state. Saturation
 * first journals the bounded private PDU and SIM routing hints; a WorkManager replay performs the
 * transport/provider work away from Android's broadcast callback thread.
 */
class SentinelMmsDeliverReceiver : BroadcastReceiver() {
    enum class DeliveryOutcome {
        COMPLETE,
        RETRY
    }

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
                    val outcome = processDelivery(appContext, deliveredIntent)
                    if (outcome == DeliveryOutcome.RETRY) {
                        deliveredIntent.getByteArrayExtra("data")?.let { retryData ->
                            captureAndScheduleRecovery(appContext, retryData, deliveredIntent)
                        }
                    }
                } catch (_: Exception) {
                    deliveredIntent.getByteArrayExtra("data")?.let { retryData ->
                        runCatching {
                            captureAndScheduleRecovery(appContext, retryData, deliveredIntent)
                        }.onFailure {
                            LocalLogger(appContext).log(
                                LocalLogger.LogLevel.SECURITY,
                                "MmsDeliver",
                                "Échec inattendu du MMS entrant et capture de reprise indisponible"
                            )
                        }
                    }
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
            runCatching {
                captureAndScheduleRecovery(appContext, data.copyOf(), deliveredIntent)
            }.onFailure {
                LocalLogger(appContext).logAsync(
                    LocalLogger.LogLevel.WARNING,
                    "MmsDeliver",
                    "MMS entrant non planifié : capture durable indisponible après saturation"
                )
            }
            pendingResult.finish()
        }
    }

    private fun captureAndScheduleRecovery(
        context: Context,
        data: ByteArray,
        sourceIntent: Intent
    ) {
        if (!holdsSmsRole(context)) return
        val persistence = IncomingMmsWapIngressStore.persist(context.filesDir, data)
        val digest = persistence.digestHex
        if (persistence.state == IncomingMmsWapIngressStore.State.FAILED || digest == null) {
            LocalLogger(context).logAsync(
                LocalLogger.LogLevel.SECURITY,
                "MmsDeliver",
                "Capture WAP MMS refusée : persistance privée indisponible"
            )
            return
        }

        val journaled = IncomingMmsWapIngressJournal(context).record(
            digestHex = digest,
            subscriptionId = MmsSubscriptionResolver.explicitSubscriptionIdForRecovery(sourceIntent)
                ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID,
            slotIndex = MmsSubscriptionResolver.explicitSlotIndexForRecovery(sourceIntent)
        )
        if (!journaled) {
            runCatching { IncomingMmsWapIngressStore.delete(context.filesDir, digest) }
            LocalLogger(context).logAsync(
                LocalLogger.LogLevel.SECURITY,
                "MmsDeliver",
                "Capture WAP MMS refusée : journal de routage indisponible"
            )
            return
        }
        runCatching {
            IncomingMmsWapIngressRecoveryWorker.schedule(context)
        }.onFailure {
            LocalLogger(context).logAsync(
                LocalLogger.LogLevel.WARNING,
                "MmsDeliver",
                "Reprise WAP MMS journalisée mais non planifiée; le démarrage suivant la relancera"
            )
        }
    }

    internal fun processRecovery(
        context: Context,
        data: ByteArray,
        record: IncomingMmsWapIngressJournal.Record
    ): DeliveryOutcome {
        val recoveryIntent = Intent(Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION)
            .setType(MMS_MIME_TYPE)
            .putExtra("data", data)
            .putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, record.subscriptionId)
            .apply {
                record.slotIndex?.let { putExtra(SubscriptionManager.EXTRA_SLOT_INDEX, it) }
            }
        return processDelivery(context, recoveryIntent)
    }

    private fun processDelivery(context: Context, intent: Intent): DeliveryOutcome {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return DeliveryOutcome.COMPLETE
        if (intent.type.orEmpty() != MMS_MIME_TYPE) return DeliveryOutcome.COMPLETE
        if (!holdsSmsRole(context)) return DeliveryOutcome.COMPLETE

        val data = intent.getByteArrayExtra("data") ?: return DeliveryOutcome.COMPLETE
        if (data.isEmpty() || data.size > MAX_PDU_BYTES) return DeliveryOutcome.COMPLETE

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
                return DeliveryOutcome.COMPLETE
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
                return DeliveryOutcome.COMPLETE
            }
            MmsDownloadCoordinator.Result.NotNotification -> Unit
        }

        val persistence = IncomingMmsPrivateStore.persist(context.filesDir, data)
        val digest = persistence.digestHex
        if (persistence.state == IncomingMmsPrivateStore.State.FAILED || digest == null) {
            LocalLogger(context).log(
                LocalLogger.LogLevel.SECURITY,
                "MmsDeliver",
                "Persistance MMS privée refusée fail-closed"
            )
            return DeliveryOutcome.RETRY
        }

        val subscriptionId = MmsSubscriptionResolver.resolve(context, intent)
        val prepared = if (MmsSubscriptionResolver.isValidSubscriptionId(subscriptionId)) {
            IncomingMmsProjectionPipeline.prepare(data, digest, subscriptionId)
        } else {
            IncomingMmsProjectionPipeline.Result.Quarantined("PLAN:INVALID_SUBSCRIPTION")
        }
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
                "MMS WAP conservé privé mais projection provider refusée; raison=${providerResult.reason}"
            )
            return DeliveryOutcome.RETRY
        }

        val providerInserted =
            providerResult is IncomingMmsConversationStore.ProjectResult.Ready && !providerResult.replay
        val providerReplay =
            providerResult is IncomingMmsConversationStore.ProjectResult.Ready && providerResult.replay
        val replay = persistence.state == IncomingMmsPrivateStore.State.EXISTING
        if (replay) {
            LocalLogger(context).log(
                LocalLogger.LogLevel.INFO,
                "MmsDeliver",
                "Replay WAP MMS reconnu par identité SHA-256; aucune chronologie dupliquée"
            )
        } else {
            runCatching {
                PhonePrivateTimelineStore(context).append(
                    PhonePrivateTimeline.Event(
                        kind = PhonePrivateTimeline.Kind.MMS,
                        timestampMs = System.currentTimeMillis(),
                        direction = "INCOMING",
                        signal = when {
                            providerInserted -> "MMS_PROVIDER_READY"
                            providerReplay -> "MMS_PROVIDER_REPLAY_NO_DUPLICATE"
                            safePreview is MmsDecodePipeline.Result.Accepted ->
                                "MMS_SAFE_PREVIEW_READY"
                            else -> "MMS_LOCAL_QUARANTINE"
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
            LocalLogger(context).log(
                LocalLogger.LogLevel.SECURITY,
                "DefaultSms",
                "MMS entrant conservé localement; taille=${data.size}; provider=" +
                    when {
                        providerInserted -> "INSERTED"
                        providerReplay -> "REPLAY_NO_DUPLICATE"
                        else -> "PRIVATE_ONLY"
                    }
            )
        }

        SmsNotificationHelper.notifyMessage(
            context,
            title = "MMS reçu",
            preview = when {
                providerInserted -> "MMS ajouté à la conversation Android."
                providerReplay -> "MMS déjà traité; aucun doublon n’a été ajouté à la conversation Android."
                safePreview is MmsDecodePipeline.Result.Accepted ->
                    "MMS conservé localement · aperçu sécurisé: ${safePreview.parts.size} partie(s) validée(s)."
                else -> "MMS conservé en quarantaine locale."
            },
            notificationId = digest.take(16).hashCode()
        )
        return DeliveryOutcome.COMPLETE
    }

    private fun holdsSmsRole(context: Context): Boolean =
        context.readSmsRoleStateFailClosed() == SmsActivationDiagnostics.SmsRoleState.HELD

    companion object {
        private const val MAX_PENDING_CALLBACKS = 32
        private val WORKER = ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue<Runnable>(MAX_PENDING_CALLBACKS),
            { task -> Thread(task, "sentinel-mms-deliver").apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy()
        )
        private const val MMS_MIME_TYPE = "application/vnd.wap.mms-message"
        private const val MAX_PDU_BYTES = 512 * 1024
    }
}
