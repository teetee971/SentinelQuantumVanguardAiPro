package com.sentinel.quantum.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import java.util.concurrent.Executors

/**
 * Bounded WAP/MMS intake for the default-SMS client.
 *
 * Notification.ind messages are handed to Android's public MMS download transport. A direct
 * M-Retrieve.conf takes the same private-persistence + fail-closed provider-projection path as the
 * later download callback, so the two ingress routes cannot diverge in product state.
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

        val persistence = IncomingMmsPrivateStore.persist(context.filesDir, data)
        val digest = persistence.digestHex
        if (persistence.state == IncomingMmsPrivateStore.State.FAILED || digest == null) {
            LocalLogger(context).log(
                LocalLogger.LogLevel.SECURITY,
                "MmsDeliver",
                "Persistance MMS privée refusée fail-closed"
            )
            return
        }

        val subscriptionId = MmsSubscriptionResolver.resolve(intent)
        val prepared = if (SubscriptionManager.isValidSubscriptionId(subscriptionId)) {
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
        }

        val replay = persistence.state == IncomingMmsPrivateStore.State.EXISTING
        if (replay) {
            LocalLogger(context).log(
                LocalLogger.LogLevel.INFO,
                "MmsDeliver",
                "Replay WAP MMS reconnu par identité SHA-256; aucune chronologie dupliquée"
            )
        } else {
            val providerReady = providerResult is IncomingMmsConversationStore.ProjectResult.Ready
            runCatching {
                PhonePrivateTimelineStore(context).append(
                    PhonePrivateTimeline.Event(
                        kind = PhonePrivateTimeline.Kind.MMS,
                        timestampMs = System.currentTimeMillis(),
                        direction = "INCOMING",
                        signal = when {
                            providerReady -> "MMS_PROVIDER_READY"
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
                    if (providerReady) "READY" else "PRIVATE_ONLY"
            )
        }

        val canonical = providerResult is IncomingMmsConversationStore.ProjectResult.Ready
        SmsNotificationHelper.notifyMessage(
            context,
            title = "MMS reçu",
            preview = when {
                canonical -> "MMS ajouté à la conversation Android."
                safePreview is MmsDecodePipeline.Result.Accepted ->
                    "MMS conservé localement · aperçu sécurisé: ${safePreview.parts.size} partie(s) validée(s)."
                else -> "MMS conservé en quarantaine locale."
            },
            notificationId = digest.take(16).hashCode()
        )
    }

    private fun holdsSmsRole(context: Context): Boolean =
        context.readSmsRoleStateFailClosed() == SmsActivationDiagnostics.SmsRoleState.HELD

    companion object {
        private val WORKER = Executors.newSingleThreadExecutor { task ->
            Thread(task, "sentinel-mms-deliver").apply { isDaemon = true }
        }
        private const val MMS_MIME_TYPE = "application/vnd.wap.mms-message"
        private const val MAX_PDU_BYTES = 512 * 1024
    }
}
