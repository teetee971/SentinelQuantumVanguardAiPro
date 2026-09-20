package com.sentinel.quantum.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import androidx.core.content.ContextCompat

/** Explicitly permission-gated, read-only lookup in the device contact provider. */
class LocalContactLookup(private val context: Context) {
    data class Identity(val displayName: String, val organisation: String?)
    data class Contact(val contactId: Long, val displayName: String, val phoneNumber: String)

    enum class ContactAccessState { READY, PERMISSION_REQUIRED, PROVIDER_UNAVAILABLE }

    data class ContactListResult(val state: ContactAccessState, val contacts: List<Contact> = emptyList())

    fun listWithState(limit: Int = 500): ContactListResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return ContactListResult(ContactAccessState.PERMISSION_REQUIRED)
        }
        val safeLimit = limit.coerceIn(1, 500)
        return try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER
                ),
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE LOCALIZED ASC"
            ) ?: return ContactListResult(ContactAccessState.PROVIDER_UNAVAILABLE)
            val contacts = cursor.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val normalizedIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER)
                val seen = mutableSetOf<Pair<Long, String>>()
                buildList {
                    while (cursor.moveToNext() && size < safeLimit) {
                        val contactId = cursor.getLong(idIndex)
                        val rawName = cursor.getString(nameIndex)?.trim()?.take(160).orEmpty()
                        val number = cursor.getString(numberIndex)?.trim()?.take(64).orEmpty()
                        val providerNormalized = if (normalizedIndex >= 0) cursor.getString(normalizedIndex)?.trim().orEmpty() else ""
                        val canonical = canonicalNumber(providerNormalized.ifBlank { number })
                        if (number.isNotBlank() && canonical.isNotBlank() && seen.add(contactId to canonical)) {
                            val displayName = rawName.takeUnless { canonicalNumber(it) == canonical }.orEmpty()
                            add(Contact(contactId, displayName, number))
                        }
                    }
                }
            }
            ContactListResult(ContactAccessState.READY, contacts)
        } catch (_: SecurityException) {
            ContactListResult(ContactAccessState.PERMISSION_REQUIRED)
        } catch (_: RuntimeException) {
            ContactListResult(ContactAccessState.PROVIDER_UNAVAILABLE)
        }
    }

    private fun canonicalNumber(value: String): String {
        val normalized = PhoneNumberUtils.normalizeNumber(value.trim())
        if (normalized.isBlank()) return ""
        return normalized.take(64)
    }

    /** Backwards-compatible projection for callers that only need readable contacts. */
    fun list(limit: Int = 500): List<Contact> = listWithState(limit).contacts

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
