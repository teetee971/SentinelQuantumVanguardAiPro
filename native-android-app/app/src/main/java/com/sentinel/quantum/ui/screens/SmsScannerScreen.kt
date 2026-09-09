package com.sentinel.quantum.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.R
import com.sentinel.quantum.data.SharedTextHolder
import com.sentinel.quantum.security.LocalLogger
import com.sentinel.quantum.security.SmsLinkAnalyzer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsScannerScreen(navController: NavController) {
    val context = LocalContext.current
    val analyzer = remember(context) { SmsLinkAnalyzer(LocalLogger(context)) }
    var rawMessage by remember { mutableStateOf(SharedTextHolder.consume().orEmpty()) }
    var result by remember { mutableStateOf<SmsLinkAnalyzer.Analysis?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sms_scanner_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Text(stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.sms_scanner_intro))
            OutlinedTextField(
                value = rawMessage,
                onValueChange = { rawMessage = it.take(16 * 1024) },
                label = { Text(stringResource(R.string.sms_scanner_label)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                minLines = 6
            )
            Button(
                onClick = { result = analyzer.analyze(rawMessage) },
                modifier = Modifier.fillMaxWidth(),
                enabled = rawMessage.isNotBlank()
            ) {
                Text(stringResource(R.string.sms_scanner_analyze))
            }
            result?.let { analysis ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.sms_scanner_result), fontWeight = FontWeight.Bold)
                        if (!analysis.accepted) {
                            Text(stringResource(R.string.sms_scanner_rejected, analysis.reason ?: "INVALID_INPUT"))
                        } else {
                            Text(stringResource(R.string.sms_scanner_risk, analysis.riskLevel.name))
                            Text(stringResource(R.string.sms_scanner_score, analysis.score))
                            Text(stringResource(R.string.sms_scanner_links, analysis.linksInspected))
                            if (analysis.findings.isEmpty()) {
                                Text(stringResource(R.string.sms_scanner_no_findings))
                            } else {
                                analysis.findings.forEach { finding ->
                                    Text(
                                        stringResource(
                                            R.string.sms_scanner_finding,
                                            finding.severity.name,
                                            findingLabel(finding.code)
                                        )
                                    )
                                }
                            }
                            Text(
                                stringResource(R.string.sms_scanner_disclaimer),
                                style = MaterialTheme.typography.bodySmall
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
