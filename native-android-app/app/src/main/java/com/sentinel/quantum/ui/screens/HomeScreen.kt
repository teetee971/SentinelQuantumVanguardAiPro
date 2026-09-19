package com.sentinel.quantum.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.navigation.Screen
import com.sentinel.quantum.SmsComposeActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SENTINEL", fontWeight = FontWeight.ExtraBold)
                        Text("Quantum Vanguard AI Pro", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Centre de protection", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Appels, messages, réseau et exposition numérique réunis dans un tableau de bord unique.")
                }
            }

            Text("Actions rapides", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction("Appeler", Icons.Default.Phone, Modifier.weight(1f)) {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:")))
                }
                QuickAction("Message", Icons.Default.Sms, Modifier.weight(1f)) {
                    context.startActivity(Intent(context, SmsComposeActivity::class.java))
                }
                QuickAction("Réseau", Icons.Default.Wifi, Modifier.weight(1f)) {
                    navController.navigate(Screen.NetworkSurveillance.route)
                }
            }

            Text("Protection", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            DashboardCard("Protection mobile", "Audit de l’appareil et posture de sécurité", Icons.Default.Shield) {
                navController.navigate(Screen.PhoneSecurity.route)
            }
            DashboardCard("Appels & Caller ID", "Filtrage local, réputation et historique", Icons.Default.PhoneInTalk) {
                navController.navigate(Screen.CallBlocking.route)
            }
            DashboardCard("Messages & liens", "Analyser un SMS ou ouvrir la messagerie", Icons.Default.MarkChatUnread) {
                navController.navigate(Screen.SmsScanner.route)
            }
            DashboardCard("Exposition numérique", "Contrôler les signaux d’exposition disponibles", Icons.Default.Key) {
                navController.navigate(Screen.DigitalExposure.route)
            }
            DashboardCard("WiFi & Bluetooth", "Scanner les environs puis ouvrir la connexion Android", Icons.Default.Radar) {
                navController.navigate(Screen.NetworkSurveillance.route)
            }
            DashboardCard("Appareils & Maison", "Montres, Imou, Philips Hue, LSC, Matter et topologie", Icons.Default.HomeWork) {
                navController.navigate(Screen.SmartHome.route)
            }
            DashboardCard("Analyse e-mail", "Inspection locale des en-têtes, domaines et liens", Icons.Default.Email) {
                navController.navigate(Screen.EmailSecurity.route)
            }
            DashboardCard("Permissions des applications", "Repérer les permissions sensibles installées", Icons.Default.Apps) {
                navController.navigate(Screen.AppPermissionAnalyzer.route)
            }
            DashboardCard("Historique des appels filtrés", "Consulter les décisions prises par Sentinel", Icons.Default.History) {
                navController.navigate(Screen.CallFilterHistory.route)
            }

            Text("Outils", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { navController.navigate(Screen.SecurityAudit.route) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Security, null); Spacer(Modifier.width(6.dp)); Text("Audit")
                }
                OutlinedButton(onClick = { navController.navigate(Screen.OsintFeed.route) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Public, null); Spacer(Modifier.width(6.dp)); Text("OSINT")
                }
            }
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = modifier.height(76.dp), contentPadding = PaddingValues(8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.height(4.dp))
            Text(label)
        }
    }
}

@Composable
private fun DashboardCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(12.dp).size(28.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}
