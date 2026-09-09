package com.sentinel.quantum.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.R
import com.sentinel.quantum.data.SharedTextHolder
import com.sentinel.quantum.security.EmailSecurityAnalyzer
import com.sentinel.quantum.security.HeaderAnomaly
import com.sentinel.quantum.security.LookalikeLevel
import com.sentinel.quantum.security.LookalikeReason
import com.sentinel.quantum.security.LocalLogger

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EmailSecurityScreen(navController: NavController) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val analyzer = remember(context) { EmailSecurityAnalyzer(LocalLogger(context)) }
    var rawMessage by remember { mutableStateOf(SharedTextHolder.consume().orEmpty()) }
    var result by remember { mutableStateOf<EmailSecurityAnalyzer.Analysis?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.email_security_title)) }, navigationIcon = {
        IconButton(onClick = { navController.navigateUp() }) {
            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back))
        }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.email_security_intro))
            OutlinedTextField(rawMessage, { rawMessage = it.take(256 * 1024) },
                label = { Text(stringResource(R.string.email_security_label)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp), minLines = 8)
            Button({ result = analyzer.analyze(rawMessage) }, Modifier.fillMaxWidth(), enabled = rawMessage.isNotBlank()) {
                Text(stringResource(R.string.email_security_analyze))
            }
            result?.let { analysis -> Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.email_security_result), fontWeight = FontWeight.Bold)
                    if (!analysis.accepted) Text(
                        stringResource(
                            R.string.email_security_rejected,
                            rejectionReason(analysis.reason)
                        )
                    )
                    else {
                        Text(stringResource(R.string.email_security_risk, riskLevelLabel(analysis.riskLevel)))
                        Text(stringResource(R.string.email_security_score, analysis.score))
                        Text(stringResource(R.string.email_security_links, analysis.linksInspected))
                        if (analysis.findings.isEmpty()) Text(stringResource(R.string.email_security_no_findings))
                        else analysis.findings.forEach { finding ->
                            Text(
                                stringResource(
                                    R.string.email_security_finding_item,
                                    severityLabel(finding.severity),
                                    findingDescription(finding)
                                )
                            )
                        }
                        if (analysis.observedAuthenticationResults) Text(
                            stringResource(R.string.email_security_auth_note),
                            style = MaterialTheme.typography.bodySmall)
                        EmailSecurityDetails(analysis) { value ->
                            clipboard.setText(AnnotatedString(value))
                        }
                    }
                }
            } }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun EmailSecurityDetails(
    analysis: EmailSecurityAnalyzer.Analysis,
    onCopy: (String) -> Unit
) {
    val iocs = analysis.iocReport
    DetailSection(stringResource(R.string.email_security_iocs_title)) {
        IocChips(stringResource(R.string.email_security_iocs_urls), iocs.urls, onCopy)
        IocChips(stringResource(R.string.email_security_iocs_ips), iocs.ipAddresses, onCopy)
        IocChips(stringResource(R.string.email_security_iocs_emails), iocs.emailAddresses, onCopy)
        IocChips(stringResource(R.string.email_security_iocs_phones), iocs.phoneNumbers, onCopy)
        if (iocs.hasPotentialPrivateIps) Text(stringResource(R.string.email_security_iocs_private_ips))
        if (iocs.hasShortenedUrls) Text(stringResource(R.string.email_security_iocs_shorteners))
    }
    if (analysis.attachments.isNotEmpty()) {
        DetailSection(stringResource(R.string.email_security_attachments_title)) {
            analysis.attachments.forEach { attachment ->
                Text(stringResource(R.string.email_security_attachment_item,
                    attachment.fileName, attachment.extension.ifBlank { stringResource(R.string.email_security_attachment_unknown) }))
            }
        }
    }
    if (analysis.lookalikeRisks.isNotEmpty()) {
        DetailSection(stringResource(R.string.email_security_lookalike_title)) {
            analysis.lookalikeRisks.forEach { risk ->
                Text(stringResource(
                    R.string.email_security_lookalike_item,
                    lookalikeLevelLabel(risk.level),
                    risk.domain,
                    lookalikeReason(risk.reason, risk.matchedTrustedDomain)
                ))
            }
        }
    }
    if (analysis.headerReport.anomalies.isNotEmpty()) {
        DetailSection(stringResource(R.string.email_security_headers_title)) {
            Text(stringResource(R.string.email_security_headers_hops, analysis.headerReport.hopCount))
            analysis.headerReport.anomalies.forEach { anomaly ->
                Text(stringResource(R.string.email_security_anomaly_item, headerAnomalyDescription(anomaly)))
            }
        }
    }
}

@Composable
private fun riskLevelLabel(level: EmailSecurityAnalyzer.RiskLevel): String = when (level) {
    EmailSecurityAnalyzer.RiskLevel.UNKNOWN -> stringResource(R.string.email_security_level_unknown)
    EmailSecurityAnalyzer.RiskLevel.LOW -> stringResource(R.string.email_security_level_low)
    EmailSecurityAnalyzer.RiskLevel.MEDIUM -> stringResource(R.string.email_security_level_medium)
    EmailSecurityAnalyzer.RiskLevel.HIGH -> stringResource(R.string.email_security_level_high)
}

