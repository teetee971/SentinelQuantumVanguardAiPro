package com.sentinel.quantum.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/** Explicitly permission-gated, read-only lookup in the device contact provider. */
class LocalContactLookup(private val context: Context) {
    data class Identity(val displayName: String, val organisation: String?)
    data class Contact(val contactId: Long, val displayName: String, val phoneNumber: String)

    fun list(limit: Int = 500): List<Contact> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val safeLimit = limit.coerceIn(1, 500)
        return runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE LOCALIZED ASC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                buildList {
                    while (cursor.moveToNext() && size < safeLimit) {
                        val name = cursor.getString(nameIndex)?.trim()?.take(160).orEmpty()
                        val number = cursor.getString(numberIndex)?.trim()?.take(64).orEmpty()
                        if (name.isNotBlank() && number.isNotBlank()) add(Contact(cursor.getLong(idIndex), name, number))
                    }
                }.distinctBy { it.contactId to it.phoneNumber }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    fun find(number: String?): Identity? {
        val safeNumber = number?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED) return null

        val lookupUri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(safeNumber)
        )
        val contact = runCatching {
            context.contentResolver.query(
                lookupUri,
                arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup._ID))
                val name = cursor.getString(
                    cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME)
                )?.trim()?.take(160).orEmpty()
                if (name.isBlank()) null else id to name
            }
        }.getOrNull() ?: return null

        val organisation = runCatching {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Organization.COMPANY),
                "${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
                arrayOf(
                    contact.first.toString(),
                    ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE
                ),
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                cursor.getString(0)?.trim()?.take(160)?.takeIf { it.isNotEmpty() }
            }
        }.getOrNull()

        return Identity(contact.second, organisation)
    }
}
