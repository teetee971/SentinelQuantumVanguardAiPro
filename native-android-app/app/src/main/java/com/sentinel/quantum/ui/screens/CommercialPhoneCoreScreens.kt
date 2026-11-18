package com.sentinel.quantum.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.PhoneCoreActivationActivity
import com.sentinel.quantum.PhoneCoreDiagnosticActivity
import com.sentinel.quantum.R
import com.sentinel.quantum.SentinelDialerActivity
import com.sentinel.quantum.SmsComposeActivity
import com.sentinel.quantum.background.PublishedNumberCatalogScheduler
import com.sentinel.quantum.data.SettingsStore
import com.sentinel.quantum.data.ThemeMode
import com.sentinel.quantum.navigation.Screen
import com.sentinel.quantum.ui.design.SentinelPanel
import com.sentinel.quantum.ui.design.SentinelTopBar

@Composable
internal fun CommercialPhoneCoreHomeScreen(navController: NavController) {
    val context = LocalContext.current
    CommercialPhoneCorePage("Sentinel") {
        SentinelPanel {
            Text("Activation Phone Core", style = MaterialTheme.typography.titleMedium)
            Text("Suivre les rôles et autorisations nécessaires aux appels et aux SMS/MMS.")
            Button(
                onClick = { context.openPhoneCore(Intent(context, PhoneCoreActivationActivity::class.java)) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Ouvrir l’assistant d’activation") }
        }
        CommercialAction("Téléphone") {
            context.openPhoneCore(Intent(context, SentinelDialerActivity::class.java))
        }
        CommercialAction("Messages SMS/MMS") {
            context.openPhoneCore(Intent(context, SmsComposeActivity::class.java))
        }
        CommercialAction("Contacts") {
            context.openPhoneCore(
                Intent(context, SentinelDialerActivity::class.java)
                    .putExtra(SentinelDialerActivity.EXTRA_OPEN_CONTACTS, true)
            )
        }
        CommercialAction("Identifier un numéro") { navController.navigate(Screen.Search.route) }
    }
}

@Composable
internal fun CommercialPhoneCoreProtectionScreen(navController: NavController) {
    CommercialPhoneCorePage(stringResource(R.string.nav_protection)) {
        CommercialAction("Règles de filtrage") { navController.navigate(Screen.CallBlocking.route) }
        CommercialAction("Listes de protection") { navController.navigate(Screen.PhoneLists.route) }
        CommercialAction("Historique du filtrage") { navController.navigate(Screen.CallFilterHistory.route) }
    }
}

@Composable
internal fun CommercialPhoneCoreSettingsScreen(
    navController: NavController,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    val context = LocalContext.current
    val settings = remember(context) { SettingsStore(context) }
    var catalogRefresh by remember(settings) { mutableStateOf(settings.isPublishedCatalogRefreshEnabled()) }
    var smsPreview by remember(settings) { mutableStateOf(settings.smsNotificationPreviewEnabled) }
    CommercialPhoneCorePage(stringResource(R.string.nav_settings)) {
        SentinelPanel {
            Text(stringResource(R.string.settings_theme_title), style = MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup()) {
                ThemeOptionRow(stringResource(R.string.settings_theme_system), themeMode == ThemeMode.SYSTEM) {
                    onThemeModeChange(ThemeMode.SYSTEM)
                }
                ThemeOptionRow(stringResource(R.string.settings_theme_light), themeMode == ThemeMode.LIGHT) {
                    onThemeModeChange(ThemeMode.LIGHT)
                }
                ThemeOptionRow(stringResource(R.string.settings_theme_dark), themeMode == ThemeMode.DARK) {
                    onThemeModeChange(ThemeMode.DARK)
                }
            }
        }
        SentinelPanel {
            Text("Confidentialité", style = MaterialTheme.typography.titleMedium)
            CommercialSwitch(
                "Aperçu des SMS dans les notifications",
                "Inclut l’expéditeur et un extrait du message dans les notifications Sentinel.",
                smsPreview
            ) { enabled ->
                settings.smsNotificationPreviewEnabled = enabled
                smsPreview = settings.smsNotificationPreviewEnabled
            }
            CommercialSwitch(
                stringResource(R.string.settings_catalog_switch),
                "Actualise les copies publiques ARCEP et RTR. Une recherche manuelle peut aussi télécharger la copie publique si elle manque sur l’appareil.",
                catalogRefresh
            ) { enabled ->
                settings.setPublishedCatalogRefreshEnabled(enabled)
                catalogRefresh = settings.isPublishedCatalogRefreshEnabled()
                PublishedNumberCatalogScheduler.sync(context, catalogRefresh)
            }
            CommercialAction("Autorisations Android") {
                context.openPhoneCore(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                )
            }
            CommercialAction("Politique de confidentialité") {
                context.openPhoneCore(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://sentinelquantumvanguardaipro.pages.dev/public/privacy.html"))
                )
            }
        }
        CommercialAction("Diagnostic Phone Core") {
            context.openPhoneCore(Intent(context, PhoneCoreDiagnosticActivity::class.java))
        }
        CommercialAction("Journal local de diagnostic") { navController.navigate(Screen.LocalLogs.route) }
    }
}

@Composable
private fun CommercialPhoneCorePage(title: String, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(topBar = { SentinelTopBar(title = title) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun CommercialAction(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
}

@Composable
private fun CommercialSwitch(label: String, details: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(label)
            Text(details, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun Context.openPhoneCore(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, "Cette action est indisponible sur cet appareil.", Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(this, "Android a refusé cette action.", Toast.LENGTH_SHORT).show()
    }
}
