package com.sentinel.quantum.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.*
import androidx.core.content.ContextCompat

/** Bounded, selected-contact Data query. Caller owns dispatching off the UI thread. Never writes. */
class LocalContactProfileReader(private val context: Context) {
    fun canRead(): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun read(contactId: Long): List<ContactProfileField>? {
        if (!canRead()) throw SecurityException("Contacts permission required")
        if (contactId <= 0) return null
        return try {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data.MIMETYPE, *((1..10).map { "data$it" }.toTypedArray())),
                "${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE} IN (?,?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf(contactId.toString(), StructuredName.CONTENT_ITEM_TYPE, Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE, StructuredPostal.CONTENT_ITEM_TYPE,
                    Organization.CONTENT_ITEM_TYPE, Event.CONTENT_ITEM_TYPE, Note.CONTENT_ITEM_TYPE,
                    Website.CONTENT_ITEM_TYPE, Nickname.CONTENT_ITEM_TYPE, Relation.CONTENT_ITEM_TYPE,
                    Im.CONTENT_ITEM_TYPE, SipAddress.CONTENT_ITEM_TYPE),
                null
            )?.use { cursor ->
                val mime = cursor.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE)
                val indices = (1..10).map { cursor.getColumnIndexOrThrow("data$it") }
                buildList {
                    var visited = 0
                    while (cursor.moveToNext() && visited++ < 256) {
                        fun data(n: Int): String = cursor.getString(indices[n - 1]).orEmpty().trim()
                            .take(if (cursor.getString(mime) == Note.CONTENT_ITEM_TYPE && n == 1) 16384 else 4096)
                        val type = data(2).toIntOrNull() ?: 0
                        val custom = data(3).take(80)
                        val field = when (cursor.getString(mime)) {
                            StructuredName.CONTENT_ITEM_TYPE -> ContactProfileField(ContactProfileKind.NAME, "Nom structuré", listOf(
                                "Nom affiché" to data(1), "Prénom" to data(2), "Nom" to data(3),
                                "Préfixe" to data(4), "Autre prénom" to data(5), "Suffixe" to data(6),
                                "Prénom phonétique" to data(7), "Autre prénom phonétique" to data(8), "Nom phonétique" to data(9)
                            ).filter { it.second.isNotBlank() }.joinToString("\n") { "${it.first} : ${it.second}" })
                            Phone.CONTENT_ITEM_TYPE -> ContactProfileField(ContactProfileKind.PHONE,
                                Phone.getTypeLabel(context.resources, type, custom).toString(), data(1))
                            Email.CONTENT_ITEM_TYPE -> ContactProfileField(ContactProfileKind.EMAIL,
                                Email.getTypeLabel(context.resources, type, custom).toString(), data(1))
                            StructuredPostal.CONTENT_ITEM_TYPE -> ContactProfileField(ContactProfileKind.ADDRESS,
                                StructuredPostal.getTypeLabel(context.resources, type, custom).toString(), data(1).ifBlank {
                                    (4..10).map { data(it) }.filter { it.isNotBlank() }.joinToString("\n")
                                })
                            Organization.CONTENT_ITEM_TYPE -> ContactProfileField(ContactProfileKind.ORGANISATION,
                                Organization.getTypeLabel(context.resources, type, custom).toString(),
                                listOf("Entreprise" to data(1), "Fonction" to data(4), "Service" to data(5),
                                    "Description du poste" to data(6), "Symbole" to data(7), "Nom phonétique" to data(8), "Bureau" to data(9))
                                    .filter { it.second.isNotBlank() }.joinToString("\n") { "${it.first} : ${it.second}" })
                            Event.CONTENT_ITEM_TYPE -> ContactProfileField(ContactProfileKind.EVENT,
                                Event.getTypeLabel(context.resources, type, custom).toString(), data(1))
                            Note.CONTENT_ITEM_TYPE -> ContactProfileField(ContactProfileKind.NOTE, "", data(1))
                            Website.CONTENT_ITEM_TYPE -> ContactProfileField(ContactProfileKind.WEBSITE,
                                when (type) {
                                    Website.TYPE_CUSTOM -> custom
                                    Website.TYPE_HOME -> "Personnel"
                                    Website.TYPE_WORK -> "Travail"
                                    Website.TYPE_BLOG -> "Blog"
                                    Website.TYPE_PROFILE -> "Profil importé"
                                    Website.TYPE_HOMEPAGE -> "Page d’accueil"
                                    else -> "Site web"
                                }, data(1))
                            Nickname.CONTENT_ITEM_TYPE -> ContactProfileField(ContactProfileKind.NICKNAME,
                                when (type) {
                                    Nickname.TYPE_CUSTOM -> custom
                                    Nickname.TYPE_MAIDEN_NAME -> "Nom de naissance"
                                    Nickname.TYPE_INITIALS -> "Initiales"
                                    Nickname.TYPE_SHORT_NAME -> "Nom court"
                                    else -> "Autre nom"
                                }, data(1))
                            Relation.CONTENT_ITEM_TYPE -> ContactProfileField(ContactProfileKind.RELATION,
                                Relation.getTypeLabel(context.resources, type, custom).toString(), data(1))
                            Im.CONTENT_ITEM_TYPE -> ContactProfileField(ContactProfileKind.IM,
                                Im.getProtocolLabel(context.resources, data(5).toIntOrNull() ?: -1, data(6)).toString(), data(1))
                            SipAddress.CONTENT_ITEM_TYPE -> ContactProfileField(ContactProfileKind.SIP,
                                SipAddress.getTypeLabel(context.resources, type, custom).toString(), data(1))
                            else -> null
                        }
                        if (field != null) add(field)
                    }
                }
            }
        } catch (denied: SecurityException) { throw denied }
        catch (_: RuntimeException) { null }
    }
}
