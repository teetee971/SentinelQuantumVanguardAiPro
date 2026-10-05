package com.sentinel.quantum.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.sentinel.quantum.R
import com.sentinel.quantum.PhoneCoreActivationActivity
import com.sentinel.quantum.PhoneCoreRuntimeFacts
import com.sentinel.quantum.PhoneCoreSetupWizardStore
import com.sentinel.quantum.SentinelDialerActivity
import com.sentinel.quantum.SmsComposeActivity
import com.sentinel.quantum.navigation.Screen
import com.sentinel.quantum.data.SettingsStore
import com.sentinel.quantum.security.AndroidPhoneNumberCanonicalizer
import com.sentinel.quantum.security.CallerReputationClient
import com.sentinel.quantum.security.CallBlocklistStore
import com.sentinel.quantum.security.ArcepDirectoryClient
import com.sentinel.quantum.security.RtrDirectoryClient
import com.sentinel.quantum.security.ExplainableAI
import com.sentinel.quantum.security.GlobalPhoneIdentityPolicy
import com.sentinel.quantum.security.LocalLogger
import com.sentinel.quantum.security.PhoneDirectoryRoutingPolicy
import com.sentinel.quantum.security.PhoneMonitor
import com.sentinel.quantum.security.PhoneCoreFrenchLabels
import com.sentinel.quantum.security.PhoneRegionRuntimeCache
import com.sentinel.quantum.security.PhoneRiskCard
import com.sentinel.quantum.security.ProtectionModePolicy
import com.sentinel.quantum.security.PhonePrivacyFirewall
import com.sentinel.quantum.ui.design.SentinelTopBar
import com.sentinel.quantum.ui.design.SentinelSectionHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var rtrResult by remember { mutableStateOf<RtrDirectoryClient.Result?>(null) }
    var pendingBlockConfirmation by remember { mutableStateOf(false) }
    var actionStatus by remember { mutableStateOf<String?>(null) }
    var postureEpoch by remember { mutableStateOf(0) }
    val hostActivity = context as? ComponentActivity
    DisposableEffect(hostActivity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) postureEpoch++
        }
        hostActivity?.lifecycle?.addObserver(observer)
        onDispose { hostActivity?.lifecycle?.removeObserver(observer) }
    }

    val logger = remember { LocalLogger(context) }
    val phoneMonitor = remember { PhoneMonitor(logger) }
    val callBlocklistStore = remember(context) { CallBlocklistStore(context) }
    val explainableAI = remember { ExplainableAI(logger) }
    val settingsStore = remember(context) { SettingsStore(context) }
    val remoteEnrichmentEnabled = remember(postureEpoch) {
        settingsStore.callerReputationEnrichmentEnabled &&
            ProtectionModePolicy.permitsCallerNumberEnrichment(settingsStore.protectionMode)
    }
    val scope = rememberCoroutineScope()
    val phoneCoreFacts = remember(context, postureEpoch) {
        PhoneCoreRuntimeFacts.read(context.applicationContext)
    }
    val phoneCoreReady = remember(phoneCoreFacts) {
        PhoneCoreSetupWizardStore.softwarePrerequisitesReady(phoneCoreFacts)
    }
    val callPrerequisitesReady = remember(phoneCoreFacts) {
        phoneCoreFacts.corePermissionsReady &&
            phoneCoreFacts.dialerRoleHeld &&
            phoneCoreFacts.callScreeningRoleHeld &&
            phoneCoreFacts.callLogPermissionGranted
    }
    val messagePrerequisitesReady = remember(phoneCoreFacts) {
        phoneCoreFacts.smsRoleHeld &&
            phoneCoreFacts.smsRuntimePermissionsReady &&
            phoneCoreFacts.mmsPermissionsReady
    }

    Scaffold(
        topBar = {
            SentinelTopBar(
                title = stringResource(R.string.phone_security_title),
                subtitle = "Posture locale & Phone Core",
                onBack = { navController.navigateUp() }
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
            ProtectionHero(
                phoneCoreReady = phoneCoreReady,
                callReady = callPrerequisitesReady,
                smsReady = messagePrerequisitesReady
            )

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text("État réel de la protection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Les indicateurs ci-dessous reflètent uniquement les rôles et prérequis réellement observés.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProtectionStatusChip(
                            label = "Appels",
                            ready = callPrerequisitesReady,
                            modifier = Modifier.weight(1f)
                        )
                        ProtectionStatusChip(
                            label = "SMS",
                            ready = messagePrerequisitesReady,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        if (callPrerequisitesReady) "Prérequis appels Android prêts."
                        else "Prérequis appels Android à configurer.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        if (messagePrerequisitesReady) "Prérequis SMS/MMS Android prêts."
                        else "Prérequis SMS/MMS Android à configurer.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = { context.startActivity(Intent(context, PhoneCoreActivationActivity::class.java)) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Vérifier tous les prérequis de téléphonie") }
                    OutlinedButton(
                        onClick = { navController.navigate(Screen.CallBlocking.route) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Configurer le filtrage et l’identification d’appel") }
                    OutlinedButton(
                        onClick = { navController.navigate(Screen.PhoneLists.route) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Listes de protection") }
                }
            }

            SentinelSectionHeader(
                title = "Fonctionnalités de protection",
                subtitle = "Accès direct aux contrôles locaux sans masquer les prérequis manquants."
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { navController.navigate(Screen.DigitalExposure.route) },
                    modifier = Modifier.weight(1f)
                ) { Text("Exposition numérique") }
                OutlinedButton(
                    onClick = { navController.navigate(Screen.AppPermissionAnalyzer.route) },
                    modifier = Modifier.weight(1f)
                ) { Text("Applications") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { navController.navigate(Screen.NetworkSurveillance.route) },
                    modifier = Modifier.weight(1f)
                ) { Text("Réseau local") }
                OutlinedButton(
                    onClick = { navController.navigate(Screen.SmsScanner.route) },
                    modifier = Modifier.weight(1f)
                ) { Text("SMS / liens") }
            }

            HorizontalDivider()
            SentinelSectionHeader(
                title = "Identification d’appel / numéro",
                subtitle = "Recherche locale et annuaires officiels lorsque les sources sont réellement disponibles."
            )

            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it.take(32) },
                label = { Text(stringResource(R.string.phone_security_label)) },
                placeholder = { Text(stringResource(R.string.phone_security_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
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
                        rtrResult = null
                        arcepStatus = "Recherche dans l’annuaire officiel…"
                        val candidate = phoneNumber
                        val observedRegion = PhoneRegionRuntimeCache.currentRegionIso()
                        val canonicalCandidate = GlobalPhoneIdentityPolicy.canonicalE164OrNull(
                            AndroidPhoneNumberCanonicalizer.normalizeWithKnownRegion(candidate, observedRegion)
                        )
                        val directoryLookupNumber = canonicalCandidate ?: candidate
                        val directoryTarget = PhoneDirectoryRoutingPolicy.targetFor(
                            directoryLookupNumber,
                            observedRegion
                        )
                        scope.launch {
                            when (directoryTarget) {
                                PhoneDirectoryRoutingPolicy.Target.RTR -> {
                                    val lookup = withContext(Dispatchers.IO) {
                                        runCatching { RtrDirectoryClient().lookup(directoryLookupNumber) }
                                    }
                                    directoryRunning = false
                                    lookup.onSuccess {
                                        rtrResult = it
                                        arcepStatus = if (it != null && it.matches.isNotEmpty()) {
                                            "Attribution RTR trouvée"
                                        } else {
                                            "Aucune attribution RTR correspondante"
                                        }
                                    }.onFailure {
                                        arcepStatus = "Annuaire RTR temporairement indisponible"
                                    }
                                }
                                PhoneDirectoryRoutingPolicy.Target.ARCEP -> {
                                    val lookup = withContext(Dispatchers.IO) {
                                        runCatching { ArcepDirectoryClient().lookup(directoryLookupNumber) }
                                    }
                                    directoryRunning = false
                                    lookup.onSuccess {
                                        arcepResult = it
                                        arcepStatus = if (it != null) {
                                            "Attribution ARCEP trouvée"
                                        } else {
                                            "Aucune attribution ARCEP correspondante"
                                        }
                                    }.onFailure {
                                        arcepStatus = "Annuaire ARCEP temporairement indisponible"
                                    }
                                }
                                PhoneDirectoryRoutingPolicy.Target.NONE -> {
                                    directoryRunning = false
                                    arcepStatus = if (canonicalCandidate == null && observedRegion == null) {
                                        "Contexte téléphonique insuffisant : aucun annuaire national n’est interrogé."
                                    } else if (canonicalCandidate == null) {
                                        "Aucun annuaire officiel intégré pour ce format de numéro."
                                    } else {
                                        "Aucun annuaire officiel intégré pour cet indicatif."
                                    }
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = phoneNumber.isNotBlank()
            ) { Text(stringResource(R.string.phone_security_check)) }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val candidate = phoneNumber.trim()
                        if (candidate.isNotBlank()) {
                            context.startActivity(
                                Intent(context, SentinelDialerActivity::class.java)
                                    .setAction(Intent.ACTION_DIAL)
                                    .setData(Uri.fromParts("tel", candidate, null))
                            )
                        }
                    },
                    enabled = phoneNumber.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Appeler")
                }
                OutlinedButton(
                    onClick = {
                        val candidate = phoneNumber.trim()
                        if (candidate.isNotBlank()) {
                            context.startActivity(
                                Intent(context, SmsComposeActivity::class.java)
                                    .setAction(Intent.ACTION_SENDTO)
                                    .setData(Uri.fromParts("smsto", candidate, null))
                            )
                        }
                    },
                    enabled = phoneNumber.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Message, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("SMS")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { pendingBlockConfirmation = phoneNumber.isNotBlank() },
                    enabled = phoneNumber.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Block, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Bloquer")
                }
                OutlinedButton(
                    onClick = {
                        val candidate = phoneNumber.trim()
                        if (candidate.isNotBlank()) {
                            context.getSystemService(ClipboardManager::class.java)
                                .setPrimaryClip(ClipData.newPlainText("Numéro de téléphone", candidate))
                            actionStatus = "Numéro copié."
                        }
                    },
                    enabled = phoneNumber.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Copier")
                }
            }
            if (pendingBlockConfirmation) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Bloquer ce numéro ?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            "Le numéro sera ajouté à votre liste de blocage locale. Sentinel ne l’envoie pas à un service distant pour cette action.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = { pendingBlockConfirmation = false },
                                modifier = Modifier.weight(1f)
                            ) { Text("Annuler") }
                            Button(
                                onClick = {
                                    val blocked = callBlocklistStore.addBlockedNumber(phoneNumber.trim())
                                    pendingBlockConfirmation = false
                                    actionStatus = if (blocked) "Numéro ajouté à la liste de blocage locale." else "Numéro invalide ou blocage impossible."
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Confirmer") }
                        }
                    }
                }
            }
            actionStatus?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

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

            rtrResult?.let { result ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Attribution officielle RTR (Autriche)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Statut : ${result.status}")
                        result.matches.forEach { allocation ->
                            allocation.allocationHolder?.let { Text("Titulaire publié : $it") }
                            Text("Plage : ${allocation.start} – ${allocation.end}")
                            Text("Catégorie : ${allocation.category}")
                            allocation.area?.let { Text("Zone : $it") }
                            allocation.holderId?.let { Text("Identifiant RTR : $it") }
                        }
                        Text("Attribution réglementaire uniquement : elle ne prouve ni l’opérateur actuel, ni l’identité de l’appelant, ni une fraude.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (remoteEnrichmentEnabled) {
                Button(
                    onClick = {
                        val candidate = phoneNumber
                        val recipientRegion = PhoneRegionRuntimeCache.currentRegionIso()
                        val canonicalCandidate = GlobalPhoneIdentityPolicy.canonicalE164OrNull(
                            AndroidPhoneNumberCanonicalizer.normalizeWithKnownRegion(candidate, recipientRegion)
                        )
                        when {
                            candidate.isBlank() -> Unit
                            recipientRegion == null -> {
                                remoteResult = null
                                remoteStatus = "Enrichissement suspendu : région de ligne non déterminée."
                            }
                            canonicalCandidate == null -> {
                                remoteResult = null
                                remoteStatus = "Enrichissement suspendu : numéro international non déterminé."
                            }
                            else -> {
                                remoteRunning = true
                                remoteResult = null
                                remoteStatus = "Enrichissement Sentinel en cours…"
                                scope.launch {
                                    val checked = withContext(Dispatchers.IO) {
                                        runCatching {
                                            CallerReputationClient(
                                                egressGate = {
                                                    settingsStore.callerReputationEnrichmentEnabled &&
                                                        ProtectionModePolicy.permitsCallerNumberEnrichment(settingsStore.protectionMode)
                                                }
                                            ).evaluate(
                                                callerNumber = canonicalCandidate,
                                                recipientCountry = recipientRegion,
                                                verificationStatus = "UNKNOWN",
                                                privacyMode = PhonePrivacyFirewall.Mode.ENHANCED,
                                                explicitConsent = settingsStore.callerReputationEnrichmentEnabled
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
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = phoneNumber.isNotBlank() && !remoteRunning
                ) {
                    Text(if (remoteRunning) "Enrichissement…" else "Enrichir avec Wangiri / réputation")
                }
            } else {
                Text(
                    "L’enrichissement distant est désactivé. Il peut être activé explicitement dans Blocage d’appels ; le numéro n’est jamais envoyé sans votre accord.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            remoteStatus?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            remoteResult?.let { result ->
                val riskCard = PhoneRiskCard.build(
                    PhoneRiskCard.Input(
                        riskScore = result.riskScore,
                        communitySignals = result.signals,
                        categoryCodes = result.categories,
                        sourceLabel = "Sentinel Reputation"
                    ),
                    nowMs = System.currentTimeMillis()
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Réputation Sentinel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Niveau de risque : " + PhoneRiskCard.riskBandLabelFr(riskCard.riskBand))
                        Text("Score indicatif : " + (riskCard.riskScore?.let { it.toString() + "/100" } ?: "Non mesuré"))
                        Text("Fraîcheur : " + PhoneRiskCard.freshnessLabelFr(riskCard.freshness))
                        Text("Action moteur : ${PhoneCoreFrenchLabels.action(result.action)}")
                        Text("Signalements communautaires : ${result.signals}")
                        Text("Renseignements communautaires : ${PhoneCoreFrenchLabels.communityIntelligence(result.communityIntelligence)}")
                        riskCard.categories.forEach { category -> Text("• " + category.frenchLabel) }
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
                        Text(stringResource(R.string.phone_security_risk_level, PhoneCoreFrenchLabels.riskLevel(result.riskLevel.name)), fontWeight = FontWeight.Bold)
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

@Composable
private fun ProtectionHero(phoneCoreReady: Boolean, callReady: Boolean, smsReady: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("PROTECTION MOBILE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(if (phoneCoreReady) "Prérequis Phone Core prêts" else "Configuration Phone Core à finaliser", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                if (phoneCoreReady) "Tous les prérequis logiciels Phone Core contrôlés par Sentinel sont actuellement prêts. La validation opérationnelle reste distincte."
                else "Sentinel affiche uniquement les protections confirmées par Android. Ouvrez le centre d’activation pour terminer les prérequis manquants.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProtectionStatusChip("Appels", callReady, Modifier.weight(1f))
                ProtectionStatusChip("SMS", smsReady, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ProtectionStatusChip(label: String, ready: Boolean, modifier: Modifier = Modifier) {
    val container = if (ready) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val content = if (ready) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(modifier = modifier, shape = MaterialTheme.shapes.large, color = container) {
        Text(
            text = "$label · " + if (ready) "Prérequis prêts" else "À configurer",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = content,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
