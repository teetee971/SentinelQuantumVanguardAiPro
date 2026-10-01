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
import com.sentinel.quantum.ui.design.SentinelD1
import com.sentinel.quantum.ui.design.SentinelHero
import com.sentinel.quantum.ui.design.SentinelSectionHeader
import com.sentinel.quantum.ui.design.SentinelTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunicationsHubScreen(navController: NavController) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            SentinelTopBar(
                title = "Communications",
                subtitle = "Téléphone, SMS/MMS & canaux externes",
                onBack = { navController.popBackStack() }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SentinelHero(
                eyebrow = "Communications",
                title = "Canaux Sentinel",
                body = "Téléphonie et messagerie locales, avec connexions externes séparées et état explicite. Un canal non raccordé reste affiché comme non raccordé.",
                badges = listOf(
                    "Local" to SentinelD1.Success,
                    "État explicite" to SentinelD1.Cyan
                )
            )
            SentinelSectionHeader(
                title = "Actions essentielles",
                subtitle = "Téléphoner, écrire et terminer l’activation sans chercher dans les réglages."
            )
            ChannelStatus("Phone Core", "Activation et test des rôles Téléphone / Filtrage / SMS") {
                context.startActivity(Intent(context, PhoneCoreActivationActivity::class.java))
            }
            ChannelStatus("Appels", "Composeur et interface d’appel présents · rôle Téléphone requis") {
                context.startActivity(Intent(context, SentinelDialerActivity::class.java))
            }
            ChannelStatus("SMS / MMS", "Envoi/réception SMS et prise en charge MMS présents · validation physique MMS encore requise") {
                context.startActivity(Intent(context, SmsComposeActivity::class.java))
            }
            SentinelSectionHeader(
                title = "Canaux externes",
                subtitle = "Intégrations externes séparées des communications Sentinel."
            )
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
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = SentinelD1.Card)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
            if (onClick != null) Button(onClick = onClick) { Text("Ouvrir") }
        }
    }
}
