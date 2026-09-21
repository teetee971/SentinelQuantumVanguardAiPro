package com.sentinel.quantum

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme

/**
 * Single user-facing entry point for Sentinel communications.
 *
 * Only native Sentinel channels are actionable here initially. Third-party channels are shown
 * as staged capabilities until their validated link/account adapters are present on main.
 */
class CommunicationsHubActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SentinelQuantumTheme {
                Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Communications") }) }) { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Tous vos canaux, avec un état vérifiable.", style = MaterialTheme.typography.bodyMedium)
                        ChannelCard("Appels", "Disponible dans Sentinel") {
                            startActivity(Intent(this@CommunicationsHubActivity, SentinelDialerActivity::class.java))
                        }
                        ChannelCard("SMS / MMS", "Disponible dans Sentinel") {
                            startActivity(Intent(this@CommunicationsHubActivity, SmsComposeActivity::class.java))
                        }
                        ChannelCard("WhatsApp", "Action externe validée · ouverture explicite", null)
                        ChannelCard("Telegram", "En préparation", null)
                        ChannelCard("Instagram", "En préparation", null)
                        ChannelCard("Messenger", "En préparation", null)
                        ChannelCard("Signal", "Non raccordé", null)
                        ChannelCard("Discord", "Non raccordé", null)
                        ChannelCard("Teams", "Compte connecté requis", null)
                        ChannelCard("Slack", "Compte connecté requis", null)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelCard(name: String, status: String, onClick: (() -> Unit)?) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
            if (onClick != null) Button(onClick = onClick) { Text("Ouvrir") }
        }
    }
}
