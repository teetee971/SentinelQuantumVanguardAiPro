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
import com.sentinel.quantum.data.SharedTextHolder
import com.sentinel.quantum.security.LocalLogger
import com.sentinel.quantum.security.SmsLinkAnalyzer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsScannerScreen(navController: NavController) {
    val context = LocalContext.current
    val analyzer = remember(context) { SmsLinkAnalyzer(LocalLogger(context)) }
    var rawMessage by remember { mutableStateOf(SharedTextHolder.consume().orEmpty()) }
    var result by remember { mutableStateOf<SmsLinkAnalyzer.Analysis?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text("Scanner SMS / liens") }, navigationIcon = {
        IconButton(onClick = { navController.navigateUp() }) { Text("←") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Collez le texte du SMS suspect. L'analyse reste sur l'appareil et n'accède à aucune boîte de messages.")
            OutlinedTextField(rawMessage, { rawMessage = it.take(16 * 1024) },
                label = { Text("Message SMS") }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp), minLines = 6)
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
                        if (analysis.findings.isEmpty()) Text("Aucun indicateur détecté. Cela ne garantit pas que le message soit légitime.")
                        else analysis.findings.forEach { Text("• [${it.severity}] ${it.description}") }
                        Text(
                            "Ce score est indicatif et local ; il ne remplace pas votre propre vigilance.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            } }
        }
    }
}
