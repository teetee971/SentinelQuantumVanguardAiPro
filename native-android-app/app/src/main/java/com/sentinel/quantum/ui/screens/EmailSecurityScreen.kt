package com.sentinel.quantum.ui.screens

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
import com.sentinel.quantum.data.SharedTextHolder
import com.sentinel.quantum.security.EmailSecurityAnalyzer
import com.sentinel.quantum.security.LocalLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailSecurityScreen(navController: NavController) {
    val context = LocalContext.current
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
                    }
                }
            } }
        }
    }
}
