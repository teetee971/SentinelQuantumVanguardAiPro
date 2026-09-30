package com.sentinel.quantum

import android.os.Bundle
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import com.sentinel.quantum.security.PhoneCorePhysicalValidation
import com.sentinel.quantum.security.PhonePrivateTimeline
import com.sentinel.quantum.security.PhonePrivateTimelineStore
import com.sentinel.quantum.security.SentinelInCallService
import com.sentinel.quantum.ui.design.PhoneCoreBrand
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Sentinel-owned bounded in-call surface for ROLE_DIALER. */
class SentinelInCallActivity : ComponentActivity() {
    private var proximityLock: PowerManager.WakeLock? = null

    override fun onResume() {
        super.onResume()
        val power = getSystemService(PowerManager::class.java)
        if (power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            proximityLock = power.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                packageName + ":incall-proximity"
            ).also {
                if (!it.isHeld) it.acquire()
            }
        }
    }

    override fun onPause() {
        proximityLock?.let { if (it.isHeld) it.release() }
        proximityLock = null
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val physicalTimeline = PhonePrivateTimelineStore(applicationContext)
        setContent {
            SentinelQuantumTheme {
                var snapshot by remember { mutableStateOf(SentinelInCallService.currentSnapshot()) }
                var calls by remember { mutableStateOf(SentinelInCallService.currentSnapshots()) }
                var uiEvidenceRecorded by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    while (true) {
                        val current = SentinelInCallService.currentSnapshot()
                        snapshot = current
                        calls = SentinelInCallService.currentSnapshots()
                        if (
                            !uiEvidenceRecorded &&
                            current != null &&
                            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                        ) {
                            val stored = physicalTimeline.append(
                                PhonePrivateTimeline.Event(
                                    kind = PhonePrivateTimeline.Kind.CALL,
                                    timestampMs = System.currentTimeMillis(),
                                    direction = "LOCAL",
                                    signal = PhoneCorePhysicalValidation.SIGNAL_INCALL_UI_SHOWN
                                )
                            )
                            if (stored) uiEvidenceRecorded = true
                        }
                        delay(250)
                    }
                }

                InCallScreen(snapshot = snapshot, calls = calls, onClose = ::finish)
            }
        }
    }
}

@Composable
private fun InCallScreen(
    snapshot: SentinelInCallService.CallSnapshot?,
    calls: List<SentinelInCallService.CallSnapshot>,
    onClose: () -> Unit
) {
    var showDialpad by rememberSaveable { mutableStateOf(false) }
    var showAudioRoutes by rememberSaveable { mutableStateOf(false) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PhoneCoreBrand(
                context = callDirectionLabel(snapshot?.direction),
                status = callStateLabel(snapshot?.state),
                modifier = Modifier.fillMaxWidth()
            )

            CallerHero(
                snapshot = snapshot,
                duration = callDurationLabel(snapshot, nowMs)
            )

            when {
                snapshot?.state == Call.STATE_RINGING -> IncomingActions()
                snapshot == null -> {
                    Text(
                        "Aucun appel actif",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = onClose) { Text("Fermer") }
                }
                snapshot.state == Call.STATE_DISCONNECTING || snapshot.state == Call.STATE_DISCONNECTED -> {
                    Text(
                        "L’appel est terminé.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = onClose) { Text("Fermer l’écran d’appel") }
                }
                else -> {
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

                    if (showAudioRoutes) {
                        AudioRoutePanel(snapshot)
                    }

                    if (showDialpad && snapshot.state == Call.STATE_ACTIVE) {
                        DialpadPanel()
                    }

                    ConferencePanel(snapshot)
                }
            }

            if (calls.size > 1) {
                ActiveCallsPanel(calls)
            }
        }
    }
}

@Composable
private fun CallerHero(
    snapshot: SentinelInCallService.CallSnapshot?,
    duration: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Surface(
                modifier = Modifier.size(104.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val initial = callerInitial(snapshot)
                    if (initial != null) {
                        Text(
                            initial,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Text(
                callerTitle(snapshot),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            snapshot?.handle?.takeIf { it.isNotBlank() }?.let { handle ->
                Text(
                    handle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Text(
                    listOfNotNull(
                        callDirectionLabel(snapshot?.direction),
                        callStateLabel(snapshot?.state),
                        duration
                    ).joinToString(" · "),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                "Téléphonie Android · traitement local",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun IncomingActions() {
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
        ) { SentinelInCallService.reject() }

        CallActionCircle(
            label = "Décrocher",
            icon = Icons.Rounded.Call,
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
            size = 78.dp
        ) { SentinelInCallService.answer() }
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
                    label = if (muted == true) "Micro activé" else "Muet",
                    icon = if (muted == true) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    containerColor = if (muted == true || false) {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    enabled = activeOrHolding && snapshot.canMute && muted != null
                ) {
                    muted?.let { SentinelInCallService.setMicrophoneMuted(!it) }
                }

                CallActionCircle(
                    label = "Audio",
                    icon = Icons.Rounded.VolumeUp,
                    containerColor = if (showAudioRoutes) {
                        MaterialTheme.colorScheme.primaryContainer
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
                        MaterialTheme.colorScheme.primaryContainer
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
                        ) { SentinelInCallService.hold() }
                    }
                    snapshot.state == Call.STATE_HOLDING && snapshot.canHold -> {
                        CallActionCircle(
                            label = "Reprendre",
                            icon = Icons.Rounded.PlayArrow,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selected = true
                        ) { SentinelInCallService.unhold() }
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
                ) { SentinelInCallService.disconnect() }
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
                    onClick = { SentinelInCallService.selectAudioRoute(route.id) },
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
private fun DialpadPanel() {
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
            DtmfPad()
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
    size: androidx.compose.ui.unit.Dp = 66.dp,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(min = 72.dp)
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
private fun DtmfPad() {
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
                            if (SentinelInCallService.startDtmf(digit)) {
                                delay(DTMF_TONE_DURATION_MS)
                                SentinelInCallService.stopDtmf()
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
