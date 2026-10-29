package com.sentinel.quantum.ui.design

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sentinel.quantum.data.BoundedInputReader
import com.sentinel.quantum.security.ContactPhotoPolicy
import com.sentinel.quantum.security.ContactPhotoSnapshot
import com.sentinel.quantum.security.LocalContactLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Local provider photo only, bounded and loaded off the UI thread. Never an invented portrait. */
@Composable
fun D1ContactAvatar(contact: LocalContactLookup.Contact, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val safeUri = ContactPhotoPolicy.localUri(contact.photoUri)
    val photo by produceState<ContactPhotoSnapshot<ImageBitmap>?>(null, contact.contactId, safeUri) {
        val loaded = withContext(Dispatchers.IO) {
            if (safeUri == null || ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) null
            else runCatching {
                val bytes = context.contentResolver.openInputStream(Uri.parse(safeUri))?.use {
                    BoundedInputReader.read(it, 2 * 1024 * 1024)
                } ?: return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth !in 1..8000 || bounds.outHeight !in 1..8000) return@runCatching null
                val options = BitmapFactory.Options().apply { inSampleSize = 1 }
                while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 256) options.inSampleSize *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
            }.getOrNull()
        }
        value = if (loaded != null && safeUri != null) ContactPhotoSnapshot(contact.contactId, safeUri, loaded) else null
    }
    Surface(modifier.clip(CircleShape), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        // produceState retains its previous value until the new producer runs.
        // Reject that value synchronously when a list slot changes identity or photo URI.
        val actualPhoto = photo?.forContact(contact.contactId, safeUri)
        if (actualPhoto != null) Image(actualPhoto, contentDescription = null, contentScale = ContentScale.Crop)
        else Box(contentAlignment = Alignment.Center) {
            Text(contact.displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun D1ContactProfileSheet(
    contact: LocalContactLookup.Contact,
    onDismiss: () -> Unit,
    onCall: (String) -> Unit,
    onMessage: (String) -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SentinelCommunicationHeader("Fiche contact · source locale", contact.displayName,
                "Dans votre carnet Android. Cette présence ne constitue pas une vérification de l’identité.")
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                D1ContactAvatar(contact, Modifier.size(88.dp))
            }
            SentinelGlassCard(Modifier.fillMaxWidth()) {
                Text("Coordonnées", style = MaterialTheme.typography.titleMedium)
                if (contact.phoneNumbers.isEmpty()) Text("Aucun numéro lisible dans le carnet Android.")
                contact.phoneNumbers.forEach { number ->
                    Text(number, style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { onCall(number) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Appeler") }
                        OutlinedButton(onClick = { onMessage(number) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Message") }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.heightIn(min = 48.dp)) { Text("Modifier dans Contacts Android") }
                OutlinedButton(onClick = onShare, modifier = Modifier.heightIn(min = 48.dp)) { Text("Partager") }
            }
            Text("Provenance : carnet Android du profil courant. Les profils sociaux, dates, notes et niveaux de confiance ne sont pas inventés lorsqu’ils ne sont pas importés.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
