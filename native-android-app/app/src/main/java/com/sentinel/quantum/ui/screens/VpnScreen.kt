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
import androidx.compose.ui.res.stringResource
import com.sentinel.quantum.R
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
                subtitle = stringResource(R.string.vpn_d1_top_subtitle),
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
                title = stringResource(R.string.vpn_d1_hero_title),
                subtitle = stringResource(R.string.vpn_d1_hero_subtitle),
                modifier = Modifier.testTag("vpn-command-center"),
                motionLevel = motionLevel
            )

            SentinelSectionHeader(
                title = stringResource(R.string.vpn_d1_tunnel_title),
                trailing = stringResource(R.string.vpn_d1_runtime_evidence)
            )

            SentinelGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vpn-runtime-truth")
            ) {
                SentinelStatusChip(status = runtimeStatus, label = stringResource(R.string.vpn_d1_gateway_missing))
                Text(
                    text = stringResource(R.string.vpn_d1_client_integrated),
                    style = MaterialTheme.typography.labelLarge,
                    color = SentinelD1.Cyan,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.vpn_d1_wireguard_integrated),
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
                title = stringResource(R.string.vpn_d1_locked_reason),
                trailing = "Fail-closed"
            )

            SentinelGlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.vpn_d1_gateway_unavailable),
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
                title = stringResource(R.string.vpn_d1_foundations),
                trailing = stringResource(R.string.vpn_d1_android_client)
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
