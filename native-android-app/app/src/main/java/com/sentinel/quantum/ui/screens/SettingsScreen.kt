package com.sentinel.quantum.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.core.content.ContextCompat
import com.sentinel.quantum.R
import com.sentinel.quantum.background.WorkScheduler
import com.sentinel.quantum.data.OsintFeedCache
import com.sentinel.quantum.data.SettingsStore
import com.sentinel.quantum.data.SentinelPreferencesBackup
import com.sentinel.quantum.data.ThemeMode
import com.sentinel.quantum.security.LocalLogger
import com.sentinel.quantum.security.CallBlocklistStore
import com.sentinel.quantum.security.CallRuleSyncConfig
import com.sentinel.quantum.security.FamilySafetyPolicy
import com.sentinel.quantum.ui.design.SentinelTopBar
import com.sentinel.quantum.ui.design.SentinelSectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    val context = LocalContext.current
    val settingsStore = remember(context) { SettingsStore(context) }
    val logger = remember(context) { LocalLogger(context) }
    val blocklistStore = remember(context) { CallBlocklistStore(context) }
    val osintFeedCache = remember(context) { OsintFeedCache(context) }
    val ruleSyncAvailable = CallRuleSyncConfig.SYNC_ENABLED && CallRuleSyncConfig.TRUSTED_KEYS.isNotEmpty()
    var ruleSyncEnabled by remember {
        mutableStateOf(ruleSyncAvailable && settingsStore.isRuleSyncEnabled())
    }
    var intervalHours by remember { mutableStateOf(settingsStore.osintRefreshIntervalHours) }
    var familySafetyProfile by remember { mutableStateOf(settingsStore.familySafetyProfile) }
    var notificationsEnabled by remember {
        mutableStateOf(
            settingsStore.osintNotificationsEnabled &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED)
        )
    }
    var statusMessageRes by remember { mutableStateOf<Int?>(null) }
    var backupStatus by remember { mutableStateOf<String?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsEnabled = granted
        settingsStore.osintNotificationsEnabled = granted
        statusMessageRes = if (granted) {
            R.string.settings_osint_notifications_enabled
        } else {
            R.string.settings_osint_permission_denied
        }
    }
    val createBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val snapshot = SentinelPreferencesBackup.Snapshot(
                themeMode = themeMode,
                protectionMode = settingsStore.protectionMode,
                familySafetyProfile = settingsStore.familySafetyProfile,
                callerReputationEnrichmentEnabled = settingsStore.callerReputationEnrichmentEnabled,
                osintRefreshIntervalHours = settingsStore.osintRefreshIntervalHours,
                osintNotificationsEnabled = settingsStore.osintNotificationsEnabled,
                smsNotificationPreviewEnabled = settingsStore.smsNotificationPreviewEnabled,
                blockedPrefixes = blocklistStore.snapshot().blockedPrefixes.sorted()
            )
            backupStatus = runCatching {
                val output = context.contentResolver.openOutputStream(uri, "wt")
                    ?: error("BACKUP_OUTPUT_UNAVAILABLE")
                output.bufferedWriter(Charsets.UTF_8).use {
                    it.write(SentinelPreferencesBackup.encode(snapshot))
                }
                "Sauvegarde locale exportée."
            }.getOrElse {
                "Échec de l’export de la sauvegarde locale."
            }
        }
    }
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            backupStatus = runCatching {
                val input = context.contentResolver.openInputStream(uri)
                    ?: error("BACKUP_INPUT_UNAVAILABLE")
                val raw = input.bufferedReader(Charsets.UTF_8).use { reader ->
                    readBoundedBackupText(reader)
                }
                val restored = SentinelPreferencesBackup.decode(raw)
                    ?: error("BACKUP_INVALID")
                val previousPrefixes = blocklistStore.snapshot().blockedPrefixes
                if (!blocklistStore.replaceBlockedPrefixes(restored.blockedPrefixes)) {
                    error("PREFIX_RESTORE_FAILED")
                }
                val settingsCommitted = settingsStore.applyRestorablePreferences(
                    SettingsStore.RestorablePreferences(
                        themeMode = restored.themeMode,
                        protectionMode = restored.protectionMode,
                        familySafetyProfile = restored.familySafetyProfile,
                        callerReputationEnrichmentEnabled =
                            restored.callerReputationEnrichmentEnabled,
                        osintRefreshIntervalHours = restored.osintRefreshIntervalHours,
                        osintNotificationsEnabled = restored.osintNotificationsEnabled,
                        smsNotificationPreviewEnabled = restored.smsNotificationPreviewEnabled
                    )
                )
                if (!settingsCommitted) {
                    val prefixesRolledBack =
                        blocklistStore.replaceBlockedPrefixes(previousPrefixes)
                    if (!prefixesRolledBack) {
                        error("SETTINGS_RESTORE_FAILED_PREFIX_ROLLBACK_FAILED")
                    }
                    error("SETTINGS_RESTORE_FAILED")
                }
                familySafetyProfile = restored.familySafetyProfile
                onThemeModeChange(restored.themeMode)
                intervalHours = restored.osintRefreshIntervalHours
                notificationsEnabled = restored.osintNotificationsEnabled &&
                    (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED)
                val schedulingFailed = runCatching {
                    WorkScheduler.schedule(context, restored.osintRefreshIntervalHours)
                }.isFailure
                if (schedulingFailed) {
                    "Sauvegarde restaurée, mais la planification de veille devra être resynchronisée au prochain démarrage."
                } else {
                    "Sauvegarde restaurée. Les numéros exacts bloqués ne sont pas importés car leur protection cryptographique est liée à l’appareil."
                }
            }.getOrElse { failure ->
                if (failure.message == "SETTINGS_RESTORE_FAILED_PREFIX_ROLLBACK_FAILED") {
                    "Restauration interrompue : vérifiez les règles de préfixe bloquées avant de continuer."
                } else {
                    "Sauvegarde invalide, trop volumineuse ou impossible à restaurer."
                }
            }
        }
    }

    val versionName = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (_: PackageManager.NameNotFoundException) {
            "?"
        }
    }

    Scaffold(
        topBar = {
            SentinelTopBar(
                title = stringResource(R.string.settings_title),
                subtitle = "Préférences locales & confidentialité",
                onBack = { navController.navigateUp() }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SentinelSectionHeader(
                title = stringResource(R.string.settings_theme_title),
                subtitle = "Choisissez l’apparence de Sentinel sans modifier les fonctions de sécurité."
            )
            Column(Modifier.selectableGroup()) {
                ThemeOptionRow(
                    label = stringResource(R.string.settings_theme_system),
                    selected = themeMode == ThemeMode.SYSTEM,
                    onClick = { onThemeModeChange(ThemeMode.SYSTEM) }
                )
                ThemeOptionRow(
                    label = stringResource(R.string.settings_theme_light),
                    selected = themeMode == ThemeMode.LIGHT,
                    onClick = { onThemeModeChange(ThemeMode.LIGHT) }
                )
                ThemeOptionRow(
                    label = stringResource(R.string.settings_theme_dark),
                    selected = themeMode == ThemeMode.DARK,
                    onClick = { onThemeModeChange(ThemeMode.DARK) }
                )
            }

            HorizontalDivider()

            SentinelSectionHeader(
                title = "Protection assistée",
                subtitle = "Renforce les avertissements locaux avant certains rappels à risque."
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Mode assisté pour les appels", fontWeight = FontWeight.Bold)
                    Text(
                        if (familySafetyProfile == FamilySafetyPolicy.Profile.ASSISTED)
                            "Actif · confirmation supplémentaire sur certains numéros à tarification potentiellement élevée."
                        else
                            "Inactif · comportement standard du composeur.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = familySafetyProfile == FamilySafetyPolicy.Profile.ASSISTED,
                    onCheckedChange = { enabled ->
                        familySafetyProfile = if (enabled) {
                            FamilySafetyPolicy.Profile.ASSISTED
                        } else {
                            FamilySafetyPolicy.Profile.STANDARD
                        }
                        settingsStore.familySafetyProfile = familySafetyProfile
                    }
                )
            }
            Text(
                "Ce mode reste local : il ne crée aucun compte supervisé, n’espionne aucun autre appareil et n’interfère jamais avec le routage d’urgence Android.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            SentinelSectionHeader(
                title = stringResource(R.string.settings_osint_section),
                subtitle = stringResource(R.string.settings_osint_description)
            )
            SettingsStore.SUPPORTED_INTERVALS_HOURS.forEach { hours ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = intervalHours == hours,
                            onClick = {
                                intervalHours = hours
                                settingsStore.osintRefreshIntervalHours = hours
                                WorkScheduler.schedule(context, hours)
                            },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = intervalHours == hours,
                        onClick = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = intervalLabel(hours))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_osint_notifications),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = notificationsEnabled,
                    onCheckedChange = { enabled ->
                        if (!enabled) {
                            notificationsEnabled = false
                            settingsStore.osintNotificationsEnabled = false
                        } else if (
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            notificationsEnabled = true
                            settingsStore.osintNotificationsEnabled = true
                            statusMessageRes = R.string.settings_osint_notifications_enabled
                        }
                    }
                )
            }
            Text(
                text = stringResource(R.string.settings_osint_privacy_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            SentinelSectionHeader(
                title = "Sauvegarde locale",
                subtitle = "Export et restauration explicites des préférences restaurables de Sentinel."
            )
            Text(
                "La sauvegarde contient le thème, les préférences de protection, la veille locale et les préfixes bloqués. Elle n’exporte ni contacts, ni SMS/MMS, ni historique d’appels, ni journaux. Les numéros exacts bloqués sont exclus car leurs empreintes sont liées à la clé sécurisée de cet appareil.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { createBackupLauncher.launch("sentinel-preferences-backup.json") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Exporter une sauvegarde") }
            OutlinedButton(
                onClick = { restoreBackupLauncher.launch(arrayOf("application/json", "text/plain")) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Restaurer une sauvegarde") }
            backupStatus?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            SentinelSectionHeader(
                title = stringResource(R.string.settings_data_title),
                subtitle = "Nettoyage explicite des données locales contrôlées par Sentinel."
            )
            OutlinedButton(
                onClick = {
                    logger.clearLogs()
                    statusMessageRes = R.string.settings_reset_logs_done
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.settings_reset_logs)) }
            OutlinedButton(
                onClick = {
                    osintFeedCache.clear()
                    statusMessageRes = R.string.settings_clear_osint_cache_done
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.settings_clear_osint_cache)) }
            statusMessageRes?.let {
                Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider()

            SentinelSectionHeader(
                title = stringResource(R.string.settings_sync_title),
                subtitle = "La synchronisation reste verrouillée tant que l’autorité de signature n’est pas réellement provisionnée."
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_sync_switch), modifier = Modifier.weight(1f))
                Switch(
                    checked = ruleSyncEnabled,
                    enabled = ruleSyncAvailable,
                    onCheckedChange = { enabled ->
                        if (ruleSyncAvailable) {
                            ruleSyncEnabled = enabled
                            settingsStore.setRuleSyncEnabled(enabled)
                        }
                    }
                )
            }
            Text(
                text = if (ruleSyncAvailable) {
                    stringResource(R.string.settings_sync_description)
                } else {
                    "VERROUILLÉ — aucune autorité de signature de production n’est provisionnée. La synchronisation reste inactive."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            SentinelSectionHeader(
                title = stringResource(R.string.settings_about_title),
                subtitle = "Version installée et documents publiés."
            )
            Text(
                text = stringResource(R.string.settings_version, versionName),
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Informations légales et confidentialité",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Consultez les documents juridiques publiés avec la version du service. Les CGV restent préparatoires tant qu’aucun paiement réel n’est activé.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LegalLinkButton("Mentions légales") {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sentinelquantumvanguardaipro.pages.dev/public/legal.html")))
            }
            LegalLinkButton("Conditions générales d’utilisation (CGU)") {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sentinelquantumvanguardaipro.pages.dev/public/terms.html")))
            }
            LegalLinkButton("Conditions générales de vente (CGV)") {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sentinelquantumvanguardaipro.pages.dev/public/cgv.html")))
            }
            LegalLinkButton("Politique de confidentialité") {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sentinelquantumvanguardaipro.pages.dev/public/privacy.html")))
            }
        }
    }
}

@Composable
private fun ThemeOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun readBoundedBackupText(
    reader: java.io.Reader,
    maxChars: Int = 128_000
): String {
    val result = StringBuilder()
    val buffer = CharArray(4_096)
    while (true) {
        val count = reader.read(buffer)
        if (count < 0) break
        if (result.length + count > maxChars) error("BACKUP_TOO_LARGE")
        result.append(buffer, 0, count)
    }
    return result.toString()
}

@Composable
private fun intervalLabel(hours: Int): String = when (hours) {
    SettingsStore.INTERVAL_NEVER -> stringResource(R.string.settings_osint_interval_never)
    else -> stringResource(R.string.settings_osint_interval_hours, hours)
}


@Composable
private fun LegalLinkButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label)
    }
}
