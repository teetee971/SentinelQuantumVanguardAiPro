package com.sentinel.quantum.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sentinel.quantum.security.ConversationNumberPolicy

@Composable
fun ConversationNumberActions(address: String, onDial: (String) -> Unit, onAddContact: (String) -> Unit) {
    val number = ConversationNumberPolicy.number(address)
    if (number == null) {
        Text("Aucune action téléphonique disponible pour cet expéditeur.",
            modifier = Modifier.testTag("conversation-number-unavailable"), style = MaterialTheme.typography.bodySmall)
        return
    }
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onDial(number) },
            modifier = Modifier.heightIn(min = 48.dp).testTag("conversation-call-number")) {
            Icon(Icons.Default.Call, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Appeler ce numéro")
        }
        OutlinedButton(onClick = { onAddContact(number) },
            modifier = Modifier.heightIn(min = 48.dp).testTag("conversation-add-contact")) {
            Icon(Icons.Default.PersonAdd, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Ajouter aux contacts")
        }
    }
}
