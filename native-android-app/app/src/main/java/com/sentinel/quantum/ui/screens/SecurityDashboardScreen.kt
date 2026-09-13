package com.sentinel.quantum.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sentinel.quantum.sms.SmsRoleManager

@Composable
fun SecurityDashboardScreen(
    onStartVpn: () -> Unit = {}
) {
    val context = LocalContext.current
    val isDefaultSms = remember { SmsRoleManager.isDefaultSmsApp(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "QUANTUM VANGUARD // P0 TELEMETRY",
            color = Color(0xFF00FFCC),
            style = MaterialTheme.typography.titleLarge
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SMS Default Role: ${if (isDefaultSms) "ACTIVE" else "INACTIVE"}",
                    color = Color.White
                )
                Button(
                    onClick = {
                        val activity = context as? Activity
                        activity?.let { SmsRoleManager.requestDefaultSmsRole(it) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F6FEB))
                ) {
                    Text("Acquérir Rôle SMS")
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "WireGuard Tunnel Defensif", color = Color.White)
                Button(
                    onClick = onStartVpn,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636))
                ) {
                    Text("Démarrer VPN")
                }
            }
        }
    }
}