@Composable
private fun severityLabel(level: EmailSecurityAnalyzer.Severity): String = when (level) {
    EmailSecurityAnalyzer.Severity.LOW -> stringResource(R.string.email_security_level_low)
    EmailSecurityAnalyzer.Severity.MEDIUM -> stringResource(R.string.email_security_level_medium)
    EmailSecurityAnalyzer.Severity.HIGH -> stringResource(R.string.email_security_level_high)
}

@Composable
private fun lookalikeLevelLabel(level: LookalikeLevel): String = when (level) {
    LookalikeLevel.LOW -> stringResource(R.string.email_security_level_low)
    LookalikeLevel.MEDIUM -> stringResource(R.string.email_security_level_medium)
    LookalikeLevel.HIGH -> stringResource(R.string.email_security_level_high)
}

@Composable
private fun rejectionReason(reason: String?): String = when (reason) {
    "EMPTY_MESSAGE" -> stringResource(R.string.email_security_reject_empty)
    "MESSAGE_TOO_LARGE" -> stringResource(R.string.email_security_reject_large)
    "HEADER_LINE_TOO_LONG" -> stringResource(R.string.email_security_reject_long_header)
    "INVALID_HEADERS" -> stringResource(R.string.email_security_reject_invalid_headers)
    "TOO_MANY_RECEIVED_HOPS" -> stringResource(R.string.email_security_reject_many_hops)
    else -> stringResource(R.string.email_security_rejected_default)
}

@Composable
private fun findingDescription(finding: EmailSecurityAnalyzer.Finding): String = when {
    finding.code == "RECEIVED_CHAIN_SUSPICIOUS" -> stringResource(R.string.email_security_finding_received)
    finding.code == "MISSING_DATE" -> stringResource(R.string.email_security_finding_missing_date)
    finding.code == "MISSING_MESSAGE_ID" -> stringResource(R.string.email_security_finding_missing_message_id)
    finding.code == "MISSING_MIME_VERSION" -> stringResource(R.string.email_security_finding_missing_mime)
    finding.code == "SUSPICIOUS_FROM_DISPLAY_NAME" -> stringResource(R.string.email_security_finding_from_name)
    finding.code == "REPLY_TO_DOMAIN_MISMATCH" -> stringResource(R.string.email_security_finding_reply_to)
    finding.code == "RETURN_PATH_DOMAIN_MISMATCH" -> stringResource(R.string.email_security_finding_return_path)
    finding.code == "SOCIAL_ENGINEERING_LANGUAGE" -> stringResource(R.string.email_security_finding_social)
    finding.code == "CLEARTEXT_LINK" -> stringResource(R.string.email_security_finding_http)
    finding.code == "IP_LITERAL_LINK" -> stringResource(R.string.email_security_finding_ip_link)
    finding.code == "MISLEADING_LINK_USERINFO" -> stringResource(R.string.email_security_finding_userinfo)
    finding.code == "SHORTENED_URL" -> stringResource(R.string.email_security_finding_shortener)
    finding.code == "LOOKALIKE_DOMAIN" -> stringResource(R.string.email_security_finding_lookalike)
    finding.code == "DANGEROUS_ATTACHMENT_TYPE" -> stringResource(R.string.email_security_finding_attachment)
    finding.code.contains("_OBSERVED_") -> stringResource(R.string.email_security_finding_authentication)
    else -> stringResource(R.string.email_security_finding_generic)
}

@Composable
private fun headerAnomalyDescription(anomaly: HeaderAnomaly): String = when (anomaly.code) {
    "RECEIVED_TOO_MANY_HOPS" -> stringResource(R.string.email_security_anomaly_many_hops)
    "RECEIVED_TIMESTAMP_INVERSION" -> stringResource(R.string.email_security_anomaly_timestamp)
    "MISSING_DATE" -> stringResource(R.string.email_security_finding_missing_date)
    "MISSING_MESSAGE_ID" -> stringResource(R.string.email_security_finding_missing_message_id)
    "MISSING_MIME_VERSION" -> stringResource(R.string.email_security_finding_missing_mime)
    "SUSPICIOUS_FROM_DISPLAY_NAME" -> stringResource(R.string.email_security_finding_from_name)
    else -> stringResource(R.string.email_security_finding_generic)
}

@Composable
private fun lookalikeReason(reason: LookalikeReason, match: String?): String = when (reason) {
    LookalikeReason.INVALID -> stringResource(R.string.email_security_lookalike_invalid)
    LookalikeReason.TRUSTED -> stringResource(R.string.email_security_lookalike_trusted)
    LookalikeReason.CONFUSABLE -> stringResource(R.string.email_security_lookalike_confusable, match.orEmpty())
    LookalikeReason.MISLEADING_LABEL -> stringResource(R.string.email_security_lookalike_label, match.orEmpty())
    LookalikeReason.TRUSTED_LABEL_RISKY_TLD -> stringResource(R.string.email_security_lookalike_risky_tld, match.orEmpty())
    LookalikeReason.EDIT_DISTANCE -> stringResource(R.string.email_security_lookalike_distance, match.orEmpty())
    LookalikeReason.NO_STRONG_MATCH -> stringResource(R.string.email_security_lookalike_none)
}

@Composable
private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun IocChips(label: String, values: List<String>, onCopy: (String) -> Unit) {
    if (values.isEmpty()) return
    Text(label)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        values.forEach { value ->
            AssistChip(onClick = { onCopy(value) }, label = { Text(value) })
        }
    }
}
