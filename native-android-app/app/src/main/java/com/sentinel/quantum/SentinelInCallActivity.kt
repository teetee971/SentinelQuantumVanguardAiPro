package com.sentinel.quantum

import android.os.Bundle
import android.os.SystemClock
import com.sentinel.quantum.security.readTelecomInCall
import com.sentinel.quantum.security.requestAndroidInCallScreen
import android.os.PowerManager
import android.telecom.Call
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.sentinel.quantum.security.InCallPresencePolicy
import com.sentinel.quantum.security.LocalLogger
import com.sentinel.quantum.ui.design.PhoneCoreDisclosure
import kotlinx.coroutines.flow.collect
import com.sentinel.quantum.security.CallBlocklistStore
import com.sentinel.quantum.security.CallRuleEngine
import com.sentinel.quantum.security.CallTrustIndicator
import com.sentinel.quantum.security.LocalContactLookup
import com.sentinel.quantum.security.PhoneCorePhysicalValidation
import com.sentinel.quantum.security.PhoneNumberRiskRules
import com.sentinel.quantum.security.PhoneCountryPrefixCatalog
import com.sentinel.quantum.security.PhonePrivateTimeline
import com.sentinel.quantum.security.PhonePrivateTimelineStore
import com.sentinel.quantum.security.SentinelInCallService
import com.sentinel.quantum.ui.design.PhoneCoreBrand
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Sentinel-owned bounded in-call surface for ROLE_DIALER. */
class SentinelInCallActivity : ComponentActivity() {
    private var proximityLock: PowerManager.WakeLock? = null

    private fun synchronizeProximity(enabled: Boolean) {
        if (!enabled) {
            runCatching { proximityLock?.let { if (it.isHeld) it.release() } }
            proximityLock = null
            return
        }
        if (proximityLock?.isHeld == true) return
        runCatching {
            val power = getSystemService(PowerManager::class.java)
            if (power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                proximityLock = power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, packageName + ":incall-proximity")
                    .also { it.acquire(10 * 60 * 1000L) }
            }
        }
    }

    override fun onPause() {
        synchronizeProximity(false)
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val physicalTimeline = PhonePrivateTimelineStore(applicationContext)
        setContent {
            SentinelQuantumTheme {
                val useDarkCallSurface = androidx.compose.foundation.isSystemInDarkTheme()
                androidx.compose.runtime.SideEffect {
                    androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
                        .isAppearanceLightStatusBars = !useDarkCallSurface
                }
                var session by remember { mutableStateOf(SentinelInCallService.sessions.value) }
                var telecomInCall by remember { mutableStateOf<Boolean?>(null) }
                var hadSession by remember { mutableStateOf(false) }
                var awaitingInitialSession by remember { mutableStateOf(true) }
                var resumed by remember { mutableStateOf(false) }
                var recordedCallId by remember { mutableStateOf<String?>(null) }
                val snapshot = session.primary
                val calls = session.calls

                LaunchedEffect(Unit) {
                    val enteredAt = SystemClock.elapsedRealtime()
                    lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        resumed = true
                        try {
                            launch {
                                SentinelInCallService.sessions.collect { observed ->
                                    session = observed
                                    telecomInCall = readTelecomInCall()
                                    if (observed.primary != null) hadSession = true
                                }
                            }
                            launch {
                                while (true) {
                                    telecomInCall = readTelecomInCall()
                                    SentinelInCallService.requestRefresh()
                                    awaitingInitialSession = SystemClock.elapsedRealtime() - enteredAt < 1_500L
                                    synchronizeProximity(session.primary?.state?.let { it != Call.STATE_DISCONNECTED && it != Call.STATE_DISCONNECTING } == true || telecomInCall == true)
                                    delay(500)
                                }
                            }
                            awaitCancellation()
                        } finally {
                            synchronizeProximity(false)
                            resumed = false
                        }
                    }
                }

                // Recording a proof must never cancel the independent live session collector.
                LaunchedEffect(snapshot?.id, snapshot?.state, resumed) {
                    val current = snapshot ?: return@LaunchedEffect
                    if (!resumed || recordedCallId == current.id ||
                        current.state == Call.STATE_DISCONNECTED || current.state == Call.STATE_DISCONNECTING) return@LaunchedEffect
                    val stored = withContext(Dispatchers.IO) {
                        runCatching {
                            physicalTimeline.append(
                                PhonePrivateTimeline.Event(
                                    kind = PhonePrivateTimeline.Kind.CALL,
                                    timestampMs = System.currentTimeMillis(),
                                    direction = "LOCAL",
                                    signal = PhoneCorePhysicalValidation.SIGNAL_INCALL_UI_SHOWN
                                )
                            )
                        }.getOrDefault(false)
                    }
                    if (stored) recordedCallId = current.id
                }

                InCallScreen(
                    snapshot = snapshot,
                    calls = calls,
                    missingSession = InCallPresencePolicy.resolve(telecomInCall, hadSession, awaitingInitialSession),
                    onRecover = {
                        SentinelInCallService.requestRefresh()
                        requestAndroidInCallScreen()
                    },
                    onConfigure = {
                        startActivity(android.content.Intent(this, PhoneCoreDiagnosticActivity::class.java))
                    },
                    onClose = ::finish
                )
            }
        }
    }
}

