package com.sentinel.quantum.ui.screens

import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.PhoneCoreActivationActivity
import com.sentinel.quantum.SentinelDialerActivity
import com.sentinel.quantum.SmsComposeActivity
import com.sentinel.quantum.navigation.Screen
import com.sentinel.quantum.ui.design.SentinelD1
import com.sentinel.quantum.ui.design.SentinelHero
import com.sentinel.quantum.ui.design.SentinelSectionHeader
import com.sentinel.quantum.ui.design.SentinelTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            SentinelTopBar(
                title = "SENTINEL",
                subtitle = "Quantum Vanguard AI Pro"
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SentinelHero(
                eyebrow = "Protection mobile",
                title = "Centre de protection Sentinel",
                body = "Appels, messages, réseau et exposition numérique réunis dans une interface locale avec état explicite.",
                badges = listOf(
                    "Local" to SentinelD1.Success,
                    "Confidentiel" to SentinelD1.Cyan
                )
            )

            SentinelSectionHeader(
                title = "Actions rapides",
                subtitle = "Les trois actions les plus fréquentes restent accessibles immédiatement."
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickAction("Appeler", Icons.Default.Phone, Modifier.weight(1f)) {
                    context.startActivity(Intent(context, SentinelDialerActivity::class.java))
                }
                QuickAction("Message", Icons.Default.Sms, Modifier.weight(1f)) {
                    context.startActivity(Intent(context, SmsComposeActivity::class.java))
                }
                QuickAction("Réseau", Icons.Default.Wifi, Modifier.weight(1f)) {
                    navController.navigate(Screen.NetworkSurveillance.route)
                }
            }

            SentinelSectionHeader(
                title = "Communications",
                subtitle = "Téléphonie et messagerie avec état Android réellement observé."
            )
            DashboardCard(
                "Phone Core",
                "Activer et tester appels entrants/sortants, filtrage, SMS et contacts",
                Icons.Default.PhoneInTalk
            ) {
                context.startActivity(Intent(context, PhoneCoreActivationActivity::class.java))
            }
            DashboardCard(
                "Communications",
                "Appels, SMS/MMS et état explicite des canaux externes",
                Icons.Default.Forum
            ) {
                navController.navigate(Screen.CommunicationsHub.route)
            }

            SentinelSectionHeader(
                title = "Protection & analyse",
                subtitle = "Chaque module distingue les capacités disponibles, à configurer et encore à valider."
            )
            DashboardCard("Protection mobile", "Audit de l’appareil et posture de sécurité", Icons.Default.Shield) {
                navController.navigate(Screen.PhoneSecurity.route)
            }
            DashboardCard("Diagnostic système", "Correctifs, stockage et signaux de sécurité observables", Icons.Default.Security) {
                navController.navigate(Screen.SystemDoctor.route)
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
            DashboardCard("VPN défensif", "Client WireGuard intégré · aucune passerelle Sentinel opérationnelle", Icons.Default.VpnLock) {
                navController.navigate(Screen.Vpn.route)
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

            SentinelSectionHeader(
                title = "Outils",
                subtitle = "Audit technique et veille OSINT."
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { navController.navigate(Screen.SecurityAudit.route) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Security, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Audit")
                }
                OutlinedButton(
                    onClick = { navController.navigate(Screen.OsintFeed.route) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Public, null)
                    Spacer(Modifier.width(6.dp))
                    Text("OSINT")
                }
            }
        }
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: ImageVector,
    modifier: Modifier,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier
            .height(88.dp)
            .border(1.dp, SentinelD1.Border, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = SentinelD1.Card,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        contentPadding = PaddingValues(8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, contentDescription = null, tint = SentinelD1.Cyan)
            Text(label, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DashboardCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SentinelD1.Border, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = SentinelD1.Card
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SentinelD1.Panel
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp).size(28.dp),
                    tint = SentinelD1.Cyan
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
