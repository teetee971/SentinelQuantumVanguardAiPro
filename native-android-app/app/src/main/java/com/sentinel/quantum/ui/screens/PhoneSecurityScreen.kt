package com.sentinel.quantum.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import com.sentinel.quantum.intelligence.Freshness
import com.sentinel.quantum.intelligence.IntelligenceCoreRuntime
import com.sentinel.quantum.intelligence.IntelligenceSnapshotPresentation
import com.sentinel.quantum.security.CallerReputationClient
import com.sentinel.quantum.security.CommunicationReputationCache
import com.sentinel.quantum.security.CommunicationReputationWriter
import com.sentinel.quantum.security.SharedPreferencesCommunicationReputationStorage
import com.sentinel.quantum.security.CallBlocklistStore
import com.sentinel.quantum.security.ArcepDirectoryClient
import com.sentinel.quantum.security.RtrDirectoryClient
import com.sentinel.quantum.security.ExplainableAI
import com.sentinel.quantum.security.LocalLogger
import com.sentinel.quantum.security.PhoneMonitor
import com.sentinel.quantum.security.PhoneCoreFrenchLabels
import com.sentinel.quantum.security.PhoneRiskCard
import com.sentinel.quantum.security.ProtectionModePolicy
import com.sentinel.quantum.security.PhonePrivacyFirewall
import com.sentinel.quantum.ui.design.SentinelActionTile
import com.sentinel.quantum.ui.design.SentinelCoreModule
import com.sentinel.quantum.ui.design.SentinelHolographicScene
import com.sentinel.quantum.ui.design.SentinelHolographicSceneModel
import com.sentinel.quantum.ui.design.SentinelMotionPolicy
import com.sentinel.quantum.ui.design.SentinelSceneKind
import com.sentinel.quantum.ui.design.SentinelStatus
import com.sentinel.quantum.ui.design.visual
import com.sentinel.quantum.ui.design.SentinelTopBar
import com.sentinel.quantum.ui.design.SentinelSectionHeader
import com.sentinel.quantum.ui.presentation.ProtectionOverviewPresentation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Serializes a reputation write with its stale-generation cleanup.
 *
 * Generation invalidation is synchronous and only touches a small in-memory counter, so editing
 * the field never waits for disk I/O. A persistence operation owns the mutex across the actual
 * write and the conditional cleanup; a newer generation observed during I/O therefore removes
 * the old candidate before another queued write can begin.
 */
