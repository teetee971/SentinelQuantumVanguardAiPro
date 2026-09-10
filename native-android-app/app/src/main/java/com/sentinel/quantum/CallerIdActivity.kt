package com.sentinel.quantum

import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme

/** Read-only caller-ID surface. It never delays or changes the screening decision. */
class CallerIdActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        setContent {
            SentinelQuantumTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CallerCard(
                        number = intent.getStringExtra(EXTRA_NUMBER).orEmpty(),
                        country = intent.getStringExtra(EXTRA_COUNTRY).orEmpty(),
                        flag = intent.getStringExtra(EXTRA_FLAG).orEmpty(),
                        type = intent.getStringExtra(EXTRA_TYPE).orEmpty(),
                        verification = intent.getStringExtra(EXTRA_VERIFICATION).orEmpty(),
                        action = intent.getStringExtra(EXTRA_ACTION).orEmpty(),
                        reason = intent.getStringExtra(EXTRA_REASON).orEmpty(),
                        name = intent.getStringExtra(EXTRA_NAME),
                        organisation = intent.getStringExtra(EXTRA_ORGANISATION),
                        source = intent.getStringExtra(EXTRA_SOURCE).orEmpty(),
                        verified = intent.getBooleanExtra(EXTRA_IDENTITY_VERIFIED, false),
                        onDismiss = ::finish
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_NUMBER = "caller.number"
        const val EXTRA_COUNTRY = "caller.country"
        const val EXTRA_FLAG = "caller.flag"
        const val EXTRA_TYPE = "caller.type"
        const val EXTRA_VERIFICATION = "caller.verification"
        const val EXTRA_ACTION = "caller.action"
        const val EXTRA_REASON = "caller.reason"
        const val EXTRA_NAME = "caller.name"
        const val EXTRA_ORGANISATION = "caller.organisation"
        const val EXTRA_SOURCE = "caller.source"
        const val EXTRA_IDENTITY_VERIFIED = "caller.identity_verified"
    }
}

@Composable
private fun CallerCard(
    number: String,
    country: String,
    flag: String,
    type: String,
    verification: String,
    action: String,
    reason: String,
    name: String?,
    organisation: String?,
    source: String,
    verified: Boolean,
    onDismiss: () -> Unit
) {
    val riskColor = when (action) {
        "BLOCK" -> Color(0xFFE15555)
        "SILENCE" -> Color(0xFFF4B740)
        else -> Color(0xFF32D6A0)
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("SENTINEL CALL ID", color = Color(0xFF66C7FF), fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(flag, fontSize = 48.sp)
            Text(action, color = riskColor, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        }
        Text(name ?: "Identité non disponible", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        organisation?.let { Text(it, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary) }
        Text(number, fontSize = 24.sp)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Fact("Pays", "$flag $country")
                Fact("Type d’appel", type)
                Fact("Vérification opérateur", verification)
                Fact("Identité", if (verified) "Vérifiée" else "Non vérifiée")
                Fact("Source", source)
                Fact("Décision Sentinel", reason)
            }
        }
        Text(
            "Le pays est déduit de l’indicatif et peut être trompé. L’opérateur d’une tranche n’est pas forcément l’opérateur actuel après portabilité.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(4.dp))
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Fermer la fiche") }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Column(Modifier.fillMaxWidth().background(Color.Transparent)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "Non disponible" }, fontWeight = FontWeight.SemiBold)
    }
}
