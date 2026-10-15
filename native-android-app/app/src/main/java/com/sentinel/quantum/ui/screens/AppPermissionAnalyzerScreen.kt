package com.sentinel.quantum.ui.screens

import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.sentinel.quantum.data.AppPermissionAnalyzer
import com.sentinel.quantum.data.InstalledAppPermissionProfile
import com.sentinel.quantum.data.OverallPermissionRisk
import com.sentinel.quantum.data.PermissionRiskLabel
import com.sentinel.quantum.data.PermissionRiskLevel
import com.sentinel.quantum.navigation.Screen
import com.sentinel.quantum.ui.design.DeviceAnalysisStatusPanel
import com.sentinel.quantum.ui.design.SentinelGlassCard
import com.sentinel.quantum.ui.design.SentinelHeroVisual
import com.sentinel.quantum.ui.design.SentinelMotionPolicy
import com.sentinel.quantum.ui.design.SentinelStatusChip
import com.sentinel.quantum.ui.design.SentinelTopBar
import com.sentinel.quantum.ui.presentation.DeviceAnalysisFacts
import com.sentinel.quantum.ui.presentation.DeviceAnalysisLayerFacts
import com.sentinel.quantum.ui.presentation.DeviceAnalysisPresentation
import com.sentinel.quantum.ui.presentation.PermissionRiskPresentation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPermissionAnalyzerScreen(navController: NavController) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    var motionEpoch by remember { mutableIntStateOf(0) }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) motionEpoch++
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }
    val analyzer = remember { AppPermissionAnalyzer(context.packageManager) }
    val motionLevel = remember(context, motionEpoch) {
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

    var profiles by remember { mutableStateOf<List<InstalledAppPermissionProfile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(analyzer) {
        isLoading = true
        errorMessage = null
        try {
            profiles = withContext(Dispatchers.IO) {
                analyzer.analyzeInstalledApps()
            }
        } catch (_: RuntimeException) {
            errorMessage = "Impossible d'analyser les applications installées."
        } finally {
            isLoading = false
        }
    }

    val highRiskApps = remember(profiles) {
        profiles.count {
            it.overallRisk == OverallPermissionRisk.CRITICAL || it.overallRisk == OverallPermissionRisk.HIGH
        }
    }
    val sensitiveApps = remember(profiles) {
        profiles.count { profile ->
            profile.permissions.any { it.riskLevel == PermissionRiskLevel.HIGH_RISK }
        }
    }
    val analysisState = remember(profiles, isLoading, errorMessage, highRiskApps, sensitiveApps) {
        DeviceAnalysisPresentation.from(
            DeviceAnalysisFacts(
                installation = DeviceAnalysisLayerFacts(implemented = false),
                permissions = DeviceAnalysisLayerFacts(implemented = false),
                applications = when {
                    isLoading -> DeviceAnalysisLayerFacts(running = true)
                    errorMessage != null -> DeviceAnalysisLayerFacts(failed = true)
                    else -> DeviceAnalysisLayerFacts(
                        completed = true,
                        healthyEvidence = true,
                        findingCount = highRiskApps,
                        // CRITICAL/HIGH here is a permission-risk heuristic, not malware evidence.
                        criticalFindingCount = 0
                    )
                },
                sensitivePermissionCount = sensitiveApps
            )
        )
    }

    val filteredProfiles = remember(profiles, query) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            profiles
        } else {
            profiles.filter {
                it.appName.contains(normalizedQuery, ignoreCase = true) ||
                    it.packageName.contains(normalizedQuery, ignoreCase = true)
            }
        }
    }

    val heroTitle = when {
        isLoading -> "Analyse locale en cours"
        errorMessage != null -> "Analyse locale limitée"
        else -> "Contrôle des permissions"
    }
    val heroSubtitle = when {
        isLoading -> "Sentinel inspecte localement les permissions déclarées par les applications visibles."
        errorMessage != null -> "Le contrôle n’a pas produit de résultat exploitable. Aucun verdict de sécurité n’est déduit."
        highRiskApps > 0 -> "$highRiskApps application(s) présentent une heuristique de permissions élevée. Cela ne prouve pas la présence d’un malware."
        else -> "Le contrôle local n’observe pas d’heuristique élevée parmi les applications visibles. Cela ne constitue pas un scan antivirus."
    }

    Scaffold(
        topBar = {
            SentinelTopBar(
                title = stringResource(Screen.AppPermissionAnalyzer.titleRes),
                subtitle = "Permissions sensibles · analyse locale",
                onBack = { navController.popBackStack() }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SentinelHeroVisual(
                    status = analysisState.overallStatus,
                    title = heroTitle,
                    subtitle = heroSubtitle,
                    motionLevel = motionLevel
                )
            }

            item {
                DeviceAnalysisStatusPanel(
                    state = analysisState,
                    title = "Analyse des permissions applications",
                    subtitle = "Heuristique locale des permissions déclarées. Ce contrôle n’est pas un scan antivirus."
                )
            }

            when {
                isLoading -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                errorMessage != null -> item {
                    AnalyzerStateMessage(message = errorMessage.orEmpty())
                }

                profiles.isEmpty() -> item {
                    AnalyzerStateMessage(message = "Aucune application installée visible pour l'analyse.")
                }

                else -> {
                    item { PermissionSummaryCard(profiles) }
                    item { PermissionScopeCard() }
                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Filtrer par nom ou package") },
                            singleLine = true
                        )
                    }
                    if (filteredProfiles.isEmpty()) {
                        item {
                            AnalyzerStateMessage(
                                message = "Aucun résultat pour ce filtre.",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp)
                            )
                        }
                    } else {
                        items(filteredProfiles, key = { it.packageName }) { profile ->
                            AppPermissionProfileCard(profile)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionSummaryCard(profiles: List<InstalledAppPermissionProfile>) {
    val highRiskApps = profiles.count {
        it.overallRisk == OverallPermissionRisk.CRITICAL || it.overallRisk == OverallPermissionRisk.HIGH
    }
    val sensitiveApps = profiles.count { profile ->
        profile.permissions.any { it.riskLevel == PermissionRiskLevel.HIGH_RISK }
    }

    SentinelGlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Synthèse locale",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        SummaryRow("Applications analysées", profiles.size.toString())
        SummaryRow("Heuristiques de permissions élevées", highRiskApps.toString())
        SummaryRow("Applications avec permissions sensibles", sensitiveApps.toString())
        Text(
            text = "Ces compteurs décrivent des permissions déclarées. Ils ne représentent ni des virus détectés, ni des malwares bloqués, ni des éléments placés en quarantaine.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PermissionScopeCard() {
    SentinelGlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Portée du contrôle",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Sentinel analyse ici les permissions déclarées des applications visibles pour Android. Cette surface n’inspecte pas le contenu binaire d’une application et n’affirme pas détecter un malware à partir d’une permission seule.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun AppPermissionProfileCard(profile: InstalledAppPermissionProfile) {
    var expanded by rememberSaveable(profile.packageName) { mutableStateOf(false) }

    SentinelGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.appName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = profile.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Réduire" else "Développer"
                )
            }
        }

        PermissionRiskBadge(profile.overallRisk)

        Text(
            text = "Score heuristique de permissions : ${profile.riskScore} · Permissions déclarées : ${profile.permissions.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (expanded) {
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            PermissionGroups(profile.permissions)
        }
    }
}

@Composable
private fun PermissionRiskBadge(risk: OverallPermissionRisk) {
    SentinelStatusChip(
        status = PermissionRiskPresentation.status(risk),
        label = PermissionRiskPresentation.label(risk)
    )
}

@Composable
private fun PermissionGroups(permissions: List<PermissionRiskLabel>) {
    if (permissions.isEmpty()) {
        Text(
            text = "Aucune permission déclarée.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    listOf(
        PermissionRiskLevel.HIGH_RISK to "Permissions à vigilance élevée",
        PermissionRiskLevel.MEDIUM_RISK to "Permissions à vigilance intermédiaire",
        PermissionRiskLevel.LOW_RISK to "Signaux faibles / non concluants"
    ).forEach { (riskLevel, title) ->
        val group = permissions.filter { it.riskLevel == riskLevel }
        if (group.isNotEmpty()) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            group.forEach { permission ->
                Text(
                    text = "• ${permission.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AnalyzerStateMessage(message: String, modifier: Modifier = Modifier) {
    SentinelGlassCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
