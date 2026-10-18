package com.sentinel.quantum.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.R
import com.sentinel.quantum.ui.design.SentinelCoreModule
import com.sentinel.quantum.ui.design.SentinelHeroVisual
import com.sentinel.quantum.ui.design.SentinelStatus
import com.sentinel.quantum.ui.design.SentinelTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(navController: NavController) {
    Scaffold(
        topBar = {
            SentinelTopBar(
                title = stringResource(R.string.about_title),
                subtitle = "Périmètre, limites & transparence",
                onBack = { navController.popBackStack() }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SentinelHeroVisual(
                status = SentinelStatus.UNKNOWN,
                title = "Ce que Sentinel fait — et ne fait pas",
                subtitle = "Cette page définit le périmètre réel du produit. Une capacité non démontrée reste explicitement absente, limitée ou en validation.",
                modules = listOf(
                    SentinelCoreModule("scope", "Périmètre", SentinelStatus.TEST, Icons.Default.Security, enabled = false),
                    SentinelCoreModule("limits", "Limites", SentinelStatus.UNKNOWN, Icons.Default.Security, enabled = false),
                    SentinelCoreModule("proof", "Preuves", SentinelStatus.LOCKED, Icons.Default.Security, enabled = false)
                )
            )

            // What Sentinel DOES
            SectionCard(
                title = stringResource(R.string.about_does_title),
                items = listOf(
                    stringResource(R.string.about_does_1),
                    stringResource(R.string.about_does_2),
                    stringResource(R.string.about_does_3),
                    stringResource(R.string.about_does_4)
                ),
                positive = true
            )
            
            // What Sentinel DOES NOT
            SectionCard(
                title = stringResource(R.string.about_doesnt_title),
                items = listOf(
                    stringResource(R.string.about_doesnt_1),
                    stringResource(R.string.about_doesnt_2),
                    stringResource(R.string.about_doesnt_3),
                    stringResource(R.string.about_doesnt_4),
                    stringResource(R.string.about_doesnt_5)
                ),
                positive = false
            )
            
            // Disclaimer
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.about_disclaimer_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = stringResource(R.string.about_disclaimer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
fun SectionCard(title: String, items: List<String>, positive: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (positive) 
                MaterialTheme.colorScheme.surface 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (positive) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            
            items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = if (positive) "✓" else "✗",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (positive) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
