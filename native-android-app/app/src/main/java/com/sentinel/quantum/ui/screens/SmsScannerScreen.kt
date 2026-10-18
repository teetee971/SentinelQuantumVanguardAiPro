package com.sentinel.quantum.ui.screens

import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import com.sentinel.quantum.ui.design.SentinelHolographicScene
import com.sentinel.quantum.ui.design.SentinelHolographicSceneModel
import com.sentinel.quantum.ui.design.SentinelHolographicSceneNode
import com.sentinel.quantum.ui.design.SentinelMotionPolicy
import com.sentinel.quantum.ui.design.SentinelSceneKind
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
            SentinelHolographicScene(
                model = SentinelHolographicSceneModel(
                    kind = SentinelSceneKind.SECURE_MESSAGES,
                    status = presentation?.status ?: SentinelStatus.UNKNOWN,
                    title = "MESSAGES SÉCURISÉS",
                    evidenceLabel = presentation?.label ?: "Analyse locale en attente",
                    evidenceSource = "SmsLinkAnalyzer · texte fourni par l’utilisateur",
                    nodes = listOf(
                        SentinelHolographicSceneNode(
                            "Message",
                            presentation?.status ?: SentinelStatus.UNKNOWN
                        ),
                        SentinelHolographicSceneNode(
                            "Liens",
                            if (result == null) SentinelStatus.UNKNOWN else presentation?.status ?: SentinelStatus.UNKNOWN
                        ),
                        SentinelHolographicSceneNode(
                            "Anti-phishing",
                            if (result == null) SentinelStatus.TEST else presentation?.status ?: SentinelStatus.UNKNOWN
                        )
                    )
                ),
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
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sms-threat-result"),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                stringResource(R.string.sms_scanner_result),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            SentinelStatusChip(status = view.status)
                        }
                        Text(view.label, fontWeight = FontWeight.Bold)
                        Text(
                            view.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!analysis.accepted) {
                            Text(
                                stringResource(
                                    R.string.sms_scanner_rejected,
                                    PhoneCoreFrenchLabels.analysisRejection(analysis.reason)
                                )
                            )
                        } else {
                            Text(
                                stringResource(
                                    R.string.sms_scanner_risk,
                                    PhoneCoreFrenchLabels.riskLevel(analysis.riskLevel.name)
                                )
                            )
                            Text(stringResource(R.string.sms_scanner_score, analysis.score))
                            Text(stringResource(R.string.sms_scanner_links, analysis.linksInspected))
                            if (analysis.findings.isEmpty()) {
                                Text(stringResource(R.string.sms_scanner_no_findings))
                            } else {
                                SentinelSectionHeader(
                                    title = "Signaux observés",
                                    subtitle = "Chaque élément correspond à une heuristique locale explicable."
                                )
                                analysis.findings.forEach { finding ->
                                    Text(
                                        stringResource(
                                            R.string.sms_scanner_finding,
                                            PhoneCoreFrenchLabels.riskLevel(finding.severity.name),
                                            findingLabel(finding.code)
                                        )
                                    )
                                }
                            }
                            Text(
                                stringResource(R.string.sms_scanner_disclaimer),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
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
