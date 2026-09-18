package com.sentinel.quantum

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sentinel.quantum.sms.SmsProviderStore
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme

/** Handles ACTION_SENDTO for sms:, smsto:, mms: and mmsto:. */
class SmsComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialRecipient = intent?.data?.schemeSpecificPart?.substringBefore('?').orEmpty().take(64)
        val initialBody = (
            intent?.getStringExtra("sms_body")
                ?: intent?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                ?: ""
            ).take(SmsProviderStore.MAX_BODY_LENGTH)
        setContent {
            SentinelQuantumTheme {
                var recipient by remember { mutableStateOf(initialRecipient) }
                var body by remember { mutableStateOf(initialBody) }
                var status by remember { mutableStateOf<String?>(null) }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("Composer un message", style = MaterialTheme.typography.headlineSmall)
                        OutlinedTextField(
                            recipient,
                            { recipient = it.take(64) },
                            label = { Text("Destinataire") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            body,
                            { body = it.take(SmsProviderStore.MAX_BODY_LENGTH) },
                            label = { Text("Message") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4
                        )
                        Button(
                            onClick = {
                                status = if (SmsProviderStore.sendText(this@SmsComposeActivity, recipient, body)) {
                                    body = ""
                                    "Message remis au système radio pour envoi."
                                } else {
                                    "Envoi indisponible : Sentinel doit être l’application SMS par défaut et disposer des permissions SMS."
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = recipient.isNotBlank() && body.isNotBlank()
                        ) { Text("Envoyer") }
                        status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}
