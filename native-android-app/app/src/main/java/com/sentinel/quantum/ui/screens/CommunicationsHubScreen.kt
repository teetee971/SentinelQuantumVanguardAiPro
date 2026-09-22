package com.sentinel.quantum.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.SentinelDialerActivity
import com.sentinel.quantum.SmsComposeActivity
import com.sentinel.quantum.PhoneCoreActivationActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunicationsHubScreen(navController: NavController) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Communications") },
                navigationIcon = { TextButton(onClick = { navController.popBackStack() }) { Text("Retour") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Canaux Sentinel et connexions externes avec état explicite.")
            ChannelStatus("Phone Core", "Activation et test des rôles Téléphone / Filtrage / SMS") {
                context.startActivity(Intent(context, PhoneCoreActivationActivity::class.java))
            }
            ChannelStatus("Appels", "Composeur et interface d’appel présents · rôle Téléphone requis") {
                context.startActivity(Intent(context, SentinelDialerActivity::class.java))
            }
            ChannelStatus("SMS / MMS", "Envoi/réception SMS présents · rôle SMS requis · pièces jointes MMS encore en validation") {
                context.startActivity(Intent(context, SmsComposeActivity::class.java))
            }
            ChannelStatus("WhatsApp", "Non raccordé")
            ChannelStatus("Telegram", "Non raccordé")
            ChannelStatus("Instagram", "Non raccordé")
            ChannelStatus("Messenger", "Non raccordé")
            ChannelStatus("Signal", "Non raccordé")
            ChannelStatus("Discord", "Non raccordé")
            ChannelStatus("Teams", "Non raccordé · compte requis")
            ChannelStatus("Slack", "Non raccordé · compte requis")
            Text(
                "Aucun message, contact ou contenu tiers n’est importé ou transmis par cet écran.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ChannelStatus(name: String, status: String, onClick: (() -> Unit)? = null) {
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
