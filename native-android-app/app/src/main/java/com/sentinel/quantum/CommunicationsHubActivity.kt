package com.sentinel.quantum

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme

@OptIn(ExperimentalMaterial3Api::class)
class CommunicationsHubActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SentinelQuantumTheme {
                Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Communications") }) }) { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).padding(20.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Canaux Sentinel et connexions externes avec état explicite.")
                        ChannelCard("Appels", "Disponible dans Sentinel") {
                            startActivity(Intent(this@CommunicationsHubActivity, SentinelDialerActivity::class.java))
                        }
                        ChannelCard("SMS / MMS", "Disponible dans Sentinel") {
                            startActivity(Intent(this@CommunicationsHubActivity, SmsComposeActivity::class.java))
                        }
                        ChannelCard("WhatsApp", "Action externe disponible depuis une conversation", null)
                        ChannelCard("Telegram", "Identifiant public requis", null)
                        ChannelCard("Instagram", "Identifiant public requis", null)
                        ChannelCard("Messenger", "Identifiant public requis", null)
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

@androidx.compose.runtime.Composable
private fun ChannelCard(name: String, status: String, onClick: (() -> Unit)?) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
            if (onClick != null) Button(onClick = onClick) { Text("Ouvrir") }
        }
    }
}
