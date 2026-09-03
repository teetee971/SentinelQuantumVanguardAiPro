package com.sentinel.quantum.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.security.LocalLogger
import com.sentinel.quantum.security.SecurityAudit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityAuditScreen(navController: NavController) {
    val context = LocalContext.current
    var auditResult by remember { mutableStateOf<SecurityAudit.SecurityAuditResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val logger = remember { LocalLogger(context) }
    val securityAudit = remember { SecurityAudit(context, logger) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audit de sécurité") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Text("←", style = MaterialTheme.typography.headlineMedium)
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
            Text("Audit local", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Contrôle des permissions réellement déclarées par l'application et de ses informations de package.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = {
                    isLoading = true
                    auditResult = securityAudit.performAudit()
                    isLoading = false
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) { Text(if (isLoading) "Audit en cours..." else "Lancer l'audit") }

            auditResult?.let { result ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Résultats", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        HorizontalDivider()
                        Text("Version : ${result.appInfo.versionName}")
                        Text("Package : ${result.appInfo.packageName}")
                        HorizontalDivider()
                        Text("Permissions déclarées", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        result.permissions.forEach { permission ->
                            PermissionItem(permission.name, permission.granted)
                        }
                        if (result.warnings.isNotEmpty()) {
                            HorizontalDivider()
                            Text("Avertissements (${result.warnings.size})", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                            result.warnings.forEach { warning ->
                                Text("• $warning", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionItem(name: String, granted: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name)
        Text(
            text = if (granted) "Accordée" else "Non accordée",
            color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}
