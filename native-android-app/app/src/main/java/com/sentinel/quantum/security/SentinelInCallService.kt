package com.sentinel.quantum.security

import android.content.Intent
import android.os.Build
import android.os.OutcomeReceiver
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.telecom.InCallService
import androidx.annotation.RequiresApi
import com.sentinel.quantum.SentinelInCallActivity

/**
 * ROLE_DIALER in-call foundation. Exposes only bounded call state/actions to Sentinel UI;
 * the Telecom Call object remains owned by this service.
 */
class SentinelInCallService : InCallService() {
    private var connectedEvidenceRecorded = false
    private var incomingNotificationEvidenceRecorded = false
    private var currentDirection = "UNKNOWN"

    private val trackedCalls = LinkedHashSet<Call>()
    private val callIds = java.util.IdentityHashMap<Call, String>()
    private var nextCallId = 1L

    private var audioMuted: Boolean? = null
    private var audioRoutes: List<AudioRouteOption> = emptyList()
    private var audioStatus: String? = null

    @Volatile
    private var modernEndpoints: List<CallEndpoint> = emptyList()
    private var currentModernEndpointId: String? = null
    private var legacySupportedRouteMask: Int = 0

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            refreshForegroundCall()
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            refreshForegroundCall()
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        trackedCalls.add(call)
        if (!callIds.containsKey(call)) callIds[call] = "call-" + nextCallId++
        activeService = this
        call.registerCallback(callback)
        initializeAudioState()
        refreshForegroundCall()
        if (call.state != Call.STATE_RINGING) showInCallActivity()
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        super.onBringToForeground(showDialpad)
        showInCallActivity()
    }

    override fun onDestroy() {
        trackedCalls.toList().forEach { it.unregisterCallback(callback) }
        trackedCalls.clear()
        callIds.clear()
        callSnapshots = emptyList()
        currentCall = null
        snapshot = null
        activeService = null
        connectedEvidenceRecorded = false
        incomingNotificationEvidenceRecorded = false
        currentDirection = "UNKNOWN"
        clearAudioState()
        SentinelCallNotificationHelper.cancel(this)
        super.onDestroy()
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(callback)
        trackedCalls.remove(call)
        callIds.remove(call)
        if (trackedCalls.isNotEmpty()) {
            initializeAudioState()
            refreshForegroundCall()
        }
        if (trackedCalls.isEmpty()) {
            currentCall = null
            snapshot = null
            callSnapshots = emptyList()
            activeService = null
            connectedEvidenceRecorded = false
            incomingNotificationEvidenceRecorded = false
            currentDirection = "UNKNOWN"
            clearAudioState()
            SentinelCallNotificationHelper.cancel(this)
        }
        super.onCallRemoved(call)
    }

    private fun refreshForegroundCall() {
        val selected = selectForegroundCall(trackedCalls)
        if (currentCall !== selected) {
            currentCall = selected
            connectedEvidenceRecorded = false
            incomingNotificationEvidenceRecorded = false
        }
        currentDirection = selected?.let(::resolveDirection) ?: "UNKNOWN"
        callSnapshots = trackedCalls
            .sortedBy { callPriority(it.state) }
            .mapNotNull(::snapshotFor)
        selected?.let(::publish)

        if (selected?.state != Call.STATE_RINGING) {
            SentinelCallNotificationHelper.cancel(this)
            return
        }

        currentSnapshot()?.let { snapshot ->
            val posted = SentinelCallNotificationHelper.showIncoming(this, snapshot)
            if (posted) recordIncomingNotificationEvidence()
            else showInCallActivity()
        }
    }

    private fun selectForegroundCall(calls: Collection<Call>): Call? =
        calls.minByOrNull { callPriority(it.state) }

    private fun callPriority(state: Int): Int = when (state) {
        Call.STATE_RINGING -> 0
        Call.STATE_ACTIVE -> 1
        Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_SELECT_PHONE_ACCOUNT -> 2
        Call.STATE_HOLDING -> 3
        else -> 4
    }

    @RequiresApi(34)
    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        super.onCallEndpointChanged(callEndpoint)
        currentModernEndpointId = modernEndpointId(callEndpoint)
        audioStatus = null
        rebuildModernAudioRoutes()
        publishCurrentCall()
    }

    @RequiresApi(34)
    override fun onAvailableCallEndpointsChanged(availableEndpoints: MutableList<CallEndpoint>) {
        super.onAvailableCallEndpointsChanged(availableEndpoints)
        modernEndpoints = availableEndpoints.toList()
        rebuildModernAudioRoutes()
        publishCurrentCall()
    }

    @RequiresApi(34)
    override fun onMuteStateChanged(isMuted: Boolean) {
        super.onMuteStateChanged(isMuted)
        audioMuted = isMuted
        audioStatus = null
        publishCurrentCall()
    }

    @Suppress("DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        if (Build.VERSION.SDK_INT >= 34) return
        updateLegacyAudioState(audioState)
        publishCurrentCall()
    }

    private fun recordIncomingNotificationEvidence() {
        if (incomingNotificationEvidenceRecorded) return
        val stored = PhonePrivateTimelineStore(this).append(
            PhonePrivateTimeline.Event(
                kind = PhonePrivateTimeline.Kind.CALL,
                timestampMs = System.currentTimeMillis(),
                direction = "INCOMING",
                signal = PhoneCorePhysicalValidation.SIGNAL_CALL_NOTIFICATION_POSTED
            )
        )
        if (stored) incomingNotificationEvidenceRecorded = true
    }

    private fun showInCallActivity() {
        startActivity(
            Intent(this, SentinelInCallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    private fun initializeAudioState() {
        clearAudioState()
        if (Build.VERSION.SDK_INT >= 34) {
            initializeModernAudioState()
        } else {
            initializeLegacyAudioState()
        }
    }

    @RequiresApi(34)
    private fun initializeModernAudioState() {
        currentModernEndpointId = runCatching {
            modernEndpointId(currentCallEndpoint)
        }.getOrNull()
        rebuildModernAudioRoutes()
    }

    @Suppress("DEPRECATION")
    private fun initializeLegacyAudioState() {
        val state = runCatching { callAudioState }.getOrNull() ?: return
        updateLegacyAudioState(state)
    }

    private fun clearAudioState() {
        audioMuted = null
        audioRoutes = emptyList()
        audioStatus = null
        modernEndpoints = emptyList()
        currentModernEndpointId = null
        legacySupportedRouteMask = 0
    }

    @Suppress("DEPRECATION")
    private fun updateLegacyAudioState(state: CallAudioState) {
        audioMuted = state.isMuted
        legacySupportedRouteMask = state.supportedRouteMask
        audioStatus = null
        val routes = buildList {
            addLegacyRouteIfSupported(
                state,
                CallAudioState.ROUTE_EARPIECE,
                "legacy:earpiece",
                InCallAudioUiPolicy.Kind.EARPIECE
            )
            addLegacyRouteIfSupported(
                state,
                CallAudioState.ROUTE_SPEAKER,
                "legacy:speaker",
                InCallAudioUiPolicy.Kind.SPEAKER
            )
            addLegacyRouteIfSupported(
                state,
                CallAudioState.ROUTE_BLUETOOTH,
                "legacy:bluetooth",
                InCallAudioUiPolicy.Kind.BLUETOOTH
            )
            addLegacyRouteIfSupported(
                state,
                CallAudioState.ROUTE_WIRED_HEADSET,
                "legacy:wired",
                InCallAudioUiPolicy.Kind.WIRED_HEADSET
            )
        }
        audioRoutes = InCallAudioUiPolicy.present(routes).map {
            AudioRouteOption(it.id, it.label, it.selected)
        }
    }

    @Suppress("DEPRECATION")
    private fun MutableList<InCallAudioUiPolicy.Route>.addLegacyRouteIfSupported(
        state: CallAudioState,
        route: Int,
        id: String,
        kind: InCallAudioUiPolicy.Kind
    ) {
        if (state.supportedRouteMask and route == 0) return
        add(
            InCallAudioUiPolicy.Route(
                id = id,
                kind = kind,
                selected = state.route == route
            )
        )
    }

    @RequiresApi(34)
    private fun rebuildModernAudioRoutes() {
        val routes = modernEndpoints.map { endpoint ->
            InCallAudioUiPolicy.Route(
                id = modernEndpointId(endpoint),
                kind = endpointKind(endpoint.endpointType),
                deviceName = endpoint.endpointName.toString(),
                selected = modernEndpointId(endpoint) == currentModernEndpointId
            )
        }
        audioRoutes = InCallAudioUiPolicy.present(routes).map {
            AudioRouteOption(it.id, it.label, it.selected)
        }
    }

    @RequiresApi(34)
    private fun modernEndpointId(endpoint: CallEndpoint): String =
        "endpoint:" + endpoint.identifier.toString()

    @RequiresApi(34)
    private fun endpointKind(type: Int): InCallAudioUiPolicy.Kind = when (type) {
        CallEndpoint.TYPE_EARPIECE -> InCallAudioUiPolicy.Kind.EARPIECE
        CallEndpoint.TYPE_BLUETOOTH -> InCallAudioUiPolicy.Kind.BLUETOOTH
        CallEndpoint.TYPE_WIRED_HEADSET -> InCallAudioUiPolicy.Kind.WIRED_HEADSET
        CallEndpoint.TYPE_SPEAKER -> InCallAudioUiPolicy.Kind.SPEAKER
        CallEndpoint.TYPE_STREAMING -> InCallAudioUiPolicy.Kind.STREAMING
        else -> InCallAudioUiPolicy.Kind.UNKNOWN
    }

    private fun publishCurrentCall() {
        currentCall?.let(::publish)
    }

    private fun snapshotFor(call: Call): CallSnapshot? {
        val id = callIds[call] ?: return null
        return CallSnapshot(
            id = id,
            state = call.state,
            displayName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                call.details.contactDisplayName?.toString()?.take(MAX_LABEL_CHARS)
            } else null,
            handle = call.details.handle?.schemeSpecificPart?.take(MAX_HANDLE_CHARS),
            canHold = InCallTruthPolicy.canToggleHold(
                currentHoldCapability = call.details.can(Call.Details.CAPABILITY_HOLD),
                genericConference = call.details.hasProperty(Call.Details.PROPERTY_GENERIC_CONFERENCE)
            ),
            canMute = InCallTruthPolicy.canMute(
                call.details.can(Call.Details.CAPABILITY_MUTE)
            ),
            canMergeConference = call.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE),
            canSwapConference = call.details.can(Call.Details.CAPABILITY_SWAP_CONFERENCE),
            isMuted = audioMuted,
            audioRoutes = audioRoutes,
            audioStatus = audioStatus
        )
    }

    private fun publish(call: Call) {
        if (currentDirection == "UNKNOWN") currentDirection = resolveDirection(call)
        snapshot = snapshotFor(call)

        if (
            call.state == Call.STATE_ACTIVE &&
            !connectedEvidenceRecorded &&
            currentDirection in setOf("INCOMING", "OUTGOING")
        ) {
            val stored = PhonePrivateTimelineStore(this).append(
                PhonePrivateTimeline.Event(
                    kind = PhonePrivateTimeline.Kind.CALL,
                    timestampMs = System.currentTimeMillis(),
                    direction = currentDirection,
                    signal = PhoneCorePhysicalValidation.SIGNAL_CALL_ACTIVE
                )
            )
            if (stored) connectedEvidenceRecorded = true
        }
    }

    private fun resolveDirection(call: Call): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (call.details.callDirection) {
                Call.Details.DIRECTION_INCOMING -> "INCOMING"
                Call.Details.DIRECTION_OUTGOING -> "OUTGOING"
                else -> "UNKNOWN"
            }
        } else {
            InCallTruthPolicy.legacyDirection(
                when (call.state) {
                    Call.STATE_RINGING -> InCallTruthPolicy.LegacyState.RINGING
                    Call.STATE_DIALING -> InCallTruthPolicy.LegacyState.DIALING
                    Call.STATE_CONNECTING -> InCallTruthPolicy.LegacyState.CONNECTING
                    Call.STATE_SELECT_PHONE_ACCOUNT -> InCallTruthPolicy.LegacyState.SELECT_PHONE_ACCOUNT
                    else -> InCallTruthPolicy.LegacyState.OTHER
                }
            )
        }

    private fun requestMicrophoneMuted(muted: Boolean): Boolean {
        val call = currentCall ?: return false
        if (!call.details.can(Call.Details.CAPABILITY_MUTE)) {
            audioStatus = "Android n’autorise pas la modification du microphone pour cet appel."
            publishCurrentCall()
            return false
        }
        return runCatching {
            audioStatus = if (muted) "Coupure du microphone demandée…" else "Réactivation du microphone demandée…"
            publishCurrentCall()
            setMuted(muted)
            true
        }.getOrElse {
            audioStatus = "Android a refusé le changement d’état du microphone."
            publishCurrentCall()
            false
        }
    }

    private fun requestAudioRoute(routeId: String): Boolean {
        if (currentCall == null || routeId.isBlank()) return false
        return if (Build.VERSION.SDK_INT >= 34) {
            requestModernAudioRoute(routeId)
        } else {
            requestLegacyAudioRoute(routeId)
        }
    }

    @RequiresApi(34)
    private fun requestModernAudioRoute(routeId: String): Boolean {
        val endpoint = modernEndpoints.firstOrNull { modernEndpointId(it) == routeId } ?: run {
            audioStatus = "Cette sortie audio n’est plus disponible."
            publishCurrentCall()
            return false
        }
        audioStatus = "Changement de sortie audio demandé…"
        publishCurrentCall()
        return runCatching {
            requestCallEndpointChange(
                endpoint,
                mainExecutor,
                object : OutcomeReceiver<Void?, CallEndpointException> {
                    override fun onResult(result: Void?) {
                        audioStatus = "Changement audio accepté par Android ; confirmation en cours…"
                        publishCurrentCall()
                    }

                    override fun onError(error: CallEndpointException) {
                        audioStatus = "Android n’a pas pu changer la sortie audio."
                        publishCurrentCall()
                    }
                }
            )
            true
        }.getOrElse {
            audioStatus = "Android n’a pas pu demander cette sortie audio."
            publishCurrentCall()
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun requestLegacyAudioRoute(routeId: String): Boolean {
        val route = when (routeId) {
            "legacy:earpiece" -> CallAudioState.ROUTE_EARPIECE
            "legacy:speaker" -> CallAudioState.ROUTE_SPEAKER
            "legacy:bluetooth" -> CallAudioState.ROUTE_BLUETOOTH
            "legacy:wired" -> CallAudioState.ROUTE_WIRED_HEADSET
            else -> return false
        }
        if (legacySupportedRouteMask and route == 0) {
            audioStatus = "Cette sortie audio n’est plus disponible."
            publishCurrentCall()
            return false
        }
        return runCatching {
            audioStatus = "Changement de sortie audio demandé…"
            publishCurrentCall()
            setAudioRoute(route)
            true
        }.getOrElse {
            audioStatus = "Android n’a pas pu changer la sortie audio."
            publishCurrentCall()
            false
        }
    }

    data class AudioRouteOption(
        val id: String,
        val label: String,
        val selected: Boolean
    )

    data class CallSnapshot(
        val id: String,
        val state: Int,
        val displayName: String?,
        val handle: String?,
        val canHold: Boolean,
        val canMute: Boolean,
        val canMergeConference: Boolean,
        val canSwapConference: Boolean,
        val isMuted: Boolean?,
        val audioRoutes: List<AudioRouteOption>,
        val audioStatus: String?
    )

    companion object {
        private const val MAX_LABEL_CHARS = 120
        private const val MAX_HANDLE_CHARS = 64

        @Volatile private var currentCall: Call? = null
        @Volatile private var snapshot: CallSnapshot? = null
        @Volatile private var callSnapshots: List<CallSnapshot> = emptyList()
        @Volatile private var activeService: SentinelInCallService? = null

        fun currentSnapshot(): CallSnapshot? = snapshot
        fun currentSnapshots(): List<CallSnapshot> = callSnapshots
        fun hasActiveCall(): Boolean = currentCall != null

        private fun callById(id: String): Call? = activeService?.let { service ->
            service.callIds.entries.firstOrNull { it.value == id }?.key
        }

        fun disconnect(id: String): Boolean = callById(id)?.let { call ->
            if (call.state == Call.STATE_DISCONNECTED || call.state == Call.STATE_DISCONNECTING) return@let false
            call.disconnect(); true
        } ?: false

        fun hold(id: String): Boolean = callById(id)?.let { call ->
            if (call.state != Call.STATE_ACTIVE || call.details.hasProperty(Call.Details.PROPERTY_GENERIC_CONFERENCE) || !call.details.can(Call.Details.CAPABILITY_HOLD)) return@let false
            call.hold(); true
        } ?: false

        fun unhold(id: String): Boolean = callById(id)?.let { call ->
            if (call.state != Call.STATE_HOLDING || call.details.hasProperty(Call.Details.PROPERTY_GENERIC_CONFERENCE) || !call.details.can(Call.Details.CAPABILITY_HOLD)) return@let false
            call.unhold(); true
        } ?: false

        fun answer(): Boolean = currentCall?.let { call ->
            if (call.state != Call.STATE_RINGING) return@let false
            call.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
            true
        } ?: false

        fun reject(): Boolean = currentCall?.let { call ->
            if (call.state != Call.STATE_RINGING) return@let false
            call.reject(false, null)
            true
        } ?: false

        fun disconnect(): Boolean = currentCall?.let { call ->
            if (call.state == Call.STATE_DISCONNECTED || call.state == Call.STATE_DISCONNECTING) return@let false
            call.disconnect()
            true
        } ?: false

        fun hold(): Boolean = currentCall?.let { call ->
            if (call.state != Call.STATE_ACTIVE) return@let false
            if (call.details.hasProperty(Call.Details.PROPERTY_GENERIC_CONFERENCE)) return@let false
            if (!call.details.can(Call.Details.CAPABILITY_HOLD)) return@let false
            call.hold()
            true
        } ?: false

        fun unhold(): Boolean = currentCall?.let { call ->
            if (call.state != Call.STATE_HOLDING) return@let false
            if (call.details.hasProperty(Call.Details.PROPERTY_GENERIC_CONFERENCE)) return@let false
            if (!call.details.can(Call.Details.CAPABILITY_HOLD)) return@let false
            call.unhold()
            true
        } ?: false

        fun mergeConference(id: String): Boolean = callById(id)?.let { call ->
            if (!call.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE)) return@let false
            return@let runCatching { call.mergeConference(); true }.getOrDefault(false)
        } ?: false

        fun swapConference(id: String): Boolean = callById(id)?.let { call ->
            if (!call.details.can(Call.Details.CAPABILITY_SWAP_CONFERENCE)) return@let false
            return@let runCatching { call.swapConference(); true }.getOrDefault(false)
        } ?: false

        fun setMicrophoneMuted(muted: Boolean): Boolean =
            activeService?.requestMicrophoneMuted(muted) ?: false

        fun selectAudioRoute(routeId: String): Boolean =
            activeService?.requestAudioRoute(routeId) ?: false

        fun startDtmf(digit: Char): Boolean {
            if (digit !in "0123456789*#") return false
            return currentCall?.let { call ->
                if (call.state != Call.STATE_ACTIVE) return@let false
                call.playDtmfTone(digit)
                true
            } ?: false
        }

        fun stopDtmf(): Boolean = currentCall?.let { call ->
            call.stopDtmfTone()
            true
        } ?: false
    }
}
