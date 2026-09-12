package com.sentinel.quantum.ui.screens
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
@Composable
fun PhoneSecurityScreen(modifier: Modifier = Modifier) {
 val dummy = listOf("Chiffrement materiel : Securise", "Debogage USB : Actif (Attention)", "Surveillance reseau : Operationnelle")
 Surface(modifier = modifier.fillMaxSize()) {
 Column(modifier = Modifier.padding(16.dp)) {
 Text("Phone Security", style = MaterialTheme.typography.headlineMedium)
 LazyColumn { items(dummy) { Text(it, modifier = Modifier.padding(8.dp)) } }
 }
 }
}
