package com.sentinel.quantum.ui.screens
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(modifier: Modifier = Modifier, onNavigate: (String) -> Unit = {}) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Sentinel Quantum Vanguard", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { onNavigate("blocking") }) { Text("Filtre d'appels") }
            Button(onClick = { onNavigate("logs") }, modifier = Modifier.padding(top = 8.dp)) { Text("Journaux de sécurité") }
        }
    }
}
