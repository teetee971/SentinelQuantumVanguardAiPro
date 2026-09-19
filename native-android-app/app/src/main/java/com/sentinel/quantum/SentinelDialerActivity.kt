package com.sentinel.quantum

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.quantum.data.SettingsStore
import com.sentinel.quantum.security.ArcepDirectoryClient
import com.sentinel.quantum.security.CallerReputationClient
import com.sentinel.quantum.security.LocalContactLookup
import com.sentinel.quantum.security.RtrDirectoryClient
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sentinel-owned dial-pad surface. It intentionally delegates final call placement to ACTION_DIAL
 * until the user has explicitly selected a complete, validated Sentinel ROLE_DIALER implementation.
 */
@OptIn(ExperimentalMaterial3Api::class)
class SentinelDialerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SentinelQuantumTheme {
                var number by remember { mutableStateOf("") }
                var directoryStatus by remember { mutableStateOf("Saisissez un numéro pour l’identifier.") }
                var lookupRunning by remember { mutableStateOf(false) }
                var contactStatus by remember { mutableStateOf<String?>(null) }
                var reputationStatus by remember { mutableStateOf<String?>(null) }
                val context = this@SentinelDialerActivity
                val arcep = remember { ArcepDirectoryClient() }
                val rtr = remember { RtrDirectoryClient() }
                val contacts = remember { LocalContactLookup(context) }
                val settings = remember { SettingsStore(context) }
                val reputation = remember { CallerReputationClient() }
                val scope = rememberCoroutineScope()

                fun lookup() {
                    if (number.isBlank() || lookupRunning) return
                    lookupRunning = true
                    directoryStatus = "Recherche officielle…"
                    contactStatus = contacts.find(number)?.let { identity ->
                        "Contact : " + identity.displayName + (identity.organisation?.let { " · $it" } ?: "")
                    }
                    reputationStatus = if (settings.callerReputationEnrichmentEnabled) "Réputation Sentinel : analyse…" else null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                val at = RtrDirectoryClient.normalize(number)
                                if (at != null) {
                                    val r = rtr.lookup(number)
                                    when {
                                        r == null -> "Format autrichien non reconnu"
                                        r.status == "ambiguous" -> "RTR : attribution ambiguë — aucune identité déduite"
                                        r.matches.isNotEmpty() -> {
                                            val m = r.matches.first()
                                            "RTR : " + (m.allocationHolder ?: m.status) + (m.area?.let { " · $it" } ?: "")
                                        }
                                        else -> "RTR : " + r.status
                                    }
                                } else {
                                    val a = arcep.lookup(number)
                                    if (a == null) "ARCEP : aucune attribution correspondante"
                                    else "ARCEP : " + (a.attributedOperator ?: a.operatorCode) + (a.territory?.let { " · $it" } ?: "")
                                }
                            }.getOrElse { "Répertoire officiel temporairement indisponible" }
                        }
                        directoryStatus = result
                        if (settings.callerReputationEnrichmentEnabled) {
                            reputationStatus = withContext(Dispatchers.IO) {
                                runCatching {
                                    val r = reputation.evaluate(number, "FR", "outgoing_user_lookup")
                                    "Réputation Sentinel : risque ${r.riskScore}/100 · ${r.action}" +
                                        if (r.flags.isNotEmpty()) " · " + r.flags.take(3).joinToString(", ") else ""
                                }.getOrElse { "Réputation Sentinel temporairement indisponible" }
                            }
                        }
                        lookupRunning = false
                    }
                }

                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("SENTINEL", fontWeight = FontWeight.ExtraBold)
                                    Text("Appels protégés", style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                                }
                            }
                        )
                    }
                ) { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF17232D))
                        ) {
                            Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("IDENTIFICATION LOCALE", color = Color(0xFF66C7FF), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text(if (number.isBlank()) "—" else number, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                Spacer(Modifier.height(8.dp))
                                contactStatus?.let {
                                    Text(it, color = Color(0xFF32D6A0), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                    Spacer(Modifier.height(6.dp))
                                }
                                Text("Attribution officielle", color = Color(0xFF66C7FF), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text(directoryStatus, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                                reputationStatus?.let {
                                    Spacer(Modifier.height(6.dp))
                                    Text(it, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                                }
                                if (!settings.callerReputationEnrichmentEnabled) {
                                    Spacer(Modifier.height(4.dp))
                                    Text("Réputation distante désactivée dans les paramètres.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                }
                                TextButton(onClick = { lookup() }, enabled = number.isNotBlank() && !lookupRunning) {
                                    Text(if (lookupRunning) "Recherche…" else "Identifier le numéro")
                                }
                            }
                        }

                        val keys = listOf(
                            listOf("1", "2", "3"), listOf("4", "5", "6"),
                            listOf("7", "8", "9"), listOf("+", "0", "⌫")
                        )
                        keys.forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                row.forEach { key ->
                                    FilledTonalButton(
                                        onClick = {
                                            when (key) {
                                                "⌫" -> if (number.isNotEmpty()) number = number.dropLast(1)
                                                "+" -> if (number.isEmpty()) number = "+"
                                                else -> if (number.length < 32) number += key
                                            }
                                            directoryStatus = "Saisissez un numéro puis lancez l’identification."
                                            contactStatus = null
                                            reputationStatus = null
                                        },
                                        modifier = Modifier.size(72.dp),
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(0.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF1A2631))
                                    ) {
                                        if (key == "⌫") Icon(Icons.Default.Backspace, contentDescription = "Effacer")
                                        else Text(key, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (number.isNotBlank()) {
                                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))
                                }
                            },
                            enabled = number.isNotBlank(),
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = "Appeler", modifier = Modifier.size(30.dp))
                        }
                        Text(
                            "Sentinel prépare le numéro et l’analyse. Android garde le contrôle de l’appel final.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
