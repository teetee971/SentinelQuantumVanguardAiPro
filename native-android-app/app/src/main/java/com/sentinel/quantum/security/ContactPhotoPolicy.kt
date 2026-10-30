package com.sentinel.quantum.security

import java.net.URI

/** Provider revision plus a lifecycle fallback, since a content URI may keep serving new bytes. */
internal data class ContactPhotoIdentity(
    val contactId: Long,
    val uri: String?,
    val revision: Long? = null,
    val refreshGeneration: Int = 0
)

/** A completed asynchronous load belongs to exactly one contact and photo version. */
internal data class ContactPhotoSnapshot<T>(val identity: ContactPhotoIdentity, val image: T) {
    fun forIdentity(current: ContactPhotoIdentity): T? = image.takeIf { current == identity }
}

/** Never fetch remote avatars or use a photo URI from another content provider. */
internal object ContactPhotoPolicy {
    fun preferredLocalUri(thumbnail: String?, fullPhoto: String?): String? =
        localUri(thumbnail) ?: localUri(fullPhoto)

    fun localUri(raw: String?): String? {
        if (raw.isNullOrBlank() || raw.length > 2048) return null
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        return raw.takeIf { uri.scheme == "content" && uri.host == "com.android.contacts" &&
            uri.userInfo == null && uri.fragment == null }
    }
}