internal class GenerationSafeReputationPersistence<T>(
    private val persist: suspend (candidate: String, value: T) -> Unit,
    private val discard: suspend (candidate: String) -> Unit
) {
    private val stateLock = Any()
    private val persistenceLock = Mutex()
    private var generation = 0

    fun invalidate(): Int = synchronized(stateLock) {
        generation += 1
        generation
    }

    suspend fun persistIfCurrent(
        expectedGeneration: Int,
        candidate: String,
        value: T
    ): Boolean = persistenceLock.withLock {
        if (!isCurrent(expectedGeneration)) return@withLock false

        persist(candidate, value)

        if (!isCurrent(expectedGeneration)) {
            discard(candidate)
            return@withLock false
        }
        true
    }

    private fun isCurrent(expectedGeneration: Int): Boolean = synchronized(stateLock) {
        generation == expectedGeneration
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneSecurityScreen(navController: NavController) {
    val context = LocalContext.current
    var phoneNumber by rememberSaveable { mutableStateOf("") }
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
    var intelligencePresentation by remember { mutableStateOf<IntelligenceSnapshotPresentation?>(null) }
    var lookupGeneration by remember { mutableStateOf(0) }
    var postureEpoch by remember { mutableStateOf(0) }

    fun clearLookupResults() {
        checkResult = null
        explanation = null
        monitorStats = null
        remoteResult = null
        remoteStatus = null
        remoteRunning = false
        arcepResult = null
        arcepStatus = null
        directoryRunning = false
        rtrResult = null
        intelligencePresentation = null
        actionStatus = null
        pendingBlockConfirmation = false
    }

    fun isCurrentLookup(
        generation: Int,
        candidate: String,
        currentGeneration: Int,
        currentPhoneNumber: String
    ): Boolean = generation == currentGeneration && candidate == currentPhoneNumber
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
    val reputationStorage = remember(context) {
        SharedPreferencesCommunicationReputationStorage(context.applicationContext)
    }
    val reputationCache = remember(reputationStorage) {
        CommunicationReputationCache(reputationStorage)
    }
    val reputationWriter = remember(reputationCache) {
        CommunicationReputationWriter(reputationCache)
    }
    val generationSafeReputationPersistence = remember(reputationWriter, reputationStorage) {
        GenerationSafeReputationPersistence<CallerReputationClient.Result>(
            persist = { candidate, result -> reputationWriter.persist(candidate, result) },
            discard = { candidate ->
                CommunicationReputationCache.cacheKeyFor(candidate)?.let(reputationStorage::remove)
            }
        )
    }
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
    val protectionStatus = remember(phoneCoreReady, callPrerequisitesReady, messagePrerequisitesReady) {
        ProtectionOverviewPresentation.status(
            phoneCoreReady = phoneCoreReady,
            callPrerequisitesReady = callPrerequisitesReady,
            messagePrerequisitesReady = messagePrerequisitesReady
        )
    }
    val protectionMotion = remember(context, postureEpoch) {
        val animatorScale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f)
        val powerSave = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        SentinelMotionPolicy.resolve(animatorScale, powerSave)
    }
    val protectionModules = remember(
        protectionStatus,
        callPrerequisitesReady,
        messagePrerequisitesReady,
        remoteEnrichmentEnabled
    ) {
        listOf(
            SentinelCoreModule(
                id = "phone-core",
                label = "Téléphone",
                status = protectionStatus,
                icon = Icons.Default.Security
            ),
            SentinelCoreModule(
                id = "calls",
                label = "Appels",
                status = if (callPrerequisitesReady) SentinelStatus.OK else SentinelStatus.LOCKED,
                icon = Icons.Default.Phone
            ),
            SentinelCoreModule(
                id = "messages",
                label = "SMS/MMS",
                status = if (messagePrerequisitesReady) SentinelStatus.OK else SentinelStatus.LOCKED,
                icon = Icons.Default.Message
            ),
            SentinelCoreModule(
                id = "network",
                label = "Réseau",
                status = SentinelStatus.UNKNOWN,
                icon = Icons.Default.Wifi
            ),
            SentinelCoreModule(
                id = "vpn",
                label = "VPN",
                status = SentinelStatus.UNKNOWN,
                icon = Icons.Default.VpnLock
            ),
            SentinelCoreModule(
                id = "reputation",
                label = "Réputation",
                status = if (remoteEnrichmentEnabled) SentinelStatus.LIMITED else SentinelStatus.LOCKED,
                icon = Icons.Default.Security
            )
        )
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
            SentinelHolographicScene(
                model = SentinelHolographicSceneModel(
                    kind = SentinelSceneKind.REAL_TIME_PROTECTION,
                    status = protectionStatus,
                    title = "PROTECTION TEMPS RÉEL",
                    evidenceLabel = protectionStatus.visual().label,
                    evidenceSource = "prérequis et contrôles Android observés",
                    nodes = protectionModules.map {
                        com.sentinel.quantum.ui.design.SentinelHolographicSceneNode(
                            label = it.label,
                            status = it.status
                        )
                    }
                ),
                modifier = Modifier.testTag("protection-command-center"),
                motionLevel = protectionMotion
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
                title = "Centre de contrôle",
                subtitle = "Analyses locales et protections disponibles, sans transformer une heuristique en détection malware."
            )
            SentinelActionTile(
                title = "Audit local",
                subtitle = "Vérifier l’installation et les autorisations Android avec des résultats observables.",
                icon = Icons.Default.Security,
                onClick = { navController.navigate(Screen.SecurityAudit.route) },
                modifier = Modifier.testTag("protection-analysis-action")
            )
            SentinelActionTile(
                title = "Applications & permissions",
                subtitle = "Analyser les permissions sensibles. Un niveau de risque élevé n’est pas une preuve de malware.",
                icon = Icons.Default.Apps,
                onClick = { navController.navigate(Screen.AppPermissionAnalyzer.route) },
                modifier = Modifier.testTag("protection-app-permissions-action")
            )
            SentinelActionTile(
                title = "Réseau local",
                subtitle = "Ouvrir les observations Wi‑Fi et réseau avec leurs limites Android explicites.",
                icon = Icons.Default.Wifi,
                onClick = { navController.navigate(Screen.NetworkSurveillance.route) },
                modifier = Modifier.testTag("protection-network-action")
            )
            SentinelActionTile(
                title = "VPN Sentinel",
                subtitle = "Configurer le tunnel défensif et consulter son état réel avant toute connexion.",
                icon = Icons.Default.VpnLock,
                onClick = { navController.navigate(Screen.Vpn.route) },
                modifier = Modifier.testTag("protection-vpn-action")
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { navController.navigate(Screen.DigitalExposure.route) },
                    modifier = Modifier.weight(1f)
                ) { Text("Exposition numérique") }
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
                onValueChange = {
                    lookupGeneration = generationSafeReputationPersistence.invalidate()
                    phoneNumber = it.take(32)
                    clearLookupResults()
                },
                label = { Text(stringResource(R.string.phone_security_label)) },
                placeholder = { Text(stringResource(R.string.phone_security_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true
            )

            Button(
                onClick = {
                    if (phoneNumber.isNotBlank()) {
                        lookupGeneration = generationSafeReputationPersistence.invalidate()
                        clearLookupResults()
                        val generation = lookupGeneration
                        val candidate = phoneNumber
                        checkResult = phoneMonitor.checkNumber(phoneNumber)
                        explanation = checkResult?.let(explainableAI::explainSpamCheck)
                        monitorStats = phoneMonitor.getStats()
                        directoryRunning = true
                        arcepStatus = "Recherche dans l’annuaire officiel…"
                        scope.launch {
                            val snapshotPresentation = withContext(Dispatchers.IO) {
                                IntelligenceCoreRuntime.snapshotForNumber(context.applicationContext, candidate)
                                    ?.let { IntelligenceSnapshotPresentation.from(it) }
                            }
                            if (!isCurrentLookup(generation, candidate, lookupGeneration, phoneNumber)) return@launch
                            intelligencePresentation = snapshotPresentation
                            val isAustria = RtrDirectoryClient.normalize(candidate) != null
                            if (isAustria) {
                                val lookup = withContext(Dispatchers.IO) { runCatching { RtrDirectoryClient().lookup(candidate) } }
                                if (!isCurrentLookup(generation, candidate, lookupGeneration, phoneNumber)) return@launch
                                directoryRunning = false
                                lookup.onSuccess {
                                    rtrResult = it
                                    arcepStatus = if (it != null && it.matches.isNotEmpty()) "Attribution RTR trouvée" else "Aucune attribution RTR correspondante"
                                }.onFailure { arcepStatus = "Annuaire RTR temporairement indisponible" }
                            } else {
                                val lookup = withContext(Dispatchers.IO) { runCatching { ArcepDirectoryClient().lookup(candidate) } }
                                if (!isCurrentLookup(generation, candidate, lookupGeneration, phoneNumber)) return@launch
                                directoryRunning = false
                                lookup.onSuccess {
                                    arcepResult = it
                                    arcepStatus = if (it != null) "Attribution ARCEP trouvée" else "Aucune attribution ARCEP correspondante"
                                }.onFailure { arcepStatus = "Annuaire ARCEP temporairement indisponible" }
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
                        Text(
                            "Attribution réglementaire uniquement : elle ne prouve ni l’opérateur actuel, ni l’identité de l’appelant, ni une fraude.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            intelligencePresentation?.let { intelligencePresentation ->
                IntelligenceDiagnosticCard(
                    intelligencePresentation = intelligencePresentation
                )
            }

            if (remoteEnrichmentEnabled) {
                Button(
                    onClick = {
                        val candidate = phoneNumber
                        if (candidate.isNotBlank()) {
                            lookupGeneration = generationSafeReputationPersistence.invalidate()
                            clearLookupResults()
                            val generation = lookupGeneration
                            remoteRunning = true
                            remoteStatus = "Enrichissement Sentinel en cours…"
                            scope.launch {
                                val checked = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val result = CallerReputationClient(
                                            egressGate = {
                                                settingsStore.callerReputationEnrichmentEnabled &&
                                                    ProtectionModePolicy.permitsCallerNumberEnrichment(settingsStore.protectionMode)
                                            }
                                        ).evaluate(
                                            callerNumber = candidate,
                                            recipientCountry = Locale.getDefault().country.ifBlank { "FR" },
                                            verificationStatus = "UNKNOWN",
                                            privacyMode = PhonePrivacyFirewall.Mode.ENHANCED,
                                            explicitConsent = settingsStore.callerReputationEnrichmentEnabled
                                        )
                                        result
                                    }
                                }
                                if (!isCurrentLookup(generation, candidate, lookupGeneration, phoneNumber)) return@launch
                                remoteRunning = false
                                checked.onFailure {
                                    remoteStatus = "Réputation distante indisponible"
                                }
                                checked.getOrNull()?.let { result ->
                                    if (!isCurrentLookup(generation, candidate, lookupGeneration, phoneNumber)) return@launch
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            generationSafeReputationPersistence.persistIfCurrent(
                                                expectedGeneration = generation,
                                                candidate = candidate,
                                                value = result
                                            )
                                        }
                                    }.onFailure {
                                        if (!isCurrentLookup(generation, candidate, lookupGeneration, phoneNumber)) return@launch
                                        remoteStatus = "Réputation distante indisponible"
                                        return@launch
                                    }
                                    if (!isCurrentLookup(generation, candidate, lookupGeneration, phoneNumber)) return@launch
                                    remoteResult = result
                                    remoteStatus = "Réputation distante reçue"
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
private fun IntelligenceDiagnosticCard(
    intelligencePresentation: IntelligenceSnapshotPresentation
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "Intelligence Sentinel locale",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text("Révision locale : ${intelligencePresentation.revision}")
            Text(
                "Score indicatif partagé : " +
                    (intelligencePresentation.riskScore?.let { "$it/100" } ?: "Non mesuré")
            )
            Text(
                "Fraîcheur : " + when (intelligencePresentation.freshness) {
                    Freshness.FRESH -> "À jour"
                    Freshness.STALE -> "Expirée"
                    Freshness.UNKNOWN -> "Non déterminée"
                }
            )
            Text("Preuves actives : ${intelligencePresentation.activeEvidenceCount}")
            Text("Preuves expirées : ${intelligencePresentation.staleEvidenceCount}")
            if (intelligencePresentation.sources.isNotEmpty()) {
                Text("Sources : " + intelligencePresentation.sources.joinToString(" · ") { it.name })
            }
            if (intelligencePresentation.evidenceStates.isNotEmpty()) {
                Text("États de preuve : " + intelligencePresentation.evidenceStates.joinToString(" · ") { it.name })
            }
            if (intelligencePresentation.communityAggregatePresent) {
                Text(
                    "Des signaux communautaires sont présents comme contexte uniquement ; ils n’autorisent pas à eux seuls un blocage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "Vue diagnostique locale en lecture seule. Ces éléments ne déclenchent aucune action automatique.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    com.sentinel.quantum.ui.design.SentinelStatusChip(
        status = ProtectionOverviewPresentation.prerequisiteStatus(ready),
        modifier = modifier,
        label = "$label · " + if (ready) "Prérequis prêts" else "À configurer"
    )
}
