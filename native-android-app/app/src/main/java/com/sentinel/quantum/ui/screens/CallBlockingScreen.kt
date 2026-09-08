package com.sentinel.quantum.ui.screens

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.R
import com.sentinel.quantum.data.SettingsStore
import com.sentinel.quantum.security.CallBlocklistStore
import com.sentinel.quantum.security.CallRuleSyncClient
import com.sentinel.quantum.security.CallRuleSyncConfig
import com.sentinel.quantum.security.OkHttpCallRulePackageTransport
import com.sentinel.quantum.security.SignedCallRulePackageVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallBlockingScreen(navController: NavController) {
    val context = LocalContext.current
    val store = remember(context) { CallBlocklistStore(context) }
    val settingsStore = remember(context) { SettingsStore(context) }
    val roleSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    var snapshot by remember { mutableStateOf(store.snapshot()) }
    var number by remember { mutableStateOf("") }
    var prefix by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var roleHeld by remember { mutableStateOf(isCallScreeningRoleHeld(context)) }
    var isSyncing by remember { mutableStateOf(false) }
    var syncStatus by remember { mutableStateOf<String?>(null) }
    val syncEnabledByUser = remember { settingsStore.isRuleSyncEnabled() }
    val blockedNumberAdded = stringResource(R.string.call_blocking_added)
    val blockedNumberInvalid = stringResource(R.string.call_blocking_invalid)
    val blockedNumbersCleared = stringResource(R.string.call_blocking_cleared)
    val blockedNumbersClearFailed = stringResource(R.string.call_blocking_clear_failed)
    val blockedPrefixAdded = stringResource(R.string.call_blocking_prefix_added)
    val blockedPrefixInvalid = stringResource(R.string.call_blocking_prefix_invalid)
    val syncFailed = stringResource(R.string.call_blocking_sync_failed)
    val scope = rememberCoroutineScope()
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        roleHeld = result.resultCode == Activity.RESULT_OK && isCallScreeningRoleHeld(context)
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.call_blocking_title)) }, navigationIcon = {
        IconButton(onClick = { navController.navigateUp() }) {
            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back))
        }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.call_blocking_intro))
            Text(
                if (roleHeld) stringResource(R.string.call_blocking_role_on) else stringResource(R.string.call_blocking_role_off),
                fontWeight = FontWeight.Bold
            )
            if (isCallScreeningRoleAvailable(context) && !roleHeld) {
                Button(onClick = { requestCallScreeningRole(context)?.let { intent -> roleLauncher.launch(intent) } },
                    modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.call_blocking_enable_role)) }
            } else if (!roleSupported) {
                Text(stringResource(R.string.call_blocking_role_unsupported))
            }

            HorizontalDivider()
            Text(stringResource(R.string.call_blocking_exact_title), fontWeight = FontWeight.Bold)
            OutlinedTextField(number, { number = it.take(64) }, label = { Text(stringResource(R.string.call_blocking_number_label)) }, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                status = if (store.addBlockedNumber(number)) blockedNumberAdded else blockedNumberInvalid
                snapshot = store.snapshot(); number = ""
            }, enabled = number.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.call_blocking_add)) }
            Text(stringResource(R.string.call_blocking_exact_count, snapshot.blockedNumberHashes.size))
            if (snapshot.blockedNumberHashes.isNotEmpty()) {
                TextButton(onClick = {
                    status = if (store.clearBlockedNumbers()) blockedNumbersCleared else blockedNumbersClearFailed
                    snapshot = store.snapshot()
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.call_blocking_clear_exact)) }
            }

            HorizontalDivider()
            Text(stringResource(R.string.call_blocking_prefix_title), fontWeight = FontWeight.Bold)
            OutlinedTextField(prefix, { prefix = it.take(24) }, label = { Text(stringResource(R.string.call_blocking_prefix_label)) }, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                status = if (store.addBlockedPrefix(prefix)) blockedPrefixAdded else blockedPrefixInvalid
                snapshot = store.snapshot(); prefix = ""
            }, enabled = prefix.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.call_blocking_add_prefix)) }
            snapshot.blockedPrefixes.sorted().forEach { value ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(value)
                    TextButton(onClick = { store.removeBlockedPrefix(value); snapshot = store.snapshot() }) { Text(stringResource(R.string.call_blocking_remove)) }
                }
            }
            Text(stringResource(R.string.call_blocking_signed_active, snapshot.signedSilencePrefixes.size),
                style = MaterialTheme.typography.bodySmall)
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(stringResource(R.string.call_blocking_reputation_note),
                style = MaterialTheme.typography.bodySmall)

            HorizontalDivider()
            Text(stringResource(R.string.call_blocking_sync_title), fontWeight = FontWeight.Bold)
            if (CallRuleSyncConfig.SYNC_ENABLED && syncEnabledByUser) {
                Button(onClick = {
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
                                    onFailure = { syncFailed }
                                )
                            }
                            snapshot = store.snapshot()
                        } finally {
                            isSyncing = false
                        }
                    }
                }, enabled = !isSyncing, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isSyncing) stringResource(R.string.call_blocking_sync_checking) else stringResource(R.string.call_blocking_sync_check))
                }
                syncStatus?.let { Text(stringResource(R.string.call_blocking_sync_result, it), style = MaterialTheme.typography.bodySmall) }
            } else if (!syncEnabledByUser) {
                Text(
                    stringResource(R.string.call_blocking_sync_disabled_setting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    stringResource(R.string.call_blocking_sync_disabled_config),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun isCallScreeningRoleHeld(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    return context.getSystemService(RoleManager::class.java)
        .isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
}

private fun isCallScreeningRoleAvailable(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    return context.getSystemService(RoleManager::class.java)
        .isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
}

private fun requestCallScreeningRole(context: Context): Intent? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    return context.getSystemService(RoleManager::class.java)
        .createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
}
