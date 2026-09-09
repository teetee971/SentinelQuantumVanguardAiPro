package com.sentinel.quantum.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.navigation.NavController
import com.sentinel.quantum.data.AppPermissionAnalyzer
import com.sentinel.quantum.data.InstalledAppPermissionProfile
import com.sentinel.quantum.data.OverallPermissionRisk
import com.sentinel.quantum.data.PermissionRiskLabel
import com.sentinel.quantum.data.PermissionRiskLevel
import com.sentinel.quantum.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPermissionAnalyzerScreen(navController: NavController) {
    val context = LocalContext.current
    val analyzer = remember { AppPermissionAnalyzer(context.packageManager) }
    var profiles by remember { mutableStateOf<List<InstalledAppPermissionProfile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(analyzer) {
        isLoading = true
        errorMessage = null
        try {
            profiles = analyzer.analyzeInstalledApps()
        } catch (_: RuntimeException) {
            errorMessage = "Impossible d'analyser les applications installées."
        } finally {
            isLoading = false
        }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Screen.AppPermissionAnalyzer.titleRes)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                errorMessage != null -> AnalyzerStateMessage(
                    message = errorMessage.orEmpty(),
                    modifier = Modifier.align(Alignment.Center)
                )
                profiles.isEmpty() -> AnalyzerStateMessage(
                    message = "Aucune application installée visible pour l'analyse.",
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        PermissionSummaryCard(profiles)
                    }
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Synthèse locale",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            SummaryRow("Applications analysées", profiles.size.toString())
            SummaryRow("Applications à risque élevé", highRiskApps.toString())
            SummaryRow("Applications avec permissions sensibles", sensitiveApps.toString())
        }
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                Spacer(modifier = Modifier.width(8.dp))
                RiskBadge(profile.overallRisk)
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Réduire" else "Développer"
                    )
                }
            }

            Text(
                text = "Score : ${profile.riskScore} · Permissions déclarées : ${profile.permissions.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                PermissionGroups(profile.permissions)
            }
        }
    }
}

@Composable
private fun RiskBadge(risk: OverallPermissionRisk) {
    val containerColor = when (risk) {
        OverallPermissionRisk.CRITICAL -> MaterialTheme.colorScheme.error
        OverallPermissionRisk.HIGH -> MaterialTheme.colorScheme.errorContainer
        OverallPermissionRisk.MEDIUM -> MaterialTheme.colorScheme.secondary
        OverallPermissionRisk.LOW -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (risk) {
        OverallPermissionRisk.CRITICAL -> MaterialTheme.colorScheme.onError
        OverallPermissionRisk.HIGH -> MaterialTheme.colorScheme.onErrorContainer
        OverallPermissionRisk.MEDIUM -> MaterialTheme.colorScheme.onSecondary
        OverallPermissionRisk.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(color = containerColor, shape = MaterialTheme.shapes.small) {
        Text(
            text = risk.name,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.Bold
        )
    }
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
        PermissionRiskLevel.HIGH_RISK to "Risque élevé",
        PermissionRiskLevel.MEDIUM_RISK to "Risque moyen",
        PermissionRiskLevel.LOW_RISK to "Risque faible"
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
    Text(
        text = message,
        modifier = modifier.padding(16.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
