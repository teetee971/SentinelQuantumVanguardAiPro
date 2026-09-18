package com.sentinel.quantum.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.sentinel.quantum.vpn.SentinelDnsVpnService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefensiveVpnScreen(navController: NavController) {
    val context = LocalContext.current
    var active by remember { mutableStateOf(SentinelDnsVpnService.isRunning(context)) }
    var status by remember { mutableStateOf<String?>(null) }

    fun startService() {
        val intent = Intent(context, SentinelDnsVpnService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
        active = true
        status = "Activation demandée. Android affiche une notification persistante tant que le filtre DNS est actif."
    }

    val vpnConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startService()
        } else {
            status = "Consentement VPN refusé : aucune protection DNS n’a été activée."
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VPN défensif & anti-traceurs") },
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
                "Filtre DNS local via Android VpnService",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Ce module intercepte uniquement les requêtes DNS dirigées vers le DNS virtuel Sentinel. " +
                    "Les domaines publicitaires et traceurs connus reçoivent une réponse NXDOMAIN locale ; " +
                    "les autres requêtes DNS sont relayées vers un résolveur externe via un socket protégé du VPN."
            )

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (active) "Protection DNS : ACTIVE" else "Protection DNS : INACTIVE", fontWeight = FontWeight.Bold)
                    Text(
                        "Limites : ce service ne déchiffre pas HTTPS, ne bloque pas les publicités servies depuis le même domaine que le contenu, " +
                            "et ne remplace pas encore un tunnel WireGuard complet. Les applications utilisant leur propre DNS chiffré peuvent contourner le filtre."
                    )
                }
            }

            if (!active) {
                Button(
                    onClick = {
                        val prepare = VpnService.prepare(context)
                        if (prepare == null) startService() else vpnConsent.launch(prepare)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Activer le VPN DNS défensif")
                }
            } else {
                OutlinedButton(
                    onClick = {
                        context.startService(
                            Intent(context, SentinelDnsVpnService::class.java)
                                .setAction(SentinelDnsVpnService.ACTION_STOP)
                        )
                        active = false
                        status = "Protection DNS arrêtée."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Arrêter la protection")
                }
            }

            status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Anti-publicité et anti-traceurs", fontWeight = FontWeight.Bold)
                    Text(
                        "Le filtre embarque une liste locale bornée de domaines publicitaires et de mesure connus. " +
                            "Aucun historique DNS n’est exporté par ce service. Une liste blanche personnalisable et des listes signées plus larges seront ajoutées séparément."
                    )
                }
            }
        }
    }
}
