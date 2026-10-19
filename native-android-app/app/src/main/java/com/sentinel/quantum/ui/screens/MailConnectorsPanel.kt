package com.sentinel.quantum.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sentinel.quantum.security.MailConnectorContract

/**
 * Fail-closed integration status: no OAuth broker is deployed yet.
 * This panel must never imply that ChatGPT's Gmail connection is transferable
 * to Sentinel or that a mailbox can already be scanned automatically.
 */
@Composable
internal fun MailConnectorsPanel(
    connections: List<MailConnectorContract.Connection> = listOf(
        MailConnectorContract.Connection(MailConnectorContract.Provider.GMAIL),
        MailConnectorContract.Connection(MailConnectorContract.Provider.MICROSOFT_365),
        MailConnectorContract.Connection(MailConnectorContract.Provider.IMAP)
    )
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Comptes e-mail", style = MaterialTheme.typography.titleLarge)
        Text(
            "Connecteurs en préparation : aucune boîte mail n’est consultée automatiquement. " +
                "L’accès nécessitera une autorisation indépendante pour Sentinel.",
            style = MaterialTheme.typography.bodyMedium
        )
        connections.forEach { connection ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        when (connection.provider) {
                            MailConnectorContract.Provider.GMAIL -> "Gmail"
                            MailConnectorContract.Provider.MICROSOFT_365 -> "Outlook / Microsoft 365"
                            MailConnectorContract.Provider.IMAP -> "Autre messagerie (IMAP)"
                        },
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        when (connection.state) {
                            MailConnectorContract.State.NOT_CONFIGURED -> "Non configuré"
                            MailConnectorContract.State.AUTHORIZATION_REQUIRED -> "Autorisation requise"
                            MailConnectorContract.State.CONNECTING -> "Connexion en cours"
                            MailConnectorContract.State.CONNECTED ->
                                if (connection.canRead) "Lecture autorisée" else "Connecté · lecture non autorisée"
                            MailConnectorContract.State.EXPIRED -> "Autorisation expirée"
                            MailConnectorContract.State.REVOKED -> "Autorisation révoquée"
                            MailConnectorContract.State.ERROR -> "Erreur de connexion"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
