package com.sentinel.quantum.ui.screens

import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.sentinel.quantum.security.SentinelVpnRuntimeStateRegistry
import com.sentinel.quantum.security.SentinelVpnController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnLock
import com.sentinel.quantum.ui.design.SentinelCoreModule
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
    val runtimeState = SentinelVpnRuntimeStateRegistry.current()?.state
    val runtimeSurface = NetworkVpnPresentation.vpnSurface(runtimeState)
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
    val tunnelModules = listOf(
        SentinelCoreModule(
            id = "device",
            label = "Téléphone · LOCAL",
            status = SentinelStatus.UNKNOWN,
            icon = Icons.Default.Security,
            enabled = false
        ),
        SentinelCoreModule(
            id = "tunnel",
            label = "Tunnel · ${runtimeSurface.badge}",
            status = runtimeSurface.status,
            icon = Icons.Default.VpnLock,
            enabled = false
        ),
        SentinelCoreModule(
            id = "gateway",
            label = if (runtimeState == SentinelVpnController.RuntimeState.PROTECTED) {
                "Passerelle · OBSERVÉE"
            } else {
                "Passerelle · INCONNUE"
            },
            status = if (runtimeState == SentinelVpnController.RuntimeState.PROTECTED) {
                SentinelStatus.OK
            } else {
                SentinelStatus.UNKNOWN
            },
            icon = Icons.Default.Security,
            enabled = false
        ),
        SentinelCoreModule(
            id = "dns",
            label = "DNS · INCONNU",
            status = SentinelStatus.UNKNOWN,
            icon = Icons.Default.Dns,
            enabled = false
        ),
        SentinelCoreModule(
            id = "ipv4",
            label = "IPv4 · INCONNU",
            status = SentinelStatus.UNKNOWN,
            icon = Icons.Default.Security,
            enabled = false
        ),
        SentinelCoreModule(
            id = "ipv6",
            label = "IPv6 · INCONNU",
            status = SentinelStatus.UNKNOWN,
            icon = Icons.Default.Security,
            enabled = false
        )
    )

    Scaffold(
        topBar = {
            SentinelTopBar(
                title = "Sentinel VPN",
                subtitle = "WireGuard défensif · vérité runtime",
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
                status = runtimeSurface.status,
                title = runtimeSurface.title,
                subtitle = runtimeSurface.subtitle,
                modifier = Modifier.testTag("vpn-command-center"),
                motionLevel = motionLevel,
                modules = tunnelModules
            )

            SentinelSectionHeader(
                title = "État du tunnel",
                trailing = "Preuve runtime"
            )

            SentinelGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vpn-runtime-truth")
            ) {
                SentinelStatusChip(status = runtimeSurface.status, label = runtimeSurface.badge)
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
                title = runtimeSurface.explanationTitle,
                trailing = "Vérité runtime"
            )

            SentinelGlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = runtimeSurface.badge,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SentinelD1.Warning
                )
                Text(
                    text = runtimeSurface.explanation,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Aucune action de connexion n’est affichée sans chaîne runtime vérifiable.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SentinelSectionHeader(
                title = "Fondations déjà actives",
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