@Composable
private fun InCallScreen(
    snapshot: SentinelInCallService.CallSnapshot?,
    calls: List<SentinelInCallService.CallSnapshot>,
    missingSession: InCallPresencePolicy.MissingSession,
    onRecover: () -> String,
    onConfigure: () -> Unit,
    onClose: () -> Unit
) {
    var showDialpad by rememberSaveable { mutableStateOf(false) }
    var showAudioRoutes by rememberSaveable { mutableStateOf(false) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val context = LocalContext.current
    var trustIndicator by remember(snapshot?.handle) {
        mutableStateOf(
            CallTrustIndicator.Result(
                level = CallTrustIndicator.Level.UNKNOWN,
                title = "Confiance non mesurée",
                detail = "Analyse locale en attente."
            )
        )
    }

    LaunchedEffect(snapshot?.handle) {
        val number = snapshot?.handle?.takeIf { it.isNotBlank() }
        trustIndicator = if (number == null) {
            CallTrustIndicator.assess(
                CallTrustIndicator.Input(
                    contactKnown = false,
                    localDecision = null,
                    premiumRateCaution = false
                )
            )
        } else {
            withContext(Dispatchers.IO) {
                val appContext = context.applicationContext
                val store = CallBlocklistStore(appContext)
                val rules = store.snapshot()
                val decision = runCatching {
                    CallRuleEngine(
                        blockedNumberHashes = rules.blockedNumberHashes,
                        blockedPrefixes = rules.blockedPrefixes,
                        reputationSilencePrefixes = rules.signedSilencePrefixes,
                        fingerprintsForNumber = store::fingerprintsForNumber
                    ).evaluate(number)
                }.getOrNull()
                val contactKnown = runCatching {
                    LocalContactLookup(appContext).find(number) != null
                }.getOrDefault(false)
                CallTrustIndicator.assess(
                    CallTrustIndicator.Input(
                        contactKnown = contactKnown,
                        localDecision = decision,
                        premiumRateCaution = PhoneNumberRiskRules.isKnownPremiumRatePrefix(number)
                    )
                )
            }
        }
    }

    LaunchedEffect(snapshot?.id, snapshot?.state, snapshot?.connectedAtMs) {
        nowMs = System.currentTimeMillis()
        while (snapshot?.state == Call.STATE_ACTIVE && snapshot.connectedAtMs != null) {
            delay(1_000)
            nowMs = System.currentTimeMillis()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PhoneCoreBrand(
                context = callDirectionLabel(snapshot?.direction),
                status = if (snapshot == null) InCallPresencePolicy.title(missingSession) else callStateLabel(snapshot.state),
                modifier = Modifier.fillMaxWidth()
            )

            // Caller identity and secondary panels may scroll, but call-critical controls stay
            // pinned below this region. This prevents answer/reject/hang-up controls from being
            // pushed off-screen on compact devices or when the dialpad/audio panels are open.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (snapshot != null) {
                    CallerHero(
                        snapshot = snapshot,
                        duration = callDurationLabel(snapshot, nowMs),
                        trustIndicator = trustIndicator
                    )
                }

                when {
                    snapshot == null -> {
                        MissingCallSession(missingSession, onRecover, onConfigure, onClose)
                    }
                    snapshot.state == Call.STATE_DISCONNECTING || snapshot.state == Call.STATE_DISCONNECTED -> {
                        Text(
                            "L’appel est terminé.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = onClose) { Text("Fermer l’écran d’appel") }
                    }
                    snapshot.state != Call.STATE_RINGING -> {
                        if (showAudioRoutes) {
                            AudioRoutePanel(snapshot)
                        }

                        if (showDialpad && snapshot.state == Call.STATE_ACTIVE) {
                            DialpadPanel(snapshot)
                        }

                        ConferencePanel(snapshot)
                    }
                }

                if (calls.size > 1) {
                    ActiveCallsPanel(calls)
                }
            }

            when {
                snapshot?.state == Call.STATE_RINGING -> IncomingActions(snapshot)
                snapshot != null &&
                    snapshot.state != Call.STATE_DISCONNECTING &&
                    snapshot.state != Call.STATE_DISCONNECTED -> {
                    OngoingPrimaryControls(
                        snapshot = snapshot,
                        showDialpad = showDialpad,
                        showAudioRoutes = showAudioRoutes,
                        onToggleDialpad = {
                            showDialpad = !showDialpad
                            if (showDialpad) showAudioRoutes = false
                        },
                        onToggleAudioRoutes = {
                            showAudioRoutes = !showAudioRoutes
                            if (showAudioRoutes) showDialpad = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MissingCallSession(
    state: InCallPresencePolicy.MissingSession,
    onRecover: () -> String,
    onConfigure: () -> Unit,
    onClose: () -> Unit
) {
    var actionStatus by remember { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Rounded.Call, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Text(InCallPresencePolicy.title(state), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                when (state) {
                    InCallPresencePolicy.MissingSession.CALL_UNAVAILABLE ->
                        "Un appel est bien détecté par Android, mais Sentinel n’a pas encore reçu les commandes de cet appel."
                    InCallPresencePolicy.MissingSession.CONNECTING ->
                        "Connexion à l’appel en cours…"
                    InCallPresencePolicy.MissingSession.ENDED ->
                        "L’appel est terminé. Vous pouvez fermer cet écran."
                    InCallPresencePolicy.MissingSession.IDLE ->
                        "Aucun appel n’est en cours."
                    InCallPresencePolicy.MissingSession.UNKNOWN ->
                        "Sentinel ne peut pas vérifier l’état de l’appel pour le moment. Vous pouvez relancer l’écran d’appel ou vérifier la configuration."
                },
                style = MaterialTheme.typography.bodyMedium
            )
            if (state == InCallPresencePolicy.MissingSession.CALL_UNAVAILABLE ||
                state == InCallPresencePolicy.MissingSession.UNKNOWN) {
                Button(onClick = { actionStatus = onRecover() }, modifier = Modifier.fillMaxWidth()) { Text("Revenir à l’appel Android") }
                OutlinedButton(onClick = onConfigure, modifier = Modifier.fillMaxWidth()) { Text("Vérifier la configuration") }
            }
            actionStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = onClose) { Text(if (state == InCallPresencePolicy.MissingSession.ENDED || state == InCallPresencePolicy.MissingSession.IDLE) "Terminer" else "Fermer") }
        }
    }
}

@Composable
private fun CallerHero(
    snapshot: SentinelInCallService.CallSnapshot?,
    duration: String?,
    trustIndicator: CallTrustIndicator.Result
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(callStateLabel(snapshot?.state), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Surface(Modifier.size(88.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)) {
            Box(contentAlignment = Alignment.Center) {
                val initial = callerInitial(snapshot)
                if (initial != null) Text(initial, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                else Icon(Icons.Rounded.Person, null, modifier = Modifier.size(44.dp))
            }
        }
        Text(callerTitle(snapshot), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        snapshot?.handle?.takeIf { it.isNotBlank() }?.let { handle ->
            Text(
                handle,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            PhoneCountryPrefixCatalog.resolveNumber(handle)?.let { zone ->
                Text(
                    listOf(zone.flag, zone.label, zone.prefix)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Zone d’indicatif uniquement · ne localise pas l’appelant",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        duration?.let { Text(it, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary) }
        PhoneCoreDisclosure(title = trustIndicator.title) {
            Text(trustIndicator.detail, style = MaterialTheme.typography.bodyMedium)
            Text("Analyse locale indicative · aucune garantie de fiabilité du numéro", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun IncomingActions(snapshot: SentinelInCallService.CallSnapshot) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        CallActionCircle(
            label = "Refuser",
            icon = Icons.Rounded.CallEnd,
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
            size = 78.dp
        ) { SentinelInCallService.reject(snapshot.id) }

        CallActionCircle(
            label = "Décrocher",
            icon = Icons.Rounded.Call,
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
            size = 78.dp
        ) { SentinelInCallService.answer(snapshot.id) }
    }
}

@Composable
private fun OngoingPrimaryControls(
    snapshot: SentinelInCallService.CallSnapshot,
    showDialpad: Boolean,
    showAudioRoutes: Boolean,
    onToggleDialpad: () -> Unit,
    onToggleAudioRoutes: () -> Unit
) {
    val activeOrHolding = snapshot.state == Call.STATE_ACTIVE || snapshot.state == Call.STATE_HOLDING
    val muted = snapshot.isMuted

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CallActionCircle(
                    label = if (muted == true) "Réactiver" else "Muet",
                    icon = if (muted == true) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    containerColor = if (muted == true) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    enabled = activeOrHolding && snapshot.canMute && muted != null,
                    selected = muted == true
                ) {
                    muted?.let { SentinelInCallService.setMicrophoneMuted(snapshot.id, !it) }
                }

                CallActionCircle(
                    label = "Audio",
                    icon = Icons.Rounded.VolumeUp,
                    containerColor = if (showAudioRoutes) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    enabled = activeOrHolding && snapshot.audioRoutes.isNotEmpty(),
                    selected = showAudioRoutes,
                    onClick = onToggleAudioRoutes
                )

                CallActionCircle(
                    label = "Clavier",
                    icon = Icons.Rounded.Dialpad,
                    containerColor = if (showDialpad) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    enabled = snapshot.state == Call.STATE_ACTIVE,
                    selected = showDialpad,
                    onClick = onToggleDialpad
                )

                when {
                    snapshot.state == Call.STATE_ACTIVE && snapshot.canHold -> {
                        CallActionCircle(
                            label = "Attente",
                            icon = Icons.Rounded.Pause,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ) { SentinelInCallService.hold(snapshot.id) }
                    }
                    snapshot.state == Call.STATE_HOLDING && snapshot.canHold -> {
                        CallActionCircle(
                            label = "Reprendre",
                            icon = Icons.Rounded.PlayArrow,
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selected = true
                        ) { SentinelInCallService.unhold(snapshot.id) }
                    }
                    else -> {
                        CallActionCircle(
                            label = "Attente",
                            icon = Icons.Rounded.Pause,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            enabled = false
                        ) {}
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 14.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CallActionCircle(
                    label = "Raccrocher",
                    icon = Icons.Rounded.CallEnd,
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    size = 78.dp
                ) { SentinelInCallService.disconnect(snapshot.id) }
            }
        }
    }
}

@Composable
private fun AudioRoutePanel(snapshot: SentinelInCallService.CallSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Sortie audio",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            snapshot.audioRoutes.forEach { route ->
                OutlinedButton(
                    onClick = { SentinelInCallService.selectAudioRoute(snapshot.id, route.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (route.selected) "✓ ${route.label}" else route.label)
                }
            }

            snapshot.audioStatus?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DialpadPanel(snapshot: SentinelInCallService.CallSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Clavier",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            DtmfPad(snapshot.id)
        }
    }
}

@Composable
private fun ConferencePanel(snapshot: SentinelInCallService.CallSnapshot) {
    if (!snapshot.canMergeConference && !snapshot.canSwapConference) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Conférence",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (snapshot.canMergeConference) {
                OutlinedButton(
                    onClick = { SentinelInCallService.mergeConference(snapshot.id) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Fusionner les appels") }
            }
            if (snapshot.canSwapConference) {
                OutlinedButton(
                    onClick = { SentinelInCallService.swapConference(snapshot.id) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Permuter les appels") }
            }
        }
    }
}

@Composable
private fun ActiveCallsPanel(calls: List<SentinelInCallService.CallSnapshot>) {
    Text(
        "Appels en cours",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth()
    )

    calls.forEach { call ->
        OutlinedCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        call.displayName?.takeIf { it.isNotBlank() }
                            ?: call.handle
                            ?: "Appel",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        callStateLabel(call.state),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                when {
                    call.state == Call.STATE_ACTIVE && call.canHold ->
                        TextButton(onClick = { SentinelInCallService.hold(call.id) }) {
                            Text("Attente")
                        }
                    call.state == Call.STATE_HOLDING && call.canHold ->
                        TextButton(onClick = { SentinelInCallService.unhold(call.id) }) {
                            Text("Reprendre")
                        }
                }

                TextButton(onClick = { SentinelInCallService.disconnect(call.id) }) {
                    Text("Raccrocher")
                }
            }
        }
    }
}

@Composable
private fun CallActionCircle(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    enabled: Boolean = true,
    selected: Boolean = false,
    size: androidx.compose.ui.unit.Dp = 62.dp,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(min = 62.dp)
    ) {
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(size)
                .semantics { contentDescription = label },
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(if (selected) 30.dp else 28.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            }
        )
    }
}

@Composable
private fun DtmfPad(callId: String) {
    val scope = rememberCoroutineScope()
    listOf("123", "456", "789", "*0#").forEach { row ->
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            row.forEach { digit ->
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            if (SentinelInCallService.startDtmf(callId, digit)) {
                                delay(DTMF_TONE_DURATION_MS)
                                SentinelInCallService.stopDtmf(callId)
                            }
                        }
                    },
                    modifier = Modifier
                        .size(64.dp)
                        .semantics {
                            contentDescription = when (digit) {
                                '*' -> "Étoile DTMF"
                                '#' -> "Dièse DTMF"
                                else -> "Chiffre DTMF $digit"
                            }
                        },
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                ) {
                    Text(digit.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun callerTitle(snapshot: SentinelInCallService.CallSnapshot?): String =
    snapshot?.displayName?.takeIf { it.isNotBlank() } ?: "Numéro non enregistré"

private fun callerInitial(snapshot: SentinelInCallService.CallSnapshot?): String? =
    snapshot?.displayName
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.firstOrNull()
        ?.uppercase()

private fun callDirectionLabel(direction: String?): String = when (direction) {
    "INCOMING" -> "Appel entrant"
    "OUTGOING" -> "Appel sortant"
    else -> "Appel"
}

private fun callDurationLabel(
    snapshot: SentinelInCallService.CallSnapshot?,
    nowMs: Long
): String? {
    if (snapshot?.state != Call.STATE_ACTIVE) return null
    val connectedAtMs = snapshot.connectedAtMs ?: return null
    if (connectedAtMs <= 0L || nowMs < connectedAtMs) return null

    val totalSeconds = (nowMs - connectedAtMs) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L

    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun callStateLabel(state: Int?): String = when (state) {
    Call.STATE_RINGING -> "Sonnerie"
    Call.STATE_DIALING -> "Composition…"
    Call.STATE_CONNECTING -> "Connexion…"
    Call.STATE_ACTIVE -> "En communication"
    Call.STATE_HOLDING -> "En attente"
    Call.STATE_DISCONNECTING -> "Fin de l’appel…"
    Call.STATE_DISCONNECTED -> "Appel terminé"
    else -> "État de l’appel"
}

private const val DTMF_TONE_DURATION_MS = 150L
