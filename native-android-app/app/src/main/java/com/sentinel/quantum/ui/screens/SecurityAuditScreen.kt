package com.sentinel.quantum.ui.screens

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

data class PermissionAuditItem(
    val permissionName: String,
    val isGranted: Boolean,
    val description: String
)

@Composable
fun SecurityAuditScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var auditList by remember { mutableStateOf(emptyList<PermissionAuditItem>()) }

    val trackedPermissions = remember {
        listOf(
            "android.permission.READ_CONTACTS" to "Accès au répertoire pour identifier les appelants légitimes.",
            "android.permission.POST_NOTIFICATIONS" to "Affichage des alertes système de sécurité en temps réel.",
            "android.permission.FOREGROUND_SERVICE" to "Exécution persistante des filtres de blocage d'arrière-plan."
        )
    }

    LaunchedEffect(Unit) {
        auditList = trackedPermissions.map { (permission, desc) ->
            val status = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            PermissionAuditItem(permission.removePrefix("android.permission."), status, desc)
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Security Audit Center", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Analyse en direct des autorisations système requises pour la protection.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(auditList) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (item.isGranted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = item.permissionName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = if (item.isGranted) "SECURE" else "VULNERABLE",
                                    color = if (item.isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = item.description, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
