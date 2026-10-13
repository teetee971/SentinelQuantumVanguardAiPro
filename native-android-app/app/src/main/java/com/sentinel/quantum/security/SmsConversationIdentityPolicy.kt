package com.sentinel.quantum.security

/**
 * Local-only presentation projection for an SMS/MMS conversation address.
 *
 * The provider address remains canonical for messaging actions. Contact data is only used to
 * improve the local label and is never persisted by this policy or sent to a remote service.
 */
internal object SmsConversationIdentityPolicy {
    enum class Provenance { ANDROID_CONTACT, UNKNOWN }

    data class ResolvedIdentity(
        val address: String,
        val displayLabel: String,
        val provenance: Provenance
    )

    fun resolve(
        address: String,
        localIdentity: LocalContactLookup.Identity?
    ): ResolvedIdentity {
        val safeAddress = address.trim().take(MAX_ADDRESS_CHARS)
        val localName = localIdentity?.displayName?.trim()?.take(MAX_DISPLAY_NAME_CHARS).orEmpty()
        return if (localName.isNotBlank()) {
            ResolvedIdentity(
                address = safeAddress,
                displayLabel = localName,
                provenance = Provenance.ANDROID_CONTACT
            )
        } else {
            ResolvedIdentity(
                address = safeAddress,
                displayLabel = safeAddress,
                provenance = Provenance.UNKNOWN
            )
        }
    }

    private const val MAX_ADDRESS_CHARS = 64
    private const val MAX_DISPLAY_NAME_CHARS = 160
}
