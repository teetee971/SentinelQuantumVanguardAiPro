package com.sentinel.quantum.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.navigation.Screen
import com.sentinel.quantum.ui.design.SentinelActionButton
import com.sentinel.quantum.ui.design.SentinelCoreModule
import com.sentinel.quantum.ui.design.SentinelGlassCard
import com.sentinel.quantum.ui.design.SentinelQuantumCore
import com.sentinel.quantum.ui.design.SentinelSectionHeader
import com.sentinel.quantum.ui.design.SentinelStatus
import com.sentinel.quantum.ui.design.SentinelTopBar
import com.sentinel.quantum.ui.presentation.AssistantPromptPolicy

@Composable
fun AssistantScreen(navController: NavController) {
    var prompt by rememberSaveable { mutableStateOf("") }
    var answer by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            SentinelTopBar(
                title = "Assistant Sentinel",
                subtitle = "Conseils contextualisés · provenance séparée",
                onBack = { navController.popBackStack() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SentinelQuantumCore(
                modules = listOf(
                    SentinelCoreModule("risk", "Risque · QUESTION", SentinelStatus.TEST, Icons.Default.HelpOutline, false),
                    SentinelCoreModule("alert", "Alerte · CONTEXTE", SentinelStatus.UNKNOWN, Icons.Default.Security, false),
                    SentinelCoreModule("source", "Provenance · REQUISE", SentinelStatus.LOCKED, Icons.Default.Security, false),
                    SentinelCoreModule("advice", "Conseil · LOCAL", SentinelStatus.TEST, Icons.Default.AutoAwesome, false)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            SentinelSectionHeader(
                title = "Poser une question",
                subtitle = "L’assistant explique les signaux disponibles sans transformer une réponse générée en preuve."
            )
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it.take(500) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Question sur un risque, une alerte ou un numéro") },
                minLines = 2,
                maxLines = 5
            )
            SentinelActionButton(
                label = "Obtenir un conseil",
                onClick = { answer = AssistantPromptPolicy.respond(prompt) },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.AutoAwesome
            )
            answer?.let { response ->
                SentinelGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Text("Réponse Sentinel", fontWeight = FontWeight.Bold)
                    }
                    Text(response)
                    Text(
                        "Sources à vérifier : contrôles locaux, données Android observées et provenance de l’alerte.",
                        color = com.sentinel.quantum.ui.design.SentinelVisualTokens.TextSecondary
                    )
                }
            }
            SentinelGlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("Limite de qualification", fontWeight = FontWeight.Bold)
                Text(
                    "Cette surface fournit actuellement une aide locale déterministe. Aucun fournisseur IA distant, historique personnel ou preuve technique n’est inventé. Les fonctions d’analyse dédiées restent les sources de vérité."
                )
            }
        }
    }
}
