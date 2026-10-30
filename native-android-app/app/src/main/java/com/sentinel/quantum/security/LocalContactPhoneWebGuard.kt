package com.sentinel.quantum.security

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.core.content.ContextCompat

/** Read-only selected-contact check before sending its number to an external browser. Caller uses IO. */
class LocalContactPhoneWebGuard(private val context: Context) {
    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun isCurrent(contactId: Long, lookupKey: String?, number: String): Boolean {
        val selected = ContactPhoneWebSearch.normalized(number) ?: return false
        if (contactId <= 0 || lookupKey.isNullOrBlank() || lookupKey.length > 1024 || !hasPermission()) return false
        return runCatching {
            val sameContact = context.contentResolver.query(
                ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
                arrayOf(ContactsContract.Contacts.LOOKUP_KEY), null, null, null
            )?.use { cursor -> cursor.moveToFirst() && cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)) == lookupKey } ?: false
            if (!sameContact || !hasPermission()) return false
            val present = context.contentResolver.query(Phone.CONTENT_URI, arrayOf(Phone.NUMBER),
                "${Phone.CONTACT_ID}=?", arrayOf(contactId.toString()), null)?.use { cursor ->
                val column = cursor.getColumnIndexOrThrow(Phone.NUMBER)
                var visited = 0
                var found = false
                while (cursor.moveToNext() && visited++ < 256) {
                    if (ContactPhoneWebSearch.normalized(cursor.getString(column).orEmpty()) == selected) { found = true; break }
                }
                found
            } ?: false
            present && hasPermission()
        }.getOrDefault(false)
    }
}
