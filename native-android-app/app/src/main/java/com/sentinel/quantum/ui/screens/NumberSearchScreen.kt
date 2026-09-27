package com.sentinel.quantum.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sentinel.quantum.SentinelDialerActivity

@Composable
fun NumberSearchScreen() {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Recherche",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            "Recherchez un numéro avec le moteur Phone Core. Les informations affichées proviennent des sources réellement disponibles ; Sentinel ne complète pas les champs inconnus.",
            style = MaterialTheme.typography.bodyMedium
        )
        Button(
            onClick = { context.startActivity(Intent(context, SentinelDialerActivity::class.java)) }
        ) {
            Icon(Icons.Default.Search, contentDescription = null)
            Text("Rechercher un numéro")
        }
    }
}
