package com.sentinel.quantum.ui.screens

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.sentinel.quantum.R
import com.sentinel.quantum.background.WorkScheduler
import com.sentinel.quantum.data.OsintFeedCache
import com.sentinel.quantum.data.SettingsStore
import com.sentinel.quantum.data.ThemeMode
import com.sentinel.quantum.security.LocalLogger

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
    val osintFeedCache = remember(context) { OsintFeedCache(context) }
    var ruleSyncEnabled by remember { mutableStateOf(settingsStore.isRuleSyncEnabled()) }
    var intervalHours by remember { mutableStateOf(settingsStore.osintRefreshIntervalHours) }
    var notificationsEnabled by remember { mutableStateOf(settingsStore.osintNotificationsEnabled) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val resetLogsDoneText = stringResource(R.string.settings_reset_logs_done)
    val clearOsintCacheDoneText = stringResource(R.string.settings_clear_osint_cache_done)

    val versionName = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (_: PackageManager.NameNotFoundException) {
            "?"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
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
            Text(
                text = stringResource(R.string.settings_theme_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
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

            Text(
                text = stringResource(R.string.settings_osint_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.settings_osint_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    onCheckedChange = {
                        notificationsEnabled = it
                        settingsStore.osintNotificationsEnabled = it
                    }
                )
            }
            Text(
                text = stringResource(R.string.settings_osint_privacy_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            Text(
                text = stringResource(R.string.settings_data_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(
                onClick = {
                    logger.clearLogs()
                    statusMessage = resetLogsDoneText
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.settings_reset_logs)) }
            OutlinedButton(
                onClick = {
                    osintFeedCache.clear()
                    statusMessage = clearOsintCacheDoneText
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.settings_clear_osint_cache)) }
            statusMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider()

            Text(
                text = stringResource(R.string.settings_sync_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_sync_switch), modifier = Modifier.weight(1f))
                Switch(
                    checked = ruleSyncEnabled,
                    onCheckedChange = { enabled ->
                        ruleSyncEnabled = enabled
                        settingsStore.setRuleSyncEnabled(enabled)
                    }
                )
            }
            Text(
                text = stringResource(R.string.settings_sync_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            Text(
                text = stringResource(R.string.settings_about_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.settings_version, versionName),
                style = MaterialTheme.typography.bodyMedium
            )
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

@Composable
private fun intervalLabel(hours: Int): String = when (hours) {
    SettingsStore.INTERVAL_NEVER -> stringResource(R.string.settings_osint_interval_never)
    else -> stringResource(R.string.settings_osint_interval_hours, hours)
}
