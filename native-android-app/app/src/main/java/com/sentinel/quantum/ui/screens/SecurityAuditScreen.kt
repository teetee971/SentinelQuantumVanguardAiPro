package com.sentinel.quantum.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.R
import com.sentinel.quantum.security.LocalLogger
import com.sentinel.quantum.security.SecurityAudit
import com.sentinel.quantum.ui.design.SentinelD1
import com.sentinel.quantum.ui.design.SentinelHero
import com.sentinel.quantum.ui.design.SentinelTopBar

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
            SentinelHero(
                eyebrow = "Audit",
                title = stringResource(R.string.security_audit_heading),
                body = stringResource(R.string.security_audit_description),
                badges = listOf(
                    "Local" to SentinelD1.Success,
                    "Observable" to SentinelD1.Cyan
                )
            )
            Button(
                onClick = {
                    isLoading = true
                    auditResult = securityAudit.performAudit()
                    isLoading = false
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) { Text(if (isLoading) stringResource(R.string.security_audit_running) else stringResource(R.string.security_audit_run)) }

            auditResult?.let { result ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.security_audit_results), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        HorizontalDivider()
                        Text(stringResource(R.string.security_audit_version, result.appInfo.versionName))
                        Text("Code de version : ${result.appInfo.versionCode}")
                        Text(stringResource(R.string.security_audit_package, result.appInfo.packageName))
                        HorizontalDivider()
                        Text(stringResource(R.string.security_audit_permissions), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.security_audit_permissions_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        result.permissions.forEach { permission ->
                            PermissionItem(permission)
                        }
                        if (result.warnings.isNotEmpty()) {
                            HorizontalDivider()
                            Text(
                                stringResource(R.string.security_audit_warnings, result.warnings.size),
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
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
        Text(permission.name, modifier = Modifier.weight(1f))
        Text(
            text = statusText,
            color = if (permission.granted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
        )
    }
}
