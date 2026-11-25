package com.sentinel.quantum.security

import android.content.Context

/**
 * Replays a private MMS whose provider projection did not finish.
 * A quarantined PDU stays retained and is not projected again.
 * Failure leaves the ledger entry in place. Nothing is deleted here.
 */
internal object IncomingMmsProjectionRetry {
    fun retryPending(context: Context): Boolean {
        val pending = IncomingMmsPrivateRetention.read(context.filesDir).filter { it.subscriptionId != null }
        if (pending.isEmpty()) return false
        if (context.readSmsRoleStateFailClosed() != SmsActivationDiagnostics.SmsRoleState.HELD) {
            return true
        }
        var stillPending = false
        for (entry in pending) {
            val subscriptionId = entry.subscriptionId ?: continue
            if (!MmsSubscriptionResolver.isValidSubscriptionId(subscriptionId)) {
                IncomingMmsPrivateRetention.clearSubscription(context.filesDir, entry.digest)
                continue
            }
            val data = IncomingMmsPrivateStore.read(context.filesDir, entry.digest)
            if (data == null) {
                stillPending = true
                continue
            }
            when (val prepared = IncomingMmsProjectionPipeline.prepare(data, entry.digest, subscriptionId)) {
                is IncomingMmsProjectionPipeline.Result.Quarantined ->
                    IncomingMmsPrivateRetention.clearSubscription(context.filesDir, entry.digest)
                is IncomingMmsProjectionPipeline.Result.Ready -> {
                    val store = IncomingMmsConversationStore(context)
                    runCatching { store.repairJournal() }
                    when (store.project(prepared.plan)) {
                        is IncomingMmsConversationStore.ProjectResult.Ready ->
                            IncomingMmsPrivateStore.releaseProjected(context.filesDir, entry.digest)
                        is IncomingMmsConversationStore.ProjectResult.Rejected -> stillPending = true
                    }
                }
            }
        }
        return stillPending
    }
}
