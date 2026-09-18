package com.sentinel.quantum

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import com.sentinel.quantum.sms.SmsRepository
import com.sentinel.quantum.sms.SmsRoleController
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme

class SmsComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialRecipient = intent?.data?.schemeSpecificPart.orEmpty().take(64)
        val initialBody = intent?.getStringExtra(android.content.Intent.EXTRA_TEXT).orEmpty().take(5_000)

        setContent {
            SentinelQuantumTheme {
                var recipient by remember { mutableStateOf(initialRecipient) }
                var body by remember { mutableStateOf(initialBody) }
                var status by remember { mutableStateOf<String?>(null) }
                var isDefault by remember { mutableStateOf(SmsRoleController.isHeld(this)) }
                val roleLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    isDefault = result.resultCode == Activity.RESULT_OK && SmsRoleController.isHeld(this)
                }
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    status = if (granted) "Permission SMS accordée." else "Permission SMS refusée."
                }

                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("Envoyer un SMS", style = MaterialTheme.typography.headlineSmall)
                        if (!isDefault) {
                            Text("Sentinel doit être choisi comme application SMS par défaut avant l’envoi.")
                            Button(
                                onClick = {
                                    SmsRoleController.createRequestIntent(this@SmsComposeActivity)
                                        ?.let(roleLauncher::launch)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Choisir Sentinel comme app SMS") }
                        }
                        OutlinedTextField(
                            value = recipient,
                            onValueChange = { recipient = it.take(64) },
                            label = { Text("Destinataire") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = body,
                            onValueChange = { body = it.take(5_000) },
                            label = { Text("Message") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4
                        )
                        Button(
                            onClick = {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    this@SmsComposeActivity,
                                    Manifest.permission.SEND_SMS
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!hasPermission) {
                                    permissionLauncher.launch(Manifest.permission.SEND_SMS)
                                } else {
                                    val sent = SmsRepository(this@SmsComposeActivity).send(recipient, body)
                                    status = if (sent) "SMS remis au système pour envoi." else "Envoi impossible."
                                    if (sent) {
                                        body = ""
                                        setResult(Activity.RESULT_OK)
                                    }
                                }
                            },
                            enabled = isDefault && recipient.isNotBlank() && body.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Envoyer") }
                        status?.let { Text(it) }
                        Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Fermer")
                        }
                    }
                }
            }
        }
    }
}
