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
                            analysis.reason ?: stringResource(R.string.email_security_rejected_default)
                        )
                    )
                    else {
                        Text(stringResource(R.string.email_security_risk, analysis.riskLevel.toString()))
                        Text(stringResource(R.string.email_security_score, analysis.score))
                        Text(stringResource(R.string.email_security_links, analysis.linksInspected))
                        if (analysis.findings.isEmpty()) Text(stringResource(R.string.email_security_no_findings))
                        else analysis.findings.forEach { Text("• [${it.severity}] ${it.description}") }
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
                Text(stringResource(R.string.email_security_lookalike_item, risk.level.toString(), risk.domain, risk.reason))
            }
        }
    }
    if (analysis.headerReport.anomalies.isNotEmpty()) {
        DetailSection(stringResource(R.string.email_security_headers_title)) {
            Text(stringResource(R.string.email_security_headers_hops, analysis.headerReport.hopCount))
            analysis.headerReport.anomalies.forEach { anomaly -> Text("• ${anomaly.description}") }
        }
    }
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
