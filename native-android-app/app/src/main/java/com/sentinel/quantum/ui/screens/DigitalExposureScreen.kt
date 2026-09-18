package com.sentinel.quantum.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.security.PwnedPasswordClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DigitalExposureScreen(navController: NavController) {
    var password by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<PwnedPasswordClient.Result?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val client = remember { PwnedPasswordClient() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Exposition numérique") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Contrôle k-anonyme d’un mot de passe compromis",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                "Le mot de passe est haché localement. Sentinel transmet uniquement les 5 premiers caractères du SHA-1 au service Pwned Passwords, puis compare le suffixe sur l’appareil. Le mot de passe et son hash complet ne sont jamais envoyés."
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it.take(256) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Mot de passe à vérifier") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = password.isNotEmpty() && !running,
                onClick = {
                    val candidate = password
                    password = ""
                    result = null
                    status = null
                    running = true
                    scope.launch {
                        val checked = withContext(Dispatchers.IO) {
                            runCatching { client.check(candidate) }
                        }
                        candidate.toCharArray().fill('\u0000')
                        running = false
                        checked.onSuccess { result = it }
                            .onFailure { status = "Service d’exposition indisponible : " + (it.message ?: "erreur réseau") }
                    }
                }
            ) {
                Text(if (running) "Vérification…" else "Vérifier maintenant")
            }

            result?.let { exposure ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (exposure.exposed) "Mot de passe exposé" else "Aucune correspondance trouvée",
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (exposure.exposed) {
                            Text("Occurrences connues : ${exposure.occurrenceCount}")
                            Text("N’utilisez plus ce mot de passe. Remplacez-le partout où il est utilisé, choisissez un mot de passe unique et activez la MFA lorsque disponible.")
                        } else {
                            Text("Aucune correspondance dans la base interrogée. Cela ne prouve pas que le mot de passe est sûr ou secret.")
                        }
                    }
                }
            }
            status?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Veille d’e-mail / domaine", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Non activée sans fournisseur autorisé et secret serveur. Les recherches d’adresses ou de domaines ne seront pas simulées ni effectuées depuis l’APK avec une clé embarquée."
                    )
                }
            }
        }
    }
}
