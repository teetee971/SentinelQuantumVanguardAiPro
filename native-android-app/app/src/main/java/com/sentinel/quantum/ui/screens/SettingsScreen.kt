package com.sentinel.quantum.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import java.security.MessageDigest
import android.os.Build
import androidx.activity.ComponentActivity
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
import com.sentinel.quantum.auth.Auth0SessionController
import com.sentinel.quantum.auth.Auth0UserSummary
import com.sentinel.quantum.auth.auth0ErrorMessage
import com.sentinel.quantum.VoiceStudioActivity
import com.sentinel.quantum.background.WorkScheduler
import com.sentinel.quantum.data.OsintFeedCache
import com.sentinel.quantum.data.SettingsStore
import com.sentinel.quantum.data.SentinelPreferencesBackup
import com.sentinel.quantum.data.SentinelUninstallFlow
import com.sentinel.quantum.data.ThemeMode
import com.sentinel.quantum.security.LocalLogger
import com.sentinel.quantum.security.CallBlocklistStore
import com.sentinel.quantum.security.CallRuleSyncConfig
import com.sentinel.quantum.security.FamilySafetyPolicy
import com.sentinel.quantum.security.SentinelDeviceIdentity
import com.sentinel.quantum.ui.design.SentinelTopBar
import com.sentinel.quantum.ui.design.SentinelSectionHeader
import com.sentinel.quantum.ui.design.SentinelCoreModule
import com.sentinel.quantum.ui.design.SentinelQuantumCore
import com.sentinel.quantum.ui.design.SentinelStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val authController = remember(context) { Auth0SessionController(context) }
    val voiceDeviceId = remember(context) { SentinelDeviceIdentity.current(context) }
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
    var showUninstallDialog by remember { mutableStateOf(false) }
    var uninstallAfterBackup by remember { mutableStateOf(false) }
    var externalLinkStatus by remember { mutableStateOf<String?>(null) }
    var authUser by remember { mutableStateOf<Auth0UserSummary?>(null) }
    var authLoading by remember { mutableStateOf(true) }
    var authErrorMessage by remember { mutableStateOf<String?>(null) }
    val ioScope = rememberCoroutineScope()

    fun openExternalPage(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onSuccess { externalLinkStatus = null }
            .onFailure { externalLinkStatus = "Impossible d’ouvrir cette page sur cet appareil." }
    }
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
            val pendingUninstall = uninstallAfterBackup
            uninstallAfterBackup = false
            ioScope.launch {
                val exportMessage = withContext(Dispatchers.IO) {
                    runCatching {
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
                val exportSucceeded = exportMessage == "Sauvegarde locale exportée."
                backupStatus = exportMessage
                if (SentinelUninstallFlow.shouldOpenUninstallAfterBackup(pendingUninstall, exportSucceeded)) {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_DELETE,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }.onFailure {
                        backupStatus = "Sauvegarde exportée, mais la confirmation de désinstallation n’a pas pu être ouverte."
                    }
                }
            }
        } else {
            uninstallAfterBackup = false
        }
    }
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            ioScope.launch {
                val (restored, message) = withContext(Dispatchers.IO) {
                    runCatching {
                        val input = context.contentResolver.openInputStream(uri)
                            ?: error("BACKUP_INPUT_UNAVAILABLE")
                        val raw = input.bufferedReader(Charsets.UTF_8).use { reader ->
                            readBoundedBackupText(reader)
                        }
                        val decoded = SentinelPreferencesBackup.decode(raw)
                            ?: error("BACKUP_INVALID")
                        val previousPrefixes = blocklistStore.snapshot().blockedPrefixes
                        if (!blocklistStore.replaceBlockedPrefixes(decoded.blockedPrefixes)) {
                            error("PREFIX_RESTORE_FAILED")
                        }
                        val settingsCommitted = settingsStore.applyRestorablePreferences(
                            SettingsStore.RestorablePreferences(
                                themeMode = decoded.themeMode,
                                protectionMode = decoded.protectionMode,
                                familySafetyProfile = decoded.familySafetyProfile,
                                callerReputationEnrichmentEnabled =
                                    decoded.callerReputationEnrichmentEnabled,
                                osintRefreshIntervalHours = decoded.osintRefreshIntervalHours,
                                osintNotificationsEnabled = decoded.osintNotificationsEnabled,
                                smsNotificationPreviewEnabled = decoded.smsNotificationPreviewEnabled
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
                        val schedulingFailed = runCatching {
                            WorkScheduler.schedule(context, decoded.osintRefreshIntervalHours)
                        }.isFailure
                        decoded to if (schedulingFailed) {
                            "Sauvegarde restaurée, mais la planification de veille devra être resynchronisée au prochain démarrage."
                        } else {
                            "Sauvegarde restaurée. Les numéros exacts bloqués ne sont pas importés car leur protection cryptographique est liée à l’appareil."
                        }
                    }.getOrElse { failure ->
                        null to if (failure.message == "SETTINGS_RESTORE_FAILED_PREFIX_ROLLBACK_FAILED") {
                            "Restauration interrompue : vérifiez les règles de préfixe bloquées avant de continuer."
                        } else {
                            "Sauvegarde invalide, trop volumineuse ou impossible à restaurer."
                        }
                    }
                }
                if (restored != null) {
                    familySafetyProfile = restored.familySafetyProfile
                    onThemeModeChange(restored.themeMode)
                    intervalHours = restored.osintRefreshIntervalHours
                    notificationsEnabled = restored.osintNotificationsEnabled &&
                        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED)
                }
                backupStatus = message
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

    LaunchedEffect(authController) {
        authController.restore(
            onSuccess = {
                authUser = it
                authLoading = false
            },
            onFailure = {
                authUser = null
                authLoading = false
            }
        )
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
            SentinelQuantumCore(
                modules = listOf(
                    SentinelCoreModule(
                        id = "account",
                        label = "Compte",
                        status = when {
                            authLoading -> SentinelStatus.WARN
                            authUser != null -> SentinelStatus.OK
                            else -> SentinelStatus.LOCKED
                        },
                        enabled = false
                    ),
                    SentinelCoreModule(
                        id = "appearance",
                        label = "Apparence",
                        status = SentinelStatus.OK,
                        enabled = false
                    ),
                    SentinelCoreModule(
                        id = "notifications",
                        label = "Notifications",
                        status = if (notificationsEnabled) SentinelStatus.OK else SentinelStatus.LIMITED,
                        enabled = false
                    ),
                    SentinelCoreModule(
                        id = "privacy",
                        label = "Confidentialité",
                        status = SentinelStatus.OK,
                        enabled = false
                    ),
                    SentinelCoreModule(
                        id = "voice",
                        label = "Studio voix · TEST",
                        status = SentinelStatus.TEST,
                        enabled = false
                    ),
                    SentinelCoreModule(
                        id = "diagnostic",
                        label = "Diagnostic · INCONNU",
                        status = SentinelStatus.UNKNOWN,
                        enabled = false
                    )
                ),
                modifier = Modifier.fillMaxWidth()
            )
            SentinelSectionHeader(
                title = stringResource(R.string.settings_auth_title),
                subtitle = stringResource(R.string.settings_auth_description)
            )
            when {
                authLoading -> Text(stringResource(R.string.settings_auth_loading))
                authUser == null -> {
                    Button(
                        onClick = {
                            val currentActivity = activity ?: return@Button
                            authErrorMessage = null
                            authController.login(
                                currentActivity,
                                onSuccess = { authUser = it },
                                onFailure = { message ->
                                    authUser = null
                                    authErrorMessage = auth0ErrorMessage(message)
                                }
                            )
                        },
                        enabled = activity != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_auth_login))
                    }
                }
                else -> {
                    Text(
                        text = stringResource(
                            R.string.settings_auth_logged_in_as,
                            authUser?.email ?: stringResource(R.string.settings_auth_no_email)
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    voiceDeviceId?.let { deviceId ->
                        Text(
                            text = stringResource(R.string.settings_auth_device_id, deviceId),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = {
                                context.getSystemService(ClipboardManager::class.java)
                                    ?.setPrimaryClip(
                                        ClipData.newPlainText(
                                            "Sentinel device ID",
                                            deviceId
                                        )
                                    )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.settings_auth_copy_device_id))
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val currentActivity = activity ?: return@OutlinedButton
                            authErrorMessage = null
                            authController.logout(
                                currentActivity,
                                onSuccess = { authUser = null },
                                onFailure = { message -> authErrorMessage = auth0ErrorMessage(message) }
                            )
                        },
                        enabled = activity != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_auth_logout))
                    }
                }
            }
            authErrorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            HorizontalDivider()

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
                title = "Studio voix",
                subtitle = "Aperçu local du moteur vocal · transformation en appel Sentinel obligatoire avant livraison de l’add-on."
            )
            ElevatedCard(
                onClick = {
                    context.startActivity(Intent(context, VoiceStudioActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Tester ma voix", fontWeight = FontWeight.Bold)
                    Text(
                        "Essayez gratuitement les rendus Naturelle, Grave et Aiguë. Le même moteur temps réel est réservé au chemin média des appels Sentinel contrôlés ; l’aperçu local n’est jamais injecté dans un appel SIM natif.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Appels transformés : moteur + client WebRTC intégrés · serveur/token/PSTN et validation physique encore requis avant activation payante.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

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
                onClick = { createBackupLauncher.launch("Sentinel-backup.json") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Exporter une sauvegarde") }
            OutlinedButton(
                onClick = { restoreBackupLauncher.launch(arrayOf("application/json", "text/plain")) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Restaurer une sauvegarde") }
            OutlinedButton(
                onClick = { showUninstallDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Sauvegarder puis désinstaller") }
            Text(
                "Cette action exporte d’abord les préférences restaurables, puis ouvre la confirmation Android. Une désinstallation lancée directement depuis les réglages ne peut pas être interceptée par l’application.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                    ioScope.launch {
                        withContext(Dispatchers.IO) { logger.clearLogs() }
                        statusMessageRes = R.string.settings_reset_logs_done
                    }
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
                subtitle = if (ruleSyncAvailable) {
                    "Mise à jour sécurisée des règles Sentinel signées."
                } else {
                    "Fonction indisponible dans cette version tant qu’une signature de production vérifiable n’est pas provisionnée."
                }
            )
            if (ruleSyncAvailable) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_sync_switch), fontWeight = FontWeight.SemiBold)
                        Text(
                            if (ruleSyncEnabled) "Activée" else "Désactivée",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = ruleSyncEnabled,
                        onCheckedChange = { enabled ->
                            ruleSyncEnabled = enabled
                            settingsStore.setRuleSyncEnabled(enabled)
                        }
                    )
                }
                Text(
                    stringResource(R.string.settings_sync_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Synchronisation sécurisée indisponible", fontWeight = FontWeight.Bold)
                        Text(
                            "Aucune règle distante n’est téléchargée. Sentinel conserve uniquement ses règles locales jusqu’à ce qu’une autorité de signature de production soit réellement configurée et vérifiée.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

            }

            HorizontalDivider()

            SentinelSectionHeader(
                title = stringResource(R.string.settings_about_title),
                subtitle = "Version installée et documents publiés."
            )
            Text(
                text = stringResource(R.string.settings_version, versionName),
                style = MaterialTheme.typography.bodyMedium
            )

            val installedSignerSha256 = remember(context) {
                runCatching {
                    val pm = context.packageManager
                    val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        pm.getPackageInfo(
                            context.packageName,
                            PackageManager.GET_SIGNING_CERTIFICATES
                        ).signingInfo?.apkContentsSigners
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
                    }
                    signatures?.firstOrNull()?.toByteArray()?.let { certificate ->
                        MessageDigest.getInstance("SHA-256").digest(certificate)
                            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                    }
                }.getOrNull()
            }
            Text(
                text = "Certificat APK installé (SHA-256) : " +
                    (installedSignerSha256 ?: "indisponible"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                openExternalPage("https://sentinelquantumvanguardaipro.pages.dev/public/legal.html")
            }
            LegalLinkButton("Conditions générales d’utilisation (CGU)") {
                openExternalPage("https://sentinelquantumvanguardaipro.pages.dev/public/terms.html")
            }
            LegalLinkButton("Conditions générales de vente (CGV)") {
                openExternalPage("https://sentinelquantumvanguardaipro.pages.dev/public/cgv.html")
            }
            LegalLinkButton("Politique de confidentialité") {
                openExternalPage("https://sentinelquantumvanguardaipro.pages.dev/public/privacy.html")
            }
            externalLinkStatus?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showUninstallDialog) {
        AlertDialog(
            onDismissRequest = { showUninstallDialog = false },
            title = { Text("Sauvegarder avant désinstallation ?") },
            text = {
                Text(
                    "Un fichier JSON sera créé avec les préférences restaurables. Les jetons Auth0, les contacts, les SMS/MMS et l’historique d’appels ne seront pas exportés."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUninstallDialog = false
                        uninstallAfterBackup = true
                        createBackupLauncher.launch(SentinelUninstallFlow.BACKUP_FILE_NAME)
                    }
                ) { Text("Sauvegarder puis continuer") }
            },
            dismissButton = {
                TextButton(onClick = { showUninstallDialog = false }) { Text("Annuler") }
            }
        )
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
