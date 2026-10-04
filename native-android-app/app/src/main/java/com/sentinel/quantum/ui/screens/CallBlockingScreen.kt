package com.sentinel.quantum.ui.screens

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.sentinel.quantum.R
import com.sentinel.quantum.data.SettingsStore
import com.sentinel.quantum.security.AndroidRoleReadPolicy
import com.sentinel.quantum.security.CallBlocklistStore
import com.sentinel.quantum.security.CallRuleSyncClient
import com.sentinel.quantum.security.CallRuleSyncConfig
import com.sentinel.quantum.security.CallScreeningActivationPolicy
import com.sentinel.quantum.security.OkHttpCallRulePackageTransport
import com.sentinel.quantum.security.PhoneCountryPrefixCatalog
import com.sentinel.quantum.security.SignedCallRulePackageVerifier
import com.sentinel.quantum.ui.design.SentinelD1
import com.sentinel.quantum.ui.design.SentinelHero
import com.sentinel.quantum.ui.design.SentinelTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallBlockingScreen(navController: NavController) {
    val context = LocalContext.current
    val store = remember(context) { CallBlocklistStore(context) }
    val settingsStore = remember(context) { SettingsStore(context) }
    var snapshot by remember { mutableStateOf(store.snapshot()) }
    var number by remember { mutableStateOf("") }
    var prefix by remember { mutableStateOf("") }
    var prefixMenuExpanded by remember { mutableStateOf(false) }
    var prefixSearch by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var screeningState by remember { mutableStateOf(CallScreeningActivationPolicy.read(context)) }
    val roleHeld = screeningState == CallScreeningActivationPolicy.State.HELD
    var contactsAllowed by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var isSyncing by remember { mutableStateOf(false) }
    var syncStatus by remember { mutableStateOf<String?>(null) }
    val syncEnabledByUser = remember { settingsStore.isRuleSyncEnabled() }
    var remoteEnrichmentEnabled by remember {
        mutableStateOf(settingsStore.callerReputationEnrichmentEnabled)
    }
    val scope = rememberCoroutineScope()
    val addedText = stringResource(R.string.call_blocking_added)
    val invalidText = stringResource(R.string.call_blocking_invalid)
    val clearedText = stringResource(R.string.call_blocking_cleared)
    val clearFailedText = stringResource(R.string.call_blocking_clear_failed)
    val prefixAddedText = stringResource(R.string.call_blocking_prefix_added)
    val prefixInvalidText = stringResource(R.string.call_blocking_prefix_invalid)
    val syncFailedText = stringResource(R.string.call_blocking_sync_failed)
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        screeningState = CallScreeningActivationPolicy.read(context)
    }
    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        contactsAllowed = granted
    }

    Scaffold(
        topBar = {
            SentinelTopBar(
                title = stringResource(R.string.call_blocking_title),
                subtitle = "Filtrage et identification, sur votre appareil",
                onBack = { navController.navigateUp() }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SentinelHero(
                eyebrow = "Appels",
                title = "Protégez vos appels",
                body = stringResource(R.string.call_blocking_intro),
                badges = listOf(
                    (if (roleHeld) "Protection active" else "Protection à activer") to
                        (if (roleHeld) SentinelD1.Success else SentinelD1.Warning),
                    "Règles privées" to SentinelD1.Cyan
                )
            )
            Text(
                if (roleHeld) stringResource(R.string.call_blocking_role_on) else stringResource(R.string.call_blocking_role_off),
                fontWeight = FontWeight.Bold
            )
            if (screeningState == CallScreeningActivationPolicy.State.AVAILABLE_NOT_HELD) {
                Button(
                    onClick = {
                        requestCallScreeningActivation(context)?.let(roleLauncher::launch)
                            ?: run { screeningState = CallScreeningActivationPolicy.read(context) }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.call_blocking_enable_role)) }
            } else if (screeningState == CallScreeningActivationPolicy.State.UNAVAILABLE) {
                Text(stringResource(R.string.call_blocking_role_unsupported))
            }

            HorizontalDivider()
            Text(stringResource(R.string.caller_id_contacts_title), fontWeight = FontWeight.Bold)
            Text(
                stringResource(
                    if (contactsAllowed) R.string.caller_id_contacts_enabled
                    else R.string.caller_id_contacts_description
                ),
                style = MaterialTheme.typography.bodySmall
            )
            if (!contactsAllowed) {
                Button(
                    onClick = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.caller_id_contacts_enable)) }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.caller_id_remote_title), fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.caller_id_remote_description),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = remoteEnrichmentEnabled,
                    onCheckedChange = { enabled ->
                        remoteEnrichmentEnabled = enabled
                        settingsStore.callerReputationEnrichmentEnabled = enabled
                    }
                )
            }

            HorizontalDivider()
            Text(stringResource(R.string.call_blocking_exact_title), fontWeight = FontWeight.Bold)
            OutlinedTextField(
                number,
                { number = it.take(64) },
                label = { Text(stringResource(R.string.call_blocking_number_label)) },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    status = if (store.addBlockedNumber(number)) addedText else invalidText
                    snapshot = store.snapshot()
                    number = ""
                },
                enabled = number.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.call_blocking_add)) }
            Text(stringResource(R.string.call_blocking_exact_count, snapshot.blockedNumberHashes.size))
            if (snapshot.blockedNumberHashes.isNotEmpty()) {
                TextButton(
                    onClick = {
                        status = if (store.clearBlockedNumbers()) clearedText else clearFailedText
                        snapshot = store.snapshot()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.call_blocking_clear_exact)) }
            }

            HorizontalDivider()
            Text("Préfixes bloqués par vous", fontWeight = FontWeight.Bold)
            Text(
                "Ajoutez un indicatif international ou un préfixe plus précis. Ces règles bloquent réellement les appels correspondants sur cet appareil.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = {
                    prefixMenuExpanded = !prefixMenuExpanded
                    if (!prefixMenuExpanded) prefixSearch = ""
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (prefixMenuExpanded) "Fermer la liste des pays" else "Choisir un pays ou une zone")
            }
            if (prefixMenuExpanded) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = prefixSearch,
                            onValueChange = { prefixSearch = it.take(64) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Rechercher un pays ou un indicatif") },
                            placeholder = { Text("France, Guadeloupe, +590…") },
                            singleLine = true
                        )
                        val countryMatches = remember(prefixSearch) {
                            if (prefixSearch.isBlank()) {
                                PhoneCountryPrefixCatalog.frequentEntries
                            } else {
                                PhoneCountryPrefixCatalog.search(prefixSearch, limit = 18)
                            }
                        }
                        Text(
                            if (prefixSearch.isBlank()) "Zones fréquentes" else "${countryMatches.size} résultat(s)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (countryMatches.isEmpty()) {
                            Text(
                                "Aucune zone trouvée. Vous pouvez saisir le préfixe manuellement ci-dessous.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        countryMatches.take(18).forEach { entry ->
                            TextButton(
                                onClick = {
                                    prefix = entry.prefix
                                    prefixMenuExpanded = false
                                    prefixSearch = ""
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        listOf(entry.flag, entry.label)
                                            .filter { it.isNotBlank() }
                                            .joinToString(" ")
                                            .take(72),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(entry.prefix, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            OutlinedTextField(
                value = prefix,
                onValueChange = { prefix = it.take(24) },
                label = { Text(stringResource(R.string.call_blocking_prefix_label)) },
                placeholder = { Text("+590 ou +33948") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true
            )
            Button(
                onClick = {
                    status = if (store.addBlockedPrefix(prefix)) prefixAddedText else prefixInvalidText
                    snapshot = store.snapshot()
                    prefix = ""
                },
                enabled = prefix.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.call_blocking_add_prefix)) }

            val manualPrefixes = store.manualBlockedPrefixes()
            Text(
                "${manualPrefixes.size} préfixe(s) personnel(s) bloqué(s)",
                style = MaterialTheme.typography.bodySmall
            )
            if (manualPrefixes.isEmpty()) {
                Text(
                    "Aucun préfixe personnel bloqué.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            manualPrefixes.sorted().forEach { value ->
                val geographicLabel = PhoneCountryPrefixCatalog.find(value)?.label
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(value, fontWeight = FontWeight.Bold)
                            Text(
                                geographicLabel ?: "Préfixe personnalisé",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = {
                                if (store.removeBlockedPrefix(value)) {
                                    snapshot = store.snapshot()
                                    status = "Règle de préfixe retirée."
                                }
                            }
                        ) { Text(stringResource(R.string.call_blocking_remove)) }
                    }
                }
            }

            ArcepVerifiedPrefixSection(store) { updatedSnapshot, message ->
                snapshot = updatedSnapshot
                status = message
            }

            HorizontalDivider()
            Text("Règles Sentinel signées", fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.call_blocking_signed_active, snapshot.signedSilencePrefixes.size),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Ces règles de réputation peuvent seulement mettre un appel en silencieux ; elles ne deviennent jamais un blocage automatique à elles seules.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(
                stringResource(R.string.call_blocking_reputation_note),
                style = MaterialTheme.typography.bodySmall
            )

            HorizontalDivider()
            Text(stringResource(R.string.call_blocking_sync_title), fontWeight = FontWeight.Bold)
            if (CallRuleSyncConfig.SYNC_ENABLED && syncEnabledByUser) {
                Button(
                    onClick = {
                        scope.launch {
                            isSyncing = true
                            try {
                                syncStatus = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val verifier = SignedCallRulePackageVerifier(
                                            CallRuleSyncConfig.TRUSTED_KEYS,
                                            CallRuleSyncConfig.EXPECTED_ISSUER_ID
                                        )
                                        val transport = OkHttpCallRulePackageTransport(
                                            CallRuleSyncConfig.ENDPOINT,
                                            CallRuleSyncConfig.ALLOWED_HOSTS
                                        )
                                        CallRuleSyncClient(transport, store, verifier).synchronize()
                                    }.fold(
                                        onSuccess = { result -> result.reason },
                                        onFailure = { syncFailedText }
                                    )
                                }
                                snapshot = store.snapshot()
                            } finally {
                                isSyncing = false
                            }
                        }
                    },
                    enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isSyncing) stringResource(R.string.call_blocking_sync_checking) else stringResource(R.string.call_blocking_sync_check))
                }
                syncStatus?.let {
                    Text(
                        stringResource(R.string.call_blocking_sync_result, it),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else if (!CallRuleSyncConfig.SYNC_ENABLED || CallRuleSyncConfig.TRUSTED_KEYS.isEmpty()) {
                Text(
                    stringResource(R.string.call_blocking_sync_disabled_config),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    stringResource(R.string.call_blocking_sync_disabled_setting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun requestCallScreeningActivation(context: Context): Intent? {
    if (CallScreeningActivationPolicy.read(context) != CallScreeningActivationPolicy.State.AVAILABLE_NOT_HELD) {
        return null
    }
    return AndroidRoleReadPolicy.readOrNull {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
        } else {
            val manager = context.getSystemService(RoleManager::class.java)
            if (!manager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) ||
                manager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
            ) {
                null
            } else {
                manager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
            }
        }
    }
}
