package com.sentinel.quantum.ui.screens

import android.app.role.RoleManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.R
import com.sentinel.quantum.navigation.Screen
import com.sentinel.quantum.data.SettingsStore
import com.sentinel.quantum.security.CallerReputationClient
import com.sentinel.quantum.security.ArcepDirectoryClient
import com.sentinel.quantum.security.ExplainableAI
import com.sentinel.quantum.security.LocalLogger
import com.sentinel.quantum.security.PhoneMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneSecurityScreen(navController: NavController) {
    val context = LocalContext.current
    var phoneNumber by remember { mutableStateOf("") }
    var checkResult by remember { mutableStateOf<PhoneMonitor.SpamCheckResult?>(null) }
    var explanation by remember { mutableStateOf<ExplainableAI.Explanation?>(null) }
    var monitorStats by remember { mutableStateOf<PhoneMonitor.MonitorStats?>(null) }
    var remoteResult by remember { mutableStateOf<CallerReputationClient.Result?>(null) }
    var remoteStatus by remember { mutableStateOf<String?>(null) }
    var remoteRunning by remember { mutableStateOf(false) }
    var arcepResult by remember { mutableStateOf<ArcepDirectoryClient.Allocation?>(null) }
    var arcepStatus by remember { mutableStateOf<String?>(null) }
    var directoryRunning by remember { mutableStateOf(false) }

    val logger = remember { LocalLogger(context) }
    val phoneMonitor = remember { PhoneMonitor(logger) }
    val explainableAI = remember { ExplainableAI(logger) }
    val settingsStore = remember(context) { SettingsStore(context) }
    val remoteEnrichmentEnabled = remember { settingsStore.callerReputationEnrichmentEnabled }
    val scope = rememberCoroutineScope()
    val callScreeningActive = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java)
                .isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        } else false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.phone_security_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.phone_security_heading), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.phone_security_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("État de la protection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (callScreeningActive) "Filtrage d’appels Android : ACTIVÉ"
                        else "Filtrage d’appels Android : non activé"
                    )
                    Button(
                        onClick = { navController.navigate(Screen.CallBlocking.route) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Configurer le filtrage et Caller ID") }
                    Button(
                        onClick = { navController.navigate(Screen.DigitalExposure.route) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Contrôler l’exposition numérique") }
                    Button(
                        onClick = { navController.navigate(Screen.AppPermissionAnalyzer.route) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Analyser les permissions des applications") }
                    Button(
                        onClick = { navController.navigate(Screen.NetworkSurveillance.route) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Scanner l’environnement réseau local") }
                    Button(
                        onClick = { navController.navigate(Screen.SmsScanner.route) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Analyser un SMS ou un lien") }
                }
            }

            HorizontalDivider()
            Text("Identification d’appel / numéro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it.take(64) },
                label = { Text(stringResource(R.string.phone_security_label)) },
                placeholder = { Text(stringResource(R.string.phone_security_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = {
                    if (phoneNumber.isNotBlank()) {
                        checkResult = phoneMonitor.checkNumber(phoneNumber)
                        explanation = checkResult?.let(explainableAI::explainSpamCheck)
                        monitorStats = phoneMonitor.getStats()
                        directoryRunning = true
                        arcepResult = null
                        arcepStatus = "Recherche dans l’annuaire officiel…"
                        val candidate = phoneNumber
                        scope.launch {
                            val lookup = withContext(Dispatchers.IO) { runCatching { ArcepDirectoryClient().lookup(candidate) } }
                            directoryRunning = false
                            lookup.onSuccess {
                                arcepResult = it
                                arcepStatus = if (it != null) "Attribution ARCEP trouvée" else "Aucune attribution ARCEP correspondante"
                            }.onFailure { arcepStatus = "Annuaire ARCEP temporairement indisponible" }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = phoneNumber.isNotBlank()
            ) { Text(stringResource(R.string.phone_security_check)) }

            arcepStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            arcepResult?.let { allocation ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Attribution officielle ARCEP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        allocation.attributedOperator?.let { Text("Attributaire publié : $it") }
                        Text("Tranche : ${allocation.start} – ${allocation.end}")
                        allocation.territory?.let { Text("Territoire : $it") }
                        allocation.allocationDate?.let { Text("Date d’attribution : $it") }
                        allocation.businessIdentifier?.let { Text("SIREN/SIRET publié : $it") }
                        allocation.rcs?.let { Text("Registre : $it") }
                        allocation.address?.let { Text("Adresse publiée : $it") }
                        Text("Code opérateur : ${allocation.operatorCode}")
                        Text(
                            "Attribution de bloc uniquement : elle ne prouve ni l’opérateur actuel après portabilité, ni l’identité de l’appelant, ni une fraude.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (remoteEnrichmentEnabled) {
                Button(
                    onClick = {
                        val candidate = phoneNumber
                        if (candidate.isNotBlank()) {
                            remoteRunning = true
                            remoteResult = null
                            remoteStatus = "Enrichissement Sentinel en cours…"
                            scope.launch {
                                val checked = withContext(Dispatchers.IO) {
                                    runCatching {
                                        CallerReputationClient().evaluate(
                                            callerNumber = candidate,
                                            recipientCountry = Locale.getDefault().country.ifBlank { "FR" },
                                            verificationStatus = "UNKNOWN"
                                        )
                                    }
                                }
                                remoteRunning = false
                                checked.onSuccess {
                                    remoteResult = it
                                    remoteStatus = "Réputation distante reçue"
                                }.onFailure {
                                    remoteStatus = "Réputation distante indisponible"
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = phoneNumber.isNotBlank() && !remoteRunning
                ) {
                    Text(if (remoteRunning) "Enrichissement…" else "Enrichir avec Wangiri / réputation")
                }
            } else {
                Text(
                    "L’enrichissement distant est désactivé. Il peut être activé dans Blocage d’appels ; le numéro n’est jamais envoyé sans cet opt-in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            remoteStatus?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            remoteResult?.let { result ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Réputation Sentinel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Score indicatif : ${result.riskScore}/100")
                        Text("Action moteur : ${result.action}")
                        Text("Signalements communautaires : ${result.signals}")
                        Text("Intelligence communautaire : ${result.communityIntelligence}")
                        result.flags.forEach { flag -> Text("• $flag") }
                        Text(
                            "Ces signaux sont indicatifs et ne constituent pas une preuve d’identité ou de fraude.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            checkResult?.let { result ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.phone_security_result), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.phone_security_risk_level, result.riskLevel.name), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.phone_security_reason, result.reason))
                        Text(
                            stringResource(R.string.phone_security_disclaimer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            monitorStats?.let { stats ->
                Text(
                    stringResource(R.string.phone_security_stats, stats.totalChecks, stats.elevatedRiskChecks),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            explanation?.let { exp ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.phone_security_explanation), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(exp.summary, style = MaterialTheme.typography.bodyMedium)
                        if (exp.recommendations.isNotEmpty()) {
                            Text(stringResource(R.string.phone_security_recommendations), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            exp.recommendations.forEach { recommendation -> Text("• $recommendation") }
                        }
                    }
                }
            }
        }
    }
}
