package com.sentinel.quantum

import android.os.Bundle
import android.telecom.Call
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sentinel.quantum.security.SentinelInCallService
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Sentinel-owned bounded in-call surface for ROLE_DIALER. */
class SentinelInCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SentinelQuantumTheme {
                var snapshot by remember { mutableStateOf(SentinelInCallService.currentSnapshot()) }
                LaunchedEffect(Unit) {
                    while (true) {
                        snapshot = SentinelInCallService.currentSnapshot()
                        delay(250)
                    }
                }
                InCallScreen(snapshot = snapshot, onClose = ::finish)
            }
        }
    }
}

@Composable
private fun InCallScreen(
    snapshot: SentinelInCallService.CallSnapshot?,
    onClose: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("SENTINEL", fontWeight = FontWeight.ExtraBold)
        Text(snapshot?.displayName ?: "Appel", style = MaterialTheme.typography.headlineMedium)
        Text(snapshot?.handle ?: "Numéro indisponible")
        Text(callStateLabel(snapshot?.state))

        if (snapshot?.state == Call.STATE_RINGING) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { SentinelInCallService.answer() }) { Text("Décrocher") }
                OutlinedButton(onClick = { SentinelInCallService.reject() }) { Text("Refuser") }
            }
        } else if (snapshot != null) {
            Button(onClick = { SentinelInCallService.disconnect() }) { Text("Raccrocher") }
            DtmfPad()
        } else {
            Text("Aucun appel actif")
            OutlinedButton(onClick = onClose) { Text("Fermer") }
        }
    }
}

@Composable
private fun DtmfPad() {
    listOf("123", "456", "789", "*0#").forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { digit ->
                val scope = rememberCoroutineScope()
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            if (SentinelInCallService.startDtmf(digit)) {
                                delay(DTMF_TONE_DURATION_MS)
                                SentinelInCallService.stopDtmf()
                            }
                        }
                    }
                ) { Text(digit.toString()) }
            }
        }
    }
}

private fun callStateLabel(state: Int?): String = when (state) {
    Call.STATE_RINGING -> "Appel entrant"
    Call.STATE_DIALING -> "Composition…"
    Call.STATE_CONNECTING -> "Connexion…"
    Call.STATE_ACTIVE -> "En communication"
    Call.STATE_HOLDING -> "En attente"
    Call.STATE_DISCONNECTED -> "Appel terminé"
    else -> "État de l’appel"
}

private const val DTMF_TONE_DURATION_MS = 150L
