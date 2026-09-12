package com.sentinel.quantum.ui.screens
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
@Composable
fun EmailSecurityScreen(modifier: Modifier = Modifier) {
 Surface(modifier = modifier.fillMaxSize()) {
 Column(modifier = Modifier.padding(16.dp)) {
 Text("Email Security", style = MaterialTheme.typography.headlineMedium)
 Spacer(modifier = Modifier.height(16.dp))
 Text("Analyse d en-tetes et extraction d IoC locales operationnelles.", style = MaterialTheme.typography.bodyMedium)
 }
 }
}
