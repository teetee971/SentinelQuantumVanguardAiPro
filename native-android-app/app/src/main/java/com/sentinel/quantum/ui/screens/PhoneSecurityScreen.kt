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
import com.sentinel.quantum.security.ExplainableAI
import com.sentinel.quantum.security.LocalLogger
import com.sentinel.quantum.security.PhoneMonitor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneSecurityScreen(navController: NavController) {
    val context = LocalContext.current
    var phoneNumber by remember { mutableStateOf("") }
    var checkResult by remember { mutableStateOf<PhoneMonitor.SpamCheckResult?>(null) }
    var explanation by remember { mutableStateOf<ExplainableAI.Explanation?>(null) }
    
    val logger = remember { LocalLogger(context) }
    val phoneMonitor = remember { PhoneMonitor(context, logger) }
    val explainableAI = remember { ExplainableAI(context, logger) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sécurité Téléphone") },
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
            Text(
                text = "Détection de SPAM",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Vérifiez un numéro de téléphone contre des sources publiques de SPAM",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("Numéro de téléphone") },
                placeholder = { Text("+33 6 12 34 56 78") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Button(
                onClick = {
                    if (phoneNumber.isNotBlank()) {
                        checkResult = phoneMonitor.checkNumber(phoneNumber)
                        checkResult?.let {
                            explanation = explainableAI.explainSpamCheck(it)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = phoneNumber.isNotBlank()
            ) {
                Text("Vérifier le numéro")
            }
            
            checkResult?.let { result ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when (result.riskLevel) {
                            PhoneMonitor.RiskLevel.HIGH -> MaterialTheme.colorScheme.errorContainer
                            PhoneMonitor.RiskLevel.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer
                            PhoneMonitor.RiskLevel.LOW -> MaterialTheme.colorScheme.primaryContainer
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Résultat de la Vérification",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Divider()
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Niveau de risque:")
                            Text(
                                text = result.riskLevel.name,
                                fontWeight = FontWeight.Bold,
                                color = when (result.riskLevel) {
                                    PhoneMonitor.RiskLevel.HIGH -> MaterialTheme.colorScheme.error
                                    PhoneMonitor.RiskLevel.MEDIUM -> MaterialTheme.colorScheme.tertiary
                                    PhoneMonitor.RiskLevel.LOW -> MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                        
                        Text(
                            text = "Raison: ${result.reason}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            
            explanation?.let { exp ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "🤖 Explication IA Locale",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Text(
                            text = exp.summary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        if (exp.recommendations.isNotEmpty()) {
                            Divider()
                            Text(
                                text = "Recommandations:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            exp.recommendations.forEach { recommendation ->
                                Text(
                                    text = "• $recommendation",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        
                        Text(
                            text = "Confiance: ${(exp.confidence * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "ℹ️ Information",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Cette fonction utilise uniquement des sources publiques pour détecter les numéros suspects. Aucune interception ou écoute n'est effectuée.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
