package com.sentinel.quantum.security

import java.util.Locale

/**
 * Pure aggregation policy for the Android contact provider.
 *
 * The provider exposes aggregate contacts and phone rows through separate tables. Keeping
 * aggregation here makes it testable and prevents the UI from silently dropping contacts
 * that have no readable phone row.
 */
object ContactDirectoryPolicy {
    data class ContactSeed(
        val contactId: Long,
        val displayName: String,
        val providerHasPhoneNumber: Boolean
    )

    data class PhoneSeed(
        val contactId: Long,
        val displayValue: String,
        val canonicalKey: String
    )

    data class Entry(
        val contactId: Long,
        val displayName: String,
        val phoneNumbers: List<String>,
        val providerHasPhoneNumber: Boolean
    )

    fun merge(
        contacts: List<ContactSeed>,
        phones: List<PhoneSeed>,
        limit: Int = Int.MAX_VALUE
    ): List<Entry> {
        val safeLimit = limit.coerceAtLeast(1)
        val selected = LinkedHashMap<Long, ContactSeed>()
        contacts.forEach { seed ->
            if (selected.size >= safeLimit) return@forEach
            if (seed.contactId >= 0L && !selected.containsKey(seed.contactId)) {
                selected[seed.contactId] = seed.copy(
                    displayName = seed.displayName.trim().take(MAX_NAME_LENGTH)
                )
            }
        }

        val numbers = selected.keys.associateWith { LinkedHashMap<String, String>() }.toMutableMap()
        phones.forEach { phone ->
            val perContact = numbers[phone.contactId] ?: return@forEach
            val display = phone.displayValue.trim().take(MAX_PHONE_LENGTH)
            val canonical = phone.canonicalKey.trim().take(MAX_PHONE_LENGTH)
            if (display.isNotBlank() && canonical.isNotBlank()) {
                perContact.putIfAbsent(canonical, display)
            }
        }

        return selected.values.map { seed ->
            Entry(
                contactId = seed.contactId,
                displayName = seed.displayName.ifBlank { "Sans nom" },
                phoneNumbers = numbers[seed.contactId]?.values?.toList().orEmpty(),
                providerHasPhoneNumber = seed.providerHasPhoneNumber
            )
        }.sortedWith(
            compareBy<Entry>(
                { it.displayName == "Sans nom" },
                { it.displayName.lowercase(Locale.ROOT) },
                { it.contactId }
            )
        )
    }

    private const val MAX_NAME_LENGTH = 160
    private const val MAX_PHONE_LENGTH = 64
}
