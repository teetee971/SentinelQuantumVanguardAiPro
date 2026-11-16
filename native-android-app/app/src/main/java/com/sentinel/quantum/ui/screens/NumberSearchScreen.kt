package com.sentinel.quantum.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sentinel.quantum.SentinelDialerActivity
import com.sentinel.quantum.ui.design.SentinelD1
import com.sentinel.quantum.ui.design.SentinelHero
import com.sentinel.quantum.ui.design.SentinelPanel
import com.sentinel.quantum.ui.design.SentinelSectionHeader
import com.sentinel.quantum.ui.design.SentinelTopBar

@Composable
fun NumberSearchScreen() {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            SentinelTopBar(
                title = "Recherche",
                subtitle = "Numéros & Caller ID"
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SentinelHero(
                eyebrow = "Phone Core",
                title = "Identifier un numéro",
                body = "La recherche de numéro ouvre le moteur Phone Core. Sentinel affiche uniquement les informations réellement disponibles et conserve les champs inconnus comme inconnus.",
                badges = listOf(
                    "Gratuit" to SentinelD1.Success,
                    "État explicite" to SentinelD1.Cyan
                )
            )

            SentinelSectionHeader(
                title = "Recherche téléphonique",
                subtitle = "Saisissez le numéro dans le composeur Sentinel pour lancer l’identification et les contrôles disponibles."
            )

            SentinelPanel {
                Icon(
                    Icons.Default.PhoneInTalk,
                    contentDescription = null,
                    tint = SentinelD1.Cyan
                )
                Text(
                    "La fiche numéro peut regrouper le pays, le type d’indicatif, les contacts locaux et les signaux de réputation réellement accessibles."
                )
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(context, SentinelDialerActivity::class.java)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Text("Ouvrir le composeur et rechercher")
                }
            }
        }
    }
}
