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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import com.sentinel.quantum.security.PhoneCorePhysicalValidation
import com.sentinel.quantum.security.PhonePrivateTimeline
import com.sentinel.quantum.security.PhonePrivateTimelineStore
import com.sentinel.quantum.security.SentinelInCallService
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
            proximityLock = power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, packageName + ":incall-proximity").also {
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
                        if (!uiEvidenceRecorded && current != null && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
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
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "SENTINEL",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                callStateLabel(snapshot?.state),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(92.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                callerInitial(snapshot),
                                fontSize = 34.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        snapshot?.displayName?.takeIf { it.isNotBlank() } ?: "Appel",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        snapshot?.handle ?: "Numéro indisponible",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        if (snapshot?.state == Call.STATE_RINGING) "Appel entrant protégé par Sentinel" else "Téléphonie Android · contrôle local",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (calls.size > 1) {
                Text("Appels en cours", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                calls.forEach { call ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(call.displayName?.takeIf { it.isNotBlank() } ?: call.handle ?: "Appel")
                                Text(callStateLabel(call.state), style = MaterialTheme.typography.bodySmall)
                            }
                            when {
                                call.state == Call.STATE_ACTIVE && call.canHold ->
                                    TextButton(onClick = { SentinelInCallService.hold(call.id) }) { Text("Attente") }
                                call.state == Call.STATE_HOLDING && call.canHold ->
                                    TextButton(onClick = { SentinelInCallService.unhold(call.id) }) { Text("Reprendre") }
                            }
                            TextButton(onClick = { SentinelInCallService.disconnect(call.id) }) { Text("Raccrocher") }
                        }
                    }
                }
            }

            when {
                snapshot?.state == Call.STATE_RINGING -> IncomingActions()
                snapshot == null -> {
                    Text("Aucun appel actif", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onClose) { Text("Fermer") }
                }
                snapshot.state == Call.STATE_DISCONNECTING || snapshot.state == Call.STATE_DISCONNECTED -> {
                    Text("L’appel est terminé.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onClose) { Text("Fermer l’écran d’appel") }
                }
                else -> OngoingActions(snapshot)
            }
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
            symbol = "✕",
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ) { SentinelInCallService.reject() }
        CallActionCircle(
            label = "Décrocher",
            symbol = "✓",
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary
        ) { SentinelInCallService.answer() }
    }
}

@Composable
private fun OngoingActions(snapshot: SentinelInCallService.CallSnapshot) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        when {
            snapshot.state == Call.STATE_ACTIVE && snapshot.canHold -> {
                CallActionCircle(
                    label = "Attente",
                    symbol = "Ⅱ",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) { SentinelInCallService.hold() }
            }
            snapshot.state == Call.STATE_HOLDING && snapshot.canHold -> {
                CallActionCircle(
                    label = "Reprendre",
                    symbol = "▶",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) { SentinelInCallService.unhold() }
            }
        }
        CallActionCircle(
            label = "Raccrocher",
            symbol = "✕",
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ) { SentinelInCallService.disconnect() }
    }

    if (snapshot.canMergeConference || snapshot.canSwapConference) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Conférence", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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

    if (snapshot.state == Call.STATE_ACTIVE || snapshot.state == Call.STATE_HOLDING) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Audio de l’appel",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    val muted = snapshot.isMuted
                    CallActionCircle(
                        label = when {
                            !snapshot.canMute -> "Micro non modifiable sur cet appel"
                            muted == true -> "Réactiver le micro"
                            muted == false -> "Couper le micro"
                            else -> "État du micro en attente"
                        },
                        symbol = "M",
                        containerColor = if (muted == true) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        enabled = snapshot.canMute && muted != null
                    ) {
                        if (snapshot.canMute) {
                            muted?.let { SentinelInCallService.setMicrophoneMuted(!it) }
                        }
                    }
                }

                Text(
                    "Sortie audio",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (snapshot.audioRoutes.isEmpty()) {
                    Text(
                        "Sorties audio en attente d’Android…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    snapshot.audioRoutes.forEach { route ->
                        OutlinedButton(
                            onClick = { SentinelInCallService.selectAudioRoute(route.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (route.selected) "✓ " + route.label else route.label)
                        }
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

    if (snapshot.state == Call.STATE_ACTIVE) {
        Text(
            "Clavier DTMF",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        DtmfPad()
    }
}

@Composable
private fun CallActionCircle(
    label: String,
    symbol: String,
    containerColor: Color,
    contentColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(72.dp).semantics { contentDescription = label },
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor
            )
        ) {
            Text(symbol, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun DtmfPad() {
    listOf("123", "456", "789", "*0#").forEach { row ->
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            row.forEach { digit ->
                val scope = rememberCoroutineScope()
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            if (SentinelInCallService.startDtmf(digit)) {
                                delay(DTMF_TONE_DURATION_MS)
                                SentinelInCallService.stopDtmf()
                            }
                        }
                    },
                    modifier = Modifier.size(64.dp).semantics {
                        contentDescription = when (digit) {
                            '*' -> "Étoile DTMF"
                            '#' -> "Dièse DTMF"
                            else -> "Chiffre DTMF $digit"
                        }
                    },
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Text(digit.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun callerInitial(snapshot: SentinelInCallService.CallSnapshot?): String {
    val label = snapshot?.displayName?.takeIf { it.isNotBlank() }
        ?: snapshot?.handle?.takeIf { it.isNotBlank() }
        ?: "S"
    return label.trim().firstOrNull()?.uppercase() ?: "S"
}

private fun callStateLabel(state: Int?): String = when (state) {
    Call.STATE_RINGING -> "Appel entrant"
    Call.STATE_DIALING -> "Composition…"
    Call.STATE_CONNECTING -> "Connexion…"
    Call.STATE_ACTIVE -> "En communication"
    Call.STATE_HOLDING -> "En attente"
    Call.STATE_DISCONNECTING -> "Fin de l’appel…"
    Call.STATE_DISCONNECTED -> "Appel terminé"
    else -> "État de l’appel"
}

private const val DTMF_TONE_DURATION_MS = 150L
