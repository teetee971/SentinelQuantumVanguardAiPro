package com.sentinel.quantum

import android.content.Context
import com.sentinel.quantum.security.PhonePrivateTimeline
import com.sentinel.quantum.security.PhonePrivateTimelineStore
import com.sentinel.quantum.security.SmsActivationDiagnostics
import com.sentinel.quantum.security.phoneCoreSnapshots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn

/** Local in-memory comparison only: no identifiers are logged, exported or sent over the network. */
internal object PhoneCoreLiveRefresh {
    data class Snapshot(
        val diagnostics: PhoneCoreLocalDiagnostics.Snapshot,
        val subscriptions: List<Int>,
        val operationalEnvironmentReady: Boolean,
        val events: List<PhonePrivateTimeline.Event>
    )

    fun snapshots(context: Context) = phoneCoreSnapshots(read = {
        Snapshot(
            diagnostics = PhoneCoreLocalDiagnostics.read(context),
            subscriptions = SmsActivationDiagnostics(context).snapshot().activeSubscriptionIds.sorted(),
            operationalEnvironmentReady = PhoneCoreRuntimeFacts.hasOperationalCarrierEnvironment(context),
            events = PhonePrivateTimelineStore(context).read().events
        )
    }).flowOn(Dispatchers.IO)
}

