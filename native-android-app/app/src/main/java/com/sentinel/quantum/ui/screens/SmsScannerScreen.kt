package com.sentinel.quantum.ui.screens
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
@Composable
fun SmsScannerScreen(modifier: Modifier = Modifier) {
 var smsText by remember { mutableStateOf("") }
 var result by remember { mutableStateOf("") }
 Surface(modifier = modifier.fillMaxSize()) {
 Column(modifier = Modifier.padding(16.dp)) {
 Text("Analyse assistee d un SMS", style = MaterialTheme.typography.headlineMedium)
 Spacer(modifier = Modifier.height(16.dp))
 OutlinedTextField(value = smsText, onValueChange = { smsText = it }, label = { Text("Collez le texte du SMS") }, modifier = Modifier.fillMaxWidth().height(150.dp))
 Spacer(modifier = Modifier.height(12.dp))
 Button(onClick = { val lower = smsText.lowercase(); result = if (lower.contains("http") || lower.contains("amende") || lower.contains("suspendu")) "Vecteur de phishing suspecte localement" else "Aucun signal de fraude evident" }, modifier = Modifier.fillMaxWidth()) { Text("Analyser les signaux") }
 if (result.isNotBlank()) {
 Spacer(modifier = Modifier.height(16.dp))
 Card(modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(16.dp)) { Text(result, style = MaterialTheme.typography.titleMedium) } }
 }
 }
 }
}
