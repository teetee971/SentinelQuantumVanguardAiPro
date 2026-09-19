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
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.sentinel.quantum.security.SentinelSmsSender
import com.sentinel.quantum.security.SmsConversationStore
import com.sentinel.quantum.security.SmsLinkAnalyzer
import com.sentinel.quantum.security.LocalLogger
import com.sentinel.quantum.security.CallerIdentityResolver
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
                val smsAnalyzer = remember { SmsLinkAnalyzer(LocalLogger(applicationContext)) }
                var threads by remember {
                    mutableStateOf(
                        if (conversations.canRead()) conversations.recentThreads(50)
                        else emptyList()
                    )
                }
                var selectedThreadId by remember { mutableStateOf<Long?>(null) }
                var threadMessages by remember { mutableStateOf(emptyList<SmsConversationStore.Message>()) }

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
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color(0xFF17232D))
                        ) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("SMS SÉCURISÉ", color = Color(0xFF66C7FF), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text("Nouveau message", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                                Text(
                                    "Analyse locale et protection Sentinel. Aucun message n’est envoyé sans votre action.",
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
                                    threads = conversations.recentThreads(50)
                                    selectedThreadId?.let { threadMessages = conversations.messagesForThread(it, 100) }
                                    status = "Conversations actualisées"
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

                            if (selectedThreadId == null) {
                                threads.forEach { thread ->
                                    Card(
                                        Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(18.dp),
                                        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color(0xFF1A2631))
                                    ) {
                                        Column(
                                            Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(thread.address.ifBlank { "Inconnu" }, fontWeight = FontWeight.Bold)
                                            Text(
                                                DateFormat.getDateTimeInstance().format(Date(thread.latestTimestampMs)),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(thread.latestBody.take(240))
                                            Text(
                                                "${thread.messageCount} message(s)",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                            OutlinedButton(
                                                onClick = {
                                                    selectedThreadId = thread.threadId
                                                    threadMessages = conversations.messagesForThread(thread.threadId, 100)
                                                    destination = thread.address.take(32)
                                                    status = null
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Ouvrir la conversation")
                                            }
                                        }
                                    }
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        selectedThreadId = null
                                        threadMessages = emptyList()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Retour aux conversations")
                                }
                                threadMessages.forEach { message ->
                                    Card(
                                        Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(18.dp),
                                        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color(0xFF1A2631))
                                    ) {
                                        Column(
                                            Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(message.address.ifBlank { "Inconnu" })
                                            Text(
                                                DateFormat.getDateTimeInstance().format(Date(message.timestampMs)),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(message.body.take(1000))
                                            val identity = CallerIdentityResolver.resolve(
                                                message.address,
                                                verification = "Non vérifié"
                                            )
                                            Text(
                                                "${identity.countryFlag} ${identity.countryName} · ${identity.callType}",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                            val analysis = remember(message.id, message.body) {
                                                smsAnalyzer.analyze(message.body)
                                            }
                                            if (analysis.findings.isNotEmpty()) {
                                                Text(
                                                    "Analyse locale : risque ${analysis.riskLevel.name} · score ${analysis.score}/100 · ${analysis.findings.joinToString { it.code }}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            } else {
                                                Text(
                                                    "Analyse locale : aucun signal détecté par les règles actuelles",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    val deleted = conversations.deleteMessage(message.id)
                                                    status = if (deleted) {
                                                        selectedThreadId?.let {
                                                            threadMessages = conversations.messagesForThread(it, 100)
                                                        }
                                                        threads = conversations.recentThreads(50)
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
