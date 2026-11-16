package com.sentinel.quantum.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import androidx.core.content.ContextCompat

/** Explicitly permission-gated, read-only lookup in the current Android profile contact provider. */
class LocalContactLookup(private val context: Context) {
    data class Identity(val displayName: String, val organisation: String?)

    data class Contact(
        val contactId: Long,
        val displayName: String,
        val phoneNumbers: List<String>,
        val providerHasPhoneNumber: Boolean
    )

    enum class ContactAccessState { READY, PERMISSION_REQUIRED, PROVIDER_UNAVAILABLE }

    data class ContactListResult(
        val state: ContactAccessState,
        val contacts: List<Contact> = emptyList(),
        val totalContacts: Int = 0,
        val callableContacts: Int = 0,
        val phoneNumberCount: Int = 0,
        val providerPhoneMismatchCount: Int = 0
    )

    fun listWithState(limit: Int = Int.MAX_VALUE): ContactListResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return ContactListResult(ContactAccessState.PERMISSION_REQUIRED)
        }
        val safeLimit = limit.coerceAtLeast(1)
        return try {
            val contactCursor = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.HAS_PHONE_NUMBER
                ),
                null,
                null,
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC"
            ) ?: return ContactListResult(ContactAccessState.PROVIDER_UNAVAILABLE)

            val contactSeeds = contactCursor.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                val hasPhoneIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                buildList {
                    while (cursor.moveToNext() && size < safeLimit) {
                        add(
                            ContactDirectoryPolicy.ContactSeed(
                                contactId = cursor.getLong(idIndex),
                                displayName = cursor.getString(nameIndex).orEmpty(),
                                providerHasPhoneNumber = cursor.getInt(hasPhoneIndex) > 0
                            )
                        )
                    }
                }
            }

            val selectedIds = contactSeeds.asSequence().map { it.contactId }.toHashSet()
            val phoneSeeds = if (selectedIds.isEmpty()) {
                emptyList()
            } else {
                val phoneCursor = context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER
                    ),
                    null,
                    null,
                    null
                ) ?: return ContactListResult(ContactAccessState.PROVIDER_UNAVAILABLE)

                phoneCursor.use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                    val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val normalizedIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER)
                    buildList {
                        while (cursor.moveToNext()) {
                            val contactId = cursor.getLong(idIndex)
                            if (contactId !in selectedIds) continue
                            val number = cursor.getString(numberIndex)?.trim()?.take(64).orEmpty()
                            val providerNormalized =
                                if (normalizedIndex >= 0) cursor.getString(normalizedIndex)?.trim().orEmpty() else ""
                            val canonical = canonicalNumber(providerNormalized.ifBlank { number })
                            if (number.isNotBlank() && canonical.isNotBlank()) {
                                add(
                                    ContactDirectoryPolicy.PhoneSeed(
                                        contactId = contactId,
                                        displayValue = number,
                                        canonicalKey = canonical
                                    )
                                )
                            }
                        }
                    }
                }
            }

            val contacts = ContactDirectoryPolicy.merge(
                contacts = contactSeeds,
                phones = phoneSeeds,
                limit = safeLimit
            ).map { entry ->
                val firstCanonical = entry.phoneNumbers.firstOrNull()?.let(::canonicalNumber).orEmpty()
                val displayName =
                    if (firstCanonical.isNotBlank() && looksLikePhoneNumber(entry.displayName, firstCanonical)) {
                        resolveStructuredDisplayName(entry.contactId, firstCanonical) ?: "Sans nom"
                    } else {
                        entry.displayName
                    }
                Contact(
                    contactId = entry.contactId,
                    displayName = displayName,
                    phoneNumbers = entry.phoneNumbers,
                    providerHasPhoneNumber = entry.providerHasPhoneNumber
                )
            }.sortedWith(
                compareBy<Contact> { it.displayName == "Sans nom" }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
                    .thenBy { it.contactId }
            )

            val callableContacts = contacts.count { it.phoneNumbers.isNotEmpty() }
            val phoneNumberCount = contacts.sumOf { it.phoneNumbers.size }
            val providerMismatchCount = contacts.count {
                it.providerHasPhoneNumber && it.phoneNumbers.isEmpty()
            }
            ContactListResult(
                state = ContactAccessState.READY,
                contacts = contacts,
                totalContacts = contacts.size,
                callableContacts = callableContacts,
                phoneNumberCount = phoneNumberCount,
                providerPhoneMismatchCount = providerMismatchCount
            )
        } catch (_: SecurityException) {
            ContactListResult(ContactAccessState.PERMISSION_REQUIRED)
        } catch (_: RuntimeException) {
            ContactListResult(ContactAccessState.PROVIDER_UNAVAILABLE)
        }
    }

    private fun resolveStructuredDisplayName(contactId: Long, canonicalPhone: String): String? {
        return runCatching {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME),
                "${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
                arrayOf(
                    contactId.toString(),
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                ),
                null
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val candidate = cursor.getString(nameIndex)?.trim()?.take(160).orEmpty()
                    if (candidate.isNotBlank() && !looksLikePhoneNumber(candidate, canonicalPhone)) {
                        return@use candidate
                    }
                }
                null
            }
        }.getOrNull()
    }

    private fun canonicalNumber(value: String): String {
        val normalized = PhoneNumberUtils.normalizeNumber(value.trim())
        if (normalized.isBlank()) return ""
        return normalized.take(64)
    }

    private fun looksLikePhoneNumber(value: String, canonicalPhone: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return false
        val hasDigit = trimmed.any(Char::isDigit)
        if (!hasDigit) return false
        val allowedFormattingOnly = trimmed.all { ch ->
            ch.isDigit() || ch.isWhitespace() || ch in "+-()./"
        }
        if (!allowedFormattingOnly) return false
        val candidate = canonicalNumber(trimmed)
        return candidate.isNotBlank() && candidate == canonicalPhone
    }

    /** Backwards-compatible projection for callers that only need readable contacts. */
    fun list(limit: Int = Int.MAX_VALUE): List<Contact> = listWithState(limit).contacts

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
