package com.sentinel.quantum.security

import java.net.URI

/** A completed asynchronous load belongs to exactly one contact and photo version. */
internal data class ContactPhotoSnapshot<T>(val contactId: Long, val uri: String, val image: T) {
    fun forContact(currentId: Long, currentUri: String?): T? =
        image.takeIf { currentId == contactId && currentUri == uri }
}

/** Never fetch remote avatars or use a photo URI from another content provider. */
internal object ContactPhotoPolicy {
    fun localUri(raw: String?): String? {
        if (raw.isNullOrBlank() || raw.length > 2048) return null
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        return raw.takeIf { uri.scheme == "content" && uri.host == "com.android.contacts" &&
            uri.userInfo == null && uri.fragment == null }
    }
}
