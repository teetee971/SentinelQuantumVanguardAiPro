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
import com.sentinel.quantum.security.EmailSecurityAnalyzer
import com.sentinel.quantum.security.LocalLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailSecurityScreen(navController: NavController) {
    val analyzer = remember { EmailSecurityAnalyzer(LocalLogger(LocalContext.current)) }
    var rawMessage by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<EmailSecurityAnalyzer.Analysis?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text("Analyse locale d'un email") }, navigationIcon = {
        IconButton(onClick = { navController.navigateUp() }) { Text("←") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Collez le message brut avec ses en-têtes. L'analyse reste sur l'appareil et n'accède à aucune boîte mail.")
            OutlinedTextField(rawMessage, { rawMessage = it.take(256 * 1024) },
                label = { Text("Email brut") }, modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp), minLines = 8)
            Button({ result = analyzer.analyze(rawMessage) }, Modifier.fillMaxWidth(), enabled = rawMessage.isNotBlank()) {
                Text("Analyser localement")
            }
            result?.let { analysis -> Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Résultat", fontWeight = FontWeight.Bold)
                    if (!analysis.accepted) Text("Analyse refusée : ${analysis.reason ?: "entrée invalide"}")
                    else {
                        Text("Risque indicatif : ${analysis.riskLevel}")
                        Text("Score heuristique : ${analysis.score}/100")
                        Text("Liens inspectés : ${analysis.linksInspected}")
                        if (analysis.findings.isEmpty()) Text("Aucun indicateur détecté. Cela ne garantit pas que l'email soit légitime.")
                        else analysis.findings.forEach { Text("• [${it.severity}] ${it.description}") }
                        if (analysis.observedAuthenticationResults) Text(
                            "SPF/DKIM/DMARC sont lus depuis Authentication-Results, sans vérification DNS indépendante.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            } }
        }
    }
}
