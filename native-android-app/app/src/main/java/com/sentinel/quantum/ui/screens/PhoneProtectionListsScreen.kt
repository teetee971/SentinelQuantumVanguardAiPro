package com.sentinel.quantum.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.sentinel.quantum.PhoneCoreActivationActivity
import com.sentinel.quantum.PhoneCoreRuntimeFacts
import com.sentinel.quantum.R
import com.sentinel.quantum.navigation.Screen
import com.sentinel.quantum.security.ArcepVerifiedPrefixCatalog
import com.sentinel.quantum.security.CallBlocklistStore
import com.sentinel.quantum.security.CallRuleSyncConfig
import com.sentinel.quantum.security.PhoneProtectionListTruth
import com.sentinel.quantum.ui.design.SentinelD1
import com.sentinel.quantum.ui.design.SentinelHero
import com.sentinel.quantum.ui.design.SentinelTopBar
import java.text.DateFormat
import java.util.Date

@Composable
fun PhoneProtectionListsScreen(navController: NavController) {
    val context = LocalContext.current
    val hostActivity = context as? ComponentActivity
    val store = remember(context) { CallBlocklistStore(context.applicationContext) }
    var postureEpoch by remember { mutableIntStateOf(0) }
    var actionStatus by remember { mutableStateOf<String?>(null) }
    var exactMigrationDialogVisible by remember { mutableStateOf(false) }
    var prefixMigrationDialogVisible by remember { mutableStateOf(false) }

    DisposableEffect(hostActivity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) postureEpoch++
        }
        hostActivity?.lifecycle?.addObserver(observer)
        onDispose { hostActivity?.lifecycle?.removeObserver(observer) }
    }

    val now = System.currentTimeMillis()
    var snapshot by remember(postureEpoch) { mutableStateOf(store.snapshot(now)) }
    val signedMetadata = remember(postureEpoch) { store.signedRuleMetadata() }
    val runtimeFacts = remember(postureEpoch) { PhoneCoreRuntimeFacts.read(context.applicationContext) }
    val contactsReady = remember(postureEpoch) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
    }
    val manualPrefixes = remember(snapshot) { store.manualBlockedPrefixes() }

    val arcepStatus = PhoneProtectionListTruth.status(
        PhoneProtectionListTruth.SourceFacts(
            packagePresent = true,
            verified = true,
            enabledByUser = snapshot.arcepVerifiedBlockingEnabled,
            itemCount = ArcepVerifiedPrefixCatalog.entries.size
        ),
        now
    )
    val signedStatus = PhoneProtectionListTruth.status(
        PhoneProtectionListTruth.SourceFacts(
            packagePresent = signedMetadata.persistedAfterVerification,
            verified = signedMetadata.persistedAfterVerification,
            enabledByUser = true,
            itemCount = signedMetadata.storedPrefixCount,
            expiresAtMs = signedMetadata.expiresAtMs
        ),
        now
    )

    val arcepEnabledResult = stringResource(R.string.phone_lists_arcep_enabled_result)
    val arcepDisabledResult = stringResource(R.string.phone_lists_arcep_disabled_result)
    val arcepChangeFailed = stringResource(R.string.phone_lists_arcep_change_failed)
    val exactMigrationRemoved = stringResource(
        R.string.phone_lists_exact_migration_removed,
        snapshot.quarantinedLegacyExactRuleCount
    )
    val exactMigrationFailed = stringResource(R.string.phone_lists_exact_migration_failed)
    val prefixMigrationRemoved = stringResource(
        R.string.phone_lists_prefix_migration_removed,
        snapshot.quarantinedLegacyPrefixRuleCount
    )
    val prefixMigrationFailed = stringResource(R.string.phone_lists_prefix_migration_failed)

    if (exactMigrationDialogVisible && snapshot.exactRuleMigrationRequired) {
        AlertDialog(
            onDismissRequest = { exactMigrationDialogVisible = false },
            title = { Text(stringResource(R.string.phone_lists_exact_migration_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.phone_lists_exact_migration_confirm_body,
                        snapshot.quarantinedLegacyExactRuleCount
                    )
                )
            },
            dismissButton = {
                TextButton(onClick = { exactMigrationDialogVisible = false }) {
                    Text(stringResource(R.string.phone_lists_exact_migration_cancel))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val removed = store.discardQuarantinedLegacyExactRules()
                        snapshot = store.snapshot()
                        actionStatus = if (removed > 0) exactMigrationRemoved else exactMigrationFailed
                        exactMigrationDialogVisible = false
                    }
                ) {
                    Text(stringResource(R.string.phone_lists_exact_migration_confirm))
                }
            }
        )
    }

    if (prefixMigrationDialogVisible && snapshot.prefixRuleMigrationRequired) {
        AlertDialog(
            onDismissRequest = { prefixMigrationDialogVisible = false },
            title = { Text(stringResource(R.string.phone_lists_prefix_migration_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.phone_lists_prefix_migration_confirm_body,
                        snapshot.quarantinedLegacyPrefixRuleCount
                    )
                )
            },
            dismissButton = {
                TextButton(onClick = { prefixMigrationDialogVisible = false }) {
                    Text(stringResource(R.string.phone_lists_exact_migration_cancel))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val removed = store.discardQuarantinedLegacyPrefixRules()
                        snapshot = store.snapshot()
                        actionStatus = if (removed > 0) prefixMigrationRemoved else prefixMigrationFailed
                        prefixMigrationDialogVisible = false
                    }
                ) {
                    Text(stringResource(R.string.phone_lists_exact_migration_confirm))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            SentinelTopBar(
                title = stringResource(R.string.phone_lists_title),
                subtitle = stringResource(R.string.phone_lists_subtitle),
                onBack = { navController.navigateUp() }
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
            SentinelHero(
                eyebrow = "Phone Core",
                title = stringResource(R.string.phone_lists_hero_title),
                body = stringResource(R.string.phone_lists_hero_body),
                badges = listOf(
                    stringResource(R.string.phone_lists_badge_truth) to SentinelD1.Cyan,
                    stringResource(R.string.phone_lists_badge_local) to SentinelD1.Success
                )
            )

            SectionTitle(stringResource(R.string.phone_lists_private_section))
            ProtectionListCard(
                title = stringResource(R.string.phone_lists_exact_title),
                type = stringResource(R.string.phone_lists_type_block),
                status = stringResource(
                    if (snapshot.blockedNumberHashes.isEmpty()) {
                        R.string.phone_lists_status_empty
                    } else {
                        R.string.phone_lists_status_local
                    }
                ),
                itemCount = snapshot.blockedNumberHashes.size,
                details = stringResource(R.string.phone_lists_exact_details)
            )

            if (snapshot.exactRuleMigrationRequired) {
                MigrationWarningCard(
                    title = stringResource(R.string.phone_lists_exact_migration_title),
                    body = stringResource(
                        R.string.phone_lists_exact_migration_body,
                        snapshot.quarantinedLegacyExactRuleCount
                    ),
                    action = stringResource(R.string.phone_lists_exact_migration_action),
                    onAction = { exactMigrationDialogVisible = true }
                )
            }

            ProtectionListCard(
                title = stringResource(R.string.phone_lists_prefix_title),
                type = stringResource(R.string.phone_lists_type_block),
                status = stringResource(
                    if (manualPrefixes.isEmpty()) {
                        R.string.phone_lists_status_empty
                    } else {
                        R.string.phone_lists_status_local
                    }
                ),
                itemCount = manualPrefixes.size,
                details = stringResource(R.string.phone_lists_prefix_details)
            )

            if (snapshot.prefixRuleMigrationRequired) {
                MigrationWarningCard(
                    title = stringResource(R.string.phone_lists_prefix_migration_title),
                    body = stringResource(
                        R.string.phone_lists_prefix_migration_body,
                        snapshot.quarantinedLegacyPrefixRuleCount
                    ),
                    action = stringResource(R.string.phone_lists_prefix_migration_action),
                    onAction = { prefixMigrationDialogVisible = true }
                )
            }

            OutlinedButton(
                onClick = { navController.navigate(Screen.CallBlocking.route) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.phone_lists_manage_rules))
            }

            SectionTitle(stringResource(R.string.phone_lists_trusted_section))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.phone_lists_arcep_title), fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.phone_lists_arcep_meta), style = MaterialTheme.typography.bodySmall)
                        }
                        StatusText(arcepStatus)
                    }
                    Text(stringResource(R.string.phone_lists_arcep_version), style = MaterialTheme.typography.bodySmall)
                    Text(
                        stringResource(R.string.phone_lists_arcep_count, ArcepVerifiedPrefixCatalog.entries.size),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        stringResource(R.string.phone_lists_arcep_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            stringResource(
                                if (snapshot.arcepVerifiedBlockingEnabled) {
                                    R.string.phone_lists_arcep_enabled
                                } else {
                                    R.string.phone_lists_arcep_disabled
                                }
                            )
                        )
                        Switch(
                            checked = snapshot.arcepVerifiedBlockingEnabled,
                            onCheckedChange = { enabled ->
                                val changed = store.setArcepVerifiedBlockingEnabled(enabled)
                                snapshot = store.snapshot()
                                actionStatus = if (changed) {
                                    if (enabled) arcepEnabledResult else arcepDisabledResult
                                } else {
                                    arcepChangeFailed
                                }
                            }
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.phone_lists_signed_title), fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.phone_lists_signed_meta), style = MaterialTheme.typography.bodySmall)
                        }
                        StatusText(signedStatus)
                    }
                    Text(
                        stringResource(R.string.phone_lists_signed_stored, signedMetadata.storedPrefixCount),
                        style = MaterialTheme.typography.bodySmall
                    )
                    signedMetadata.acceptedSequence?.let {
                        Text(stringResource(R.string.phone_lists_signed_sequence, it), style = MaterialTheme.typography.bodySmall)
                    }
                    signedMetadata.packageId?.let {
                        Text(stringResource(R.string.phone_lists_signed_package, it), style = MaterialTheme.typography.bodySmall)
                    }
                    signedMetadata.issuerId?.let {
                        Text(stringResource(R.string.phone_lists_signed_issuer, it), style = MaterialTheme.typography.bodySmall)
                    }
                    signedMetadata.keyId?.let {
                        Text(stringResource(R.string.phone_lists_signed_key, it), style = MaterialTheme.typography.bodySmall)
                    }
                    signedMetadata.issuedAtMs?.let {
                        Text(
                            stringResource(R.string.phone_lists_signed_issued, formatDate(it)),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    signedMetadata.expiresAtMs?.let {
                        Text(
                            stringResource(R.string.phone_lists_signed_expires, formatDate(it)),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        stringResource(
                            if (CallRuleSyncConfig.SYNC_ENABLED && CallRuleSyncConfig.TRUSTED_KEYS.isNotEmpty()) {
                                R.string.phone_lists_signed_channel_ready
                            } else {
                                R.string.phone_lists_signed_channel_missing
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.phone_lists_signed_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SectionTitle(stringResource(R.string.phone_lists_community_section))
            ProtectionListCard(
                title = stringResource(R.string.phone_lists_community_title),
                type = stringResource(R.string.phone_lists_type_identify_alert),
                status = stringResource(R.string.phone_lists_status_not_evaluated),
                itemCount = 0,
                details = stringResource(R.string.phone_lists_community_details)
            )

            HorizontalDivider()
            SectionTitle(stringResource(R.string.phone_lists_android_section))
            Text(
                stringResource(R.string.phone_lists_android_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SystemFactRow(stringResource(R.string.phone_lists_fact_dialer), runtimeFacts.dialerRoleHeld)
            SystemFactRow(stringResource(R.string.phone_lists_fact_screening), runtimeFacts.callScreeningRoleHeld)
            SystemFactRow(stringResource(R.string.phone_lists_fact_sms), runtimeFacts.smsRoleHeld)
            SystemFactRow(stringResource(R.string.phone_lists_fact_contacts), contactsReady)
            SystemFactRow(stringResource(R.string.phone_lists_fact_notifications), runtimeFacts.notificationChannelsReady)
            Text(
                stringResource(R.string.phone_lists_battery_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { context.startActivity(Intent(context, PhoneCoreActivationActivity::class.java)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.phone_lists_configure_phone_core))
            }

            actionStatus?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MigrationWarningCard(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                Text(action)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun ProtectionListCard(
    title: String,
    type: String,
    status: String,
    itemCount: Int,
    details: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(status, style = MaterialTheme.typography.labelMedium)
            }
            Text(
                "$type · ${stringResource(R.string.phone_lists_item_count, itemCount)}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusText(status: PhoneProtectionListTruth.ListStatus) {
    val label = stringResource(
        when (status) {
            PhoneProtectionListTruth.ListStatus.ACTIVE -> R.string.phone_lists_status_active
            PhoneProtectionListTruth.ListStatus.DISABLED -> R.string.phone_lists_status_disabled
            PhoneProtectionListTruth.ListStatus.EXPIRED -> R.string.phone_lists_status_expired
            PhoneProtectionListTruth.ListStatus.UNAVAILABLE -> R.string.phone_lists_status_unavailable
        }
    )
    Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun SystemFactRow(label: String, ready: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(
            stringResource(
                if (ready) R.string.phone_lists_fact_active else R.string.phone_lists_fact_inactive
            ),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatDate(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))
