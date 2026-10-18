package com.sentinel.quantum.ui.screens

import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Security
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.sentinel.quantum.R
import com.sentinel.quantum.security.LocalLogger
import com.sentinel.quantum.security.SecurityAudit
import com.sentinel.quantum.ui.design.DeviceAnalysisStatusPanel
import com.sentinel.quantum.ui.design.SentinelCoreModule
import com.sentinel.quantum.ui.design.SentinelGlassCard
import com.sentinel.quantum.ui.design.SentinelHolographicScene
import com.sentinel.quantum.ui.design.SentinelHolographicSceneModel
import com.sentinel.quantum.ui.design.SentinelMotionPolicy
import com.sentinel.quantum.ui.design.SentinelSceneKind
import com.sentinel.quantum.ui.design.SentinelStatus
import com.sentinel.quantum.ui.design.SentinelStatusChip
import com.sentinel.quantum.ui.design.SentinelTopBar
import com.sentinel.quantum.ui.presentation.DeviceAnalysisFacts
import com.sentinel.quantum.ui.presentation.DeviceAnalysisLayerFacts
import com.sentinel.quantum.ui.presentation.DeviceAnalysisPresentation
import com.sentinel.quantum.ui.presentation.SecurityAuditPermissionPresentation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityAuditScreen(navController: NavController) {
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
    var auditResult by remember { mutableStateOf<SecurityAudit.SecurityAuditResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val logger = remember { LocalLogger(context) }
    val securityAudit = remember { SecurityAudit(context, logger) }
    val scope = rememberCoroutineScope()
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

    val analysisState = remember(auditResult, isLoading) {
        val result = auditResult
        DeviceAnalysisPresentation.from(
            DeviceAnalysisFacts(
                installation = when {
                    isLoading -> DeviceAnalysisLayerFacts(running = true)
                    result != null -> DeviceAnalysisLayerFacts(
                        completed = true,
                        healthyEvidence = true,
                        findingCount = 0
                    )
                    else -> DeviceAnalysisLayerFacts()
                },
                permissions = when {
                    isLoading -> DeviceAnalysisLayerFacts(running = true)
                    result != null -> DeviceAnalysisLayerFacts(
                        completed = true,
                        healthyEvidence = result.warnings.isEmpty(),
                        findingCount = result.warnings.size,
                        criticalFindingCount = 0
                    )
                    else -> DeviceAnalysisLayerFacts()
                },
                applications = DeviceAnalysisLayerFacts(implemented = false)
            )
        )
    }

    val heroTitle = when {
        isLoading -> "Audit local en cours"
        auditResult == null -> "Audit de configuration"
        analysisState.overallStatus == SentinelStatus.OK -> "Contrôles locaux terminés"
        else -> "Points de configuration à examiner"
    }
    val heroSubtitle = when {
        isLoading -> "Sentinel vérifie l’installation et les autorisations Android accessibles à cette surface."
        auditResult == null -> "Exécutez les contrôles locaux pour obtenir des résultats observables. Aucun scan antivirus complet n’est revendiqué."
        auditResult?.warnings?.isNotEmpty() == true -> "Des avertissements de configuration ont été observés. Ils ne constituent pas, à eux seuls, une détection de malware."
        else -> "Les contrôles exécutés n’ont pas produit d’avertissement de configuration. Cela ne prouve pas l’absence de malware."
    }
    val auditModules = listOf(
        SentinelCoreModule(
            id = "installation",
            label = "Installation",
            status = auditLayerStatus(analysisState.installation),
            icon = Icons.Default.Security,
            enabled = false
        ),
        SentinelCoreModule(
            id = "permissions",
            label = "Permissions",
            status = auditLayerStatus(analysisState.permissions),
            icon = Icons.Default.Key,
            enabled = false
        ),
        SentinelCoreModule(
            id = "applications",
            label = "Applications",
            status = auditLayerStatus(analysisState.applications),
            icon = Icons.Default.Apps,
            enabled = false
        ),
        SentinelCoreModule(
            id = "history",
            label = if (auditResult == null) "Historique · INCONNU" else "Résultats · OBSERVÉS",
            status = if (auditResult == null) SentinelStatus.UNKNOWN else analysisState.overallStatus,
            icon = Icons.Default.Security,
            enabled = false
        )
    )

    Scaffold(
        topBar = {
            SentinelTopBar(
                title = stringResource(R.string.security_audit_title),
                subtitle = "Audit local de l’installation",
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
                    kind = SentinelSceneKind.ANALYSIS_REPORT,
                    status = analysisState.overallStatus,
                    title = "RAPPORT D’ANALYSE",
                    evidenceLabel = heroTitle,
                    evidenceSource = "contrôles SecurityAudit exécutés",
                    progress = if (isLoading) 0.5f else if (auditResult == null) null else 1f,
                    nodes = auditModules.map {
                        com.sentinel.quantum.ui.design.SentinelHolographicSceneNode(
                            label = it.label,
                            status = it.status
                        )
                    }
                ),
                motionLevel = motionLevel
            )

            DeviceAnalysisStatusPanel(
                state = analysisState,
                title = "Contrôles locaux",
                subtitle = "Installation et autorisations Android. Aucun scan antivirus complet n’est revendiqué."
            )

            Button(
                onClick = {
                    if (isLoading) return@Button
                    scope.launch {
                        isLoading = true
                        auditResult = withContext(Dispatchers.IO) {
                            securityAudit.performAudit()
                        }
                        isLoading = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text(
                    if (isLoading) stringResource(R.string.security_audit_running)
                    else stringResource(R.string.security_audit_run)
                )
            }

            auditResult?.let { result ->
                SentinelGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            stringResource(R.string.security_audit_results),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        SentinelStatusChip(status = analysisState.overallStatus)
                    }
                    HorizontalDivider()
                    Text(stringResource(R.string.security_audit_version, result.appInfo.versionName))
                    Text("Code de version : ${result.appInfo.versionCode}")
                    Text(stringResource(R.string.security_audit_package, result.appInfo.packageName))
                    HorizontalDivider()
                    Text(
                        "Autorisations Android",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Cette vue distingue ce que vous avez accordé, ce qui reste à activer et ce qu’Android gère automatiquement. Une autorisation manquante est un état de configuration, pas une preuve de compromission.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val runtimeGranted = result.permissions.filter {
                        it.grantModel == SecurityAudit.PermissionGrantModel.RUNTIME_USER && it.granted
                    }
                    val runtimeMissing = result.permissions.filter {
                        it.grantModel == SecurityAudit.PermissionGrantModel.RUNTIME_USER && !it.granted
                    }
                    val automatic = result.permissions.filter {
                        it.grantModel == SecurityAudit.PermissionGrantModel.INSTALL_TIME
                    }
                    val systemControlled = result.permissions.filter {
                        it.grantModel == SecurityAudit.PermissionGrantModel.SYSTEM_CONTROLLED
                    }

                    Text(
                        "${runtimeGranted.size} accordée(s) · ${runtimeMissing.size} à activer · ${automatic.size} automatique(s)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    PermissionSection("À activer", runtimeMissing)
                    PermissionSection("Accordées", runtimeGranted)
                    PermissionSection("Automatiques", automatic)
                    PermissionSection("Gérées par Android", systemControlled)

                    if (result.warnings.isNotEmpty()) {
                        HorizontalDivider()
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SentinelStatusChip(
                                status = SentinelStatus.WARN,
                                label = "${result.warnings.size} avertissement(s)"
                            )
                        }
                        result.warnings.forEach { warning ->
                            Text(
                                "• $warning",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "Ces avertissements décrivent des contrôles de configuration observables ; ils ne sont pas comptés comme des virus ou malwares détectés.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun auditLayerStatus(layer: com.sentinel.quantum.ui.presentation.DeviceAnalysisLayerUiState): SentinelStatus = when {
    layer.state == com.sentinel.quantum.ui.presentation.AnalysisLayerState.FAILED -> SentinelStatus.BAD
    layer.state == com.sentinel.quantum.ui.presentation.AnalysisLayerState.RUNNING -> SentinelStatus.WARN
    layer.state == com.sentinel.quantum.ui.presentation.AnalysisLayerState.UNAVAILABLE -> SentinelStatus.LIMITED
    layer.state == com.sentinel.quantum.ui.presentation.AnalysisLayerState.NOT_EXECUTED -> SentinelStatus.UNKNOWN
    layer.healthyEvidence == true && layer.findingCount == 0 -> SentinelStatus.OK
    layer.healthyEvidence == false || (layer.findingCount ?: 0) > 0 -> SentinelStatus.WARN
    else -> SentinelStatus.UNKNOWN
}

@Composable
private fun PermissionSection(
    title: String,
    permissions: List<SecurityAudit.PermissionStatus>
) {
    if (permissions.isEmpty()) return
    HorizontalDivider()
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
    permissions.forEach { permission ->
        PermissionItem(permission)
    }
}

@Composable
fun PermissionItem(permission: SecurityAudit.PermissionStatus) {
    val statusText = when (permission.grantModel) {
        SecurityAudit.PermissionGrantModel.INSTALL_TIME ->
            if (permission.granted) {
                stringResource(R.string.security_audit_permission_normal_available)
            } else {
                stringResource(R.string.security_audit_permission_unavailable)
            }
        SecurityAudit.PermissionGrantModel.RUNTIME_USER ->
            if (permission.granted) {
                stringResource(R.string.security_audit_permission_granted)
            } else {
                stringResource(R.string.security_audit_permission_denied)
            }
        SecurityAudit.PermissionGrantModel.SYSTEM_CONTROLLED ->
            stringResource(R.string.security_audit_permission_system_controlled)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                SecurityAudit.userFacingPermissionLabel(permission.name),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                permission.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SentinelStatusChip(
            status = SecurityAuditPermissionPresentation.status(
                permission.grantModel,
                permission.granted
            ),
            label = statusText
        )
    }
}
