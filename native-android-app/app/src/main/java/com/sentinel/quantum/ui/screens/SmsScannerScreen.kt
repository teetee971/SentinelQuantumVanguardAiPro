package com.sentinel.quantum.ui.screens

import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.R
import com.sentinel.quantum.data.SharedTextHolder
import com.sentinel.quantum.security.LocalLogger
import com.sentinel.quantum.security.PhoneCoreFrenchLabels
import com.sentinel.quantum.security.PhonePrivateTimelineStore
import com.sentinel.quantum.security.SmsLinkAnalyzer
import com.sentinel.quantum.security.SmsTimelineMapper
import com.sentinel.quantum.ui.design.SentinelHeroVisual
import com.sentinel.quantum.ui.design.SentinelGlassCard
import com.sentinel.quantum.ui.design.SentinelMotionPolicy
import com.sentinel.quantum.ui.design.SentinelSectionHeader
import com.sentinel.quantum.ui.design.SentinelStatus
import com.sentinel.quantum.ui.design.SentinelStatusChip
import com.sentinel.quantum.ui.design.SentinelTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsScannerScreen(navController: NavController) {
    val context = LocalContext.current
    val analyzer = remember(context) { SmsLinkAnalyzer(LocalLogger(context)) }
    val timelineStore = remember(context) { PhonePrivateTimelineStore(context) }
    var rawMessage by remember { mutableStateOf(SharedTextHolder.consume().orEmpty()) }
    var result by remember { mutableStateOf<SmsLinkAnalyzer.Analysis?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
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
    val presentation = result?.let(SmsThreatPresentation::from)

    Scaffold(
        topBar = {
            SentinelTopBar(
                title = stringResource(R.string.sms_scanner_title),
                subtitle = "Analyse locale des messages & liens",
                onBack = { navController.navigateUp() }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SentinelHeroVisual(
                status = presentation?.status ?: SentinelStatus.UNKNOWN,
                title = presentation?.label ?: "Analyser avant d’ouvrir",
                subtitle = presentation?.detail
                    ?: "Sentinel inspecte localement le texte et les liens partagés. Aucun lien n’est ouvert automatiquement.",
                modifier = Modifier.testTag("sms-threat-hero"),
                motionLevel = motionLevel
            )

            SentinelSectionHeader(
                title = "Message à examiner",
                subtitle = "Analyse bornée et locale. L’absence de signal n’est jamais présentée comme une preuve de sécurité."
            )
            OutlinedTextField(
                value = rawMessage,
                onValueChange = {
                    rawMessage = it.take(16 * 1024)
                    result = null
                },
                label = { Text(stringResource(R.string.sms_scanner_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp)
                    .testTag("sms-threat-input"),
                minLines = 6
            )
            Button(
                onClick = {
                    val candidate = rawMessage
                    isAnalyzing = true
                    scope.launch {
                        val analysis = withContext(Dispatchers.IO) {
                            analyzer.analyze(candidate).also { analyzed ->
                                SmsTimelineMapper.toEvent(analyzed)?.let { event ->
                                    timelineStore.append(event)
                                }
                            }
                        }
                        result = analysis
                        isAnalyzing = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sms-threat-analyze-action"),
                enabled = rawMessage.isNotBlank() && !isAnalyzing
            ) {
                Text(if (isAnalyzing) "Analyse…" else stringResource(R.string.sms_scanner_analyze))
            }

            result?.let { analysis ->
                val view = SmsThreatPresentation.from(analysis)
                SmsThreatDetailCard(analysis = analysis, view = view)
            }
        }
    }
}

@Composable
private fun SmsThreatDetailCard(
    analysis: SmsLinkAnalyzer.Analysis,
    view: SmsThreatPresentation
) {
    SentinelGlassCard(modifier = Modifier.fillMaxWidth().testTag("sms-threat-detail")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.material3.Icon(
                if (analysis.accepted) Icons.Default.WarningAmber else Icons.Default.Security,
                contentDescription = null,
                tint = if (analysis.accepted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Column(Modifier.weight(1f)) {
                Text("Menace détectée", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text(view.label, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            SentinelStatusChip(view.status)
        }
        Text(view.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!analysis.accepted) {
            Text(
                stringResource(R.string.sms_scanner_rejected, PhoneCoreFrenchLabels.analysisRejection(analysis.reason)),
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThreatMetric("Niveau de risque", PhoneCoreFrenchLabels.riskLevel(analysis.riskLevel.name), Modifier.weight(1f))
                ThreatMetric("Score", "${analysis.score}/100", Modifier.weight(1f))
                ThreatMetric("Liens inspectés", analysis.linksInspected.toString(), Modifier.weight(1f))
            }
            SentinelSectionHeader(title = "Comportement suspect", subtitle = "Signaux observés localement")
            if (analysis.findings.isEmpty()) {
                Text(stringResource(R.string.sms_scanner_no_findings), style = MaterialTheme.typography.bodySmall)
            } else {
                analysis.findings.forEach { finding ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(R.string.sms_scanner_finding, PhoneCoreFrenchLabels.riskLevel(finding.severity.name), findingLabel(finding.code)),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            Text(
                "Aucune action externe automatique : le lien n’est pas ouvert et aucune donnée n’est envoyée.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ThreatMetric(label: String, value: String, modifier: Modifier) {
    ElevatedCard(modifier = modifier, colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun findingLabel(code: String): String = stringResource(
    when (code) {
        "CLEARTEXT_LINK" -> R.string.sms_finding_http
        "IP_LITERAL_LINK" -> R.string.sms_finding_ip
        "MISLEADING_LINK_USERINFO" -> R.string.sms_finding_userinfo
        "URL_SHORTENER" -> R.string.sms_finding_shortener
        "SUSPICIOUS_TLD" -> R.string.sms_finding_tld
        "MULTIPLE_LINKS" -> R.string.sms_finding_multiple
        "SOCIAL_ENGINEERING_LANGUAGE" -> R.string.sms_finding_social
        "PREMIUM_SHORTCODE_MENTION" -> R.string.sms_finding_shortcode
        else -> R.string.sms_finding_generic
    }
)
