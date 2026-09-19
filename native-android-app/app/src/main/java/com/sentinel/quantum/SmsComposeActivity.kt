package com.sentinel.quantum

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sentinel.quantum.security.SentinelSmsSender
import com.sentinel.quantum.security.SmsConversationStore
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme
import java.text.DateFormat
import java.util.Date

/**
 * SENDTO composer and staged conversation surface for the future default-SMS role.
 *
 * Sending, reading, exporting and deleting remain fail-closed unless Android confirms that
 * Sentinel is the default SMS handler and the corresponding runtime permission is granted.
 */
@OptIn(ExperimentalMaterial3Api::class)
class SmsComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialDestination = intent?.data?.schemeSpecificPart.orEmpty()
            .substringBefore('?')
            .take(32)
        val initialBody = intent?.getStringExtra("sms_body")
            .orEmpty()
            .take(SentinelSmsSender.MAX_BODY_CHARS)

        setContent {
            SentinelQuantumTheme {
                var destination by remember { mutableStateOf(initialDestination) }
                var body by remember { mutableStateOf(initialBody) }
                var status by remember { mutableStateOf<String?>(null) }
                val sender = remember { SentinelSmsSender(applicationContext) }
                val conversations = remember { SmsConversationStore(applicationContext) }
                var recent by remember {
                    mutableStateOf(
                        if (conversations.canRead()) conversations.recentMessages(50)
                        else emptyList()
                    )
                }

                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Column {
                                    Text("Messages Sentinel", fontWeight = FontWeight.Bold)
                                    Text("SMS protégé", style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                                }
                            }
                        )
                    }
                ) { scaffoldPadding ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(scaffoldPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Nouveau message", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(
                                    "Composez ici sans quitter Sentinel. L’envoi reste soumis au rôle SMS Android et aux permissions utilisateur.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        OutlinedTextField(
                            value = destination,
                            onValueChange = { destination = it.take(32) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Destinataire") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = body,
                            onValueChange = { body = it.take(SentinelSmsSender.MAX_BODY_CHARS) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Message") },
                            minLines = 6
                        )
                        Button(
                            onClick = {
                                val result = sender.send(destination, body)
                                status = when (result.reason) {
                                    "SUBMITTED_TO_ANDROID_TELEPHONY" -> "Message remis au système radio."
                                    "SMS_SUBSCRIPTION_REQUIRED" -> "Choisissez une SIM SMS par défaut dans les réglages Android."
                                    "EMERGENCY_NUMBER_USE_DIALER" -> "Numéro d’urgence détecté : utilisez le composeur téléphonique."
                                    "SMS_ROLE_NOT_HELD" -> "Sentinel n’est pas l’application SMS par défaut."
                                    "SEND_SMS_PERMISSION_NOT_GRANTED" -> "Permission d’envoi SMS non accordée."
                                    "INVALID_MESSAGE" -> "Destinataire ou message invalide."
                                    else -> "Échec d’envoi."
                                }
                                if (result.accepted) body = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = destination.isNotBlank() && body.isNotBlank()
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Envoyer")
                        }

                        if (!sender.holdsSmsRole()) {
                            Text(
                                "Envoi, lecture et export restent verrouillés tant que Sentinel n’est pas l’application SMS par défaut choisie par l’utilisateur.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        if (conversations.canRead()) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    "Conversations récentes",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    recent = conversations.recentMessages(50)
                                    status = "Messages actualisés"
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Actualiser")
                            }

                            OutlinedButton(
                                onClick = {
                                    val exported = conversations.exportRecentMessages(100)
                                    if (exported == null) {
                                        status = "Aucun message exportable"
                                    } else {
                                        val share = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/json"
                                            putExtra(Intent.EXTRA_STREAM, exported.uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        startActivity(Intent.createChooser(share, "Exporter les messages"))
                                        status = "Export préparé : ${exported.messageCount} messages"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Exporter jusqu’à 100 messages")
                            }

                            recent.forEach { message ->
                                Card(Modifier.fillMaxWidth()) {
                                    Column(
                                        Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(message.address.ifBlank { "Inconnu" })
                                        Text(
                                            DateFormat.getDateTimeInstance()
                                                .format(Date(message.timestampMs)),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(message.body.take(1000))
                                        OutlinedButton(
                                            onClick = {
                                                val deleted = conversations.deleteMessage(message.id)
                                                status = if (deleted) {
                                                    recent = conversations.recentMessages(50)
                                                    "Message supprimé"
                                                } else {
                                                    "Suppression refusée ou impossible"
                                                }
                                            }
                                        ) {
                                            Text("Supprimer ce message")
                                        }
                                    }
                                }
                            }
                        }

                        status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

                        Text(
                            "Les exports sont créés dans un cache privé temporaire et ne sont partagés qu’après votre action explicite.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
