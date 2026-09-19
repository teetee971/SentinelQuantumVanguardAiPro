package com.sentinel.quantum

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Message
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
import com.sentinel.quantum.security.PhonePrivacyFirewall
import com.sentinel.quantum.security.ProtectionModePolicy
import com.sentinel.quantum.security.RtrDirectoryClient
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sentinel-owned dial-pad surface. Direct PSTN placement is fail-closed behind explicit
 * ROLE_DIALER ownership and CALL_PHONE permission; otherwise Sentinel does not place the call.
 */
@OptIn(ExperimentalMaterial3Api::class)
class SentinelDialerActivity : ComponentActivity() {
    private var pendingNumber: String? = null

    private val callPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pendingNumber?.let(::placeCallIfReady)
        pendingNumber = null
    }

    private val dialerRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        pendingNumber?.let(::placeCallIfReady)
    }

    private fun holdsDialerRole(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = getSystemService(RoleManager::class.java)
            roles.isRoleAvailable(RoleManager.ROLE_DIALER) && roles.isRoleHeld(RoleManager.ROLE_DIALER)
        } else {
            getSystemService(TelecomManager::class.java).defaultDialerPackage == packageName
        }
    }

    private fun requestDialerRole(number: String) {
        pendingNumber = number
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = getSystemService(RoleManager::class.java)
            if (roles.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                dialerRoleLauncher.launch(roles.createRequestRoleIntent(RoleManager.ROLE_DIALER))
            }
        } else {
            dialerRoleLauncher.launch(
                Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).putExtra(
                    TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName
                )
            )
        }
    }

    private fun placeCallIfReady(number: String) {
        if (!holdsDialerRole()) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            pendingNumber = number
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
            return
        }
        val telecom = getSystemService(TelecomManager::class.java)
        telecom.placeCall(Uri.parse("tel:" + Uri.encode(number)), Bundle())
    }

    private fun initialDialNumber(): String {
        if (intent?.action != Intent.ACTION_DIAL) return ""
        val uri = intent?.data ?: return ""
        if (!uri.scheme.equals("tel", ignoreCase = true)) return ""
        return uri.schemeSpecificPart.orEmpty().filter { it.isDigit() || it in "+*#" }.take(32)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SentinelQuantumTheme {
                var number by remember { mutableStateOf(initialDialNumber()) }
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
                    val remoteReputationAllowed = settings.callerReputationEnrichmentEnabled &&
                        ProtectionModePolicy.permitsCallerNumberEnrichment(settings.protectionMode)
                    reputationStatus = if (remoteReputationAllowed) "Réputation Sentinel : analyse…" else null
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
                        if (remoteReputationAllowed) {
                            reputationStatus = withContext(Dispatchers.IO) {
                                runCatching {
                                    val r = reputation.evaluate(
                                        callerNumber = number,
                                        recipientCountry = "FR",
                                        verificationStatus = "outgoing_user_lookup",
                                        privacyMode = PhonePrivacyFirewall.Mode.ENHANCED,
                                        explicitConsent = settings.callerReputationEnrichmentEnabled
                                    )
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
                                if (!settings.callerReputationEnrichmentEnabled ||
                                    !ProtectionModePolicy.permitsCallerNumberEnrichment(settings.protectionMode)) {
                                    Spacer(Modifier.height(4.dp))
                                    Text("Réputation distante désactivée dans les paramètres.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                }
                                TextButton(onClick = { lookup() }, enabled = number.isNotBlank() && !lookupRunning) {
                                    Text(if (lookupRunning) "Recherche…" else "Identifier le numéro")
                                }
                            }
                        }

                        if (number.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    val safe = number.filter { it.isDigit() || it in "+*#" }.take(32)
                                    if (safe.isNotBlank()) {
                                        startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(safe))).setPackage(packageName))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Message, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Envoyer un SMS avec Sentinel")
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
                                    if (holdsDialerRole()) {
                                        placeCallIfReady(number)
                                    } else {
                                        requestDialerRole(number)
                                    }
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
                            "Sentinel demande explicitement le rôle Téléphone avant de placer directement l’appel. Sans ce rôle, aucun appel direct n’est lancé.",
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
