package com.sentinel.quantum

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
import com.sentinel.quantum.security.SentinelSmsSender
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme

/**
 * Minimal SENDTO composer used by the future default-SMS role.
 * Sending remains fail-closed unless Android confirms Sentinel actually holds ROLE_SMS.
 */
class SmsComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialDestination = intent?.data?.schemeSpecificPart.orEmpty().substringBefore('?').take(32)
        val initialBody = intent?.getStringExtra("sms_body").orEmpty().take(SentinelSmsSender.MAX_BODY_CHARS)

        setContent {
            SentinelQuantumTheme {
                var destination by remember { mutableStateOf(initialDestination) }
                var body by remember { mutableStateOf(initialBody) }
                var status by remember { mutableStateOf<String?>(null) }
                val sender = remember { SentinelSmsSender(applicationContext) }

                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.fillMaxSize().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("Nouveau SMS", style = MaterialTheme.typography.headlineSmall)
                        OutlinedTextField(
                            value = destination,
                            onValueChange = { destination = it.take(32) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Destinataire") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = body,
                            onValueChange = { body = it.take(SentinelSmsSender.MAX_BODY_CHARS) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Message") },
                            minLines = 6
                        )
                        Button(
                            onClick = {
                                val result = sender.send(destination, body)
                                status = result.reason
                                if (result.accepted) body = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = destination.isNotBlank() && body.isNotBlank()
                        ) {
                            Text("Envoyer")
                        }
                        status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        if (!sender.holdsSmsRole()) {
                            Text(
                                "Envoi verrouillé tant que Sentinel n’est pas l’application SMS par défaut choisie par l’utilisateur.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
