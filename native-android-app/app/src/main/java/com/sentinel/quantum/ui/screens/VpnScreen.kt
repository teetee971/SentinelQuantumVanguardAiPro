package com.sentinel.quantum.ui.screens

import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.security.SentinelVpnController
import com.sentinel.quantum.ui.design.SentinelD1
import com.sentinel.quantum.ui.design.SentinelGlassCard
import com.sentinel.quantum.ui.design.SentinelHeroVisual
import com.sentinel.quantum.ui.design.SentinelMotionPolicy
import com.sentinel.quantum.ui.design.SentinelSectionHeader
import com.sentinel.quantum.ui.design.SentinelStatus
import com.sentinel.quantum.ui.design.SentinelStatusChip
import com.sentinel.quantum.ui.design.SentinelTopBar

/**
 * Truthful D.1 VPN status surface.
 *
 * The Android WireGuard client foundation exists, but no validated Sentinel exit gateway is
 * currently provisioned. A protected state is therefore impossible on this screen until runtime
 * evidence proves a real tunnel UP.
 */
@Composable
fun VpnScreen(navController: NavController) {
    val context = LocalContext.current
    val runtimeState = SentinelVpnController.RuntimeState.READY_NO_GATEWAY
    val runtimeStatus = NetworkVpnPresentation.vpnStatus(runtimeState)
    val motionLevel = remember(context) {
        val animatorScale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f)
        val powerSave = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        SentinelMotionPolicy.resolve(animatorScale, powerSave)
    }

    Scaffold(
        topBar = {
            SentinelTopBar(
                title = "Sentinel VPN",
                subtitle = "Passerelle indisponible · tunnel non attesté",
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
            SentinelHeroVisual(
                status = runtimeStatus,
                title = "Passerelle requise",
                subtitle = "Service VPN public non opérationnel : aucune passerelle Sentinel disponible et validée.",
                modifier = Modifier.testTag("vpn-command-center"),
                motionLevel = motionLevel
            )

            VpnReferenceDashboard(runtimeStatus)

            SentinelSectionHeader(
                title = "État du tunnel",
                trailing = "Preuve runtime"
            )

            SentinelGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vpn-runtime-truth")
            ) {
                SentinelStatusChip(status = runtimeStatus, label = "Passerelle absente")
                Text(
                    text = "Client intégré",
                    style = MaterialTheme.typography.labelLarge,
                    color = SentinelD1.Cyan,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Client WireGuard intégré",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Le contrôleur Sentinel impose un tunnel complet IPv4 + IPv6, un DNS épinglé à la passerelle et le consentement VPN Android. La configuration sensible est effacée du buffer après validation.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Un état protégé exige un handshake WireGuard réellement établi et confirmé par le backend.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SentinelSectionHeader(
                title = "Pourquoi la connexion est verrouillée",
                trailing = "Fail-closed"
            )

            SentinelGlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Passerelle Sentinel indisponible",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SentinelD1.Warning
                )
                Text(
                    text = "Une connexion ne peut démarrer qu’avec une passerelle marquée AVAILABLE par le catalogue/control plane Sentinel et disposant de DNS épinglés. Les états PLANNED, PROVISIONING, DRAINING, OFFLINE ou REVOKED restent verrouillés.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Une action de connexion ne sera affichée que lorsque toute la chaîne sera réellement vérifiable.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SentinelSectionHeader(
                title = "Capacités du client Android",
                trailing = "Client Android"
            )

            SentinelGlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("• Backend WireGuard Android maintenu")
                Text("• Consentement VpnService obligatoire")
                Text("• Full tunnel IPv4 et IPv6 requis")
                Text("• DNS limité aux adresses épinglées de la passerelle")
                Text("• Arbitrage avec Private Mesh")
                Text("• Bail de connexion borné et arrêt fail-closed")
                Text(
                    text = "Aucun pays, débit, IP de sortie ou niveau de protection n’est affiché tant qu’un tunnel réel n’a pas été établi et confirmé.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VpnReferenceDashboard(status: SentinelStatus) {
    SentinelGlassCard(
        modifier = Modifier.fillMaxWidth().testTag("vpn-reference-dashboard")
    ) {
        Text("État du tunnel", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Text(
            "Les valeurs réseau restent masquées tant qu’un handshake WireGuard n’est pas attesté.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VpnMetric("Serveur VPN", "Non configuré", Icons.Default.Dns, Modifier.weight(1f))
            VpnMetric("IP de sortie", "Non disponible", Icons.Default.Language, Modifier.weight(1f))
            VpnMetric("Latence", "Non mesurée", Icons.Default.Speed, Modifier.weight(1f))
            VpnMetric("Données chiffrées", "Non attestée", Icons.Default.Lock, Modifier.weight(1f))
        }
        SentinelStatusChip(status, label = "Passerelle absente")
    }

    SentinelSectionHeader(title = "Fonctionnalités de protection", trailing = "Fail-closed")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VpnFeature("Kill Switch", "Verrouillé", Modifier.weight(1f))
        VpnFeature("DNS protégé", "Non attesté", Modifier.weight(1f))
        VpnFeature("Anti-traçage", "Non attesté", Modifier.weight(1f))
        VpnFeature("Fuite WebRTC", "Non attesté", Modifier.weight(1f))
    }
    SentinelSectionHeader(title = "Actions rapides", trailing = "Disponibles après provisioning")
}

@Composable
private fun VpnMetric(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Column(modifier, horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = SentinelD1.Cyan, modifier = Modifier.size(24.dp))
        Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun VpnFeature(label: String, state: String, modifier: Modifier) {
    androidx.compose.material3.ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(containerColor = SentinelD1.Card.copy(alpha = 0.9f))
    ) {
        Column(Modifier.padding(9.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(state, style = MaterialTheme.typography.labelSmall, color = SentinelD1.Warning)
        }
    }
}
