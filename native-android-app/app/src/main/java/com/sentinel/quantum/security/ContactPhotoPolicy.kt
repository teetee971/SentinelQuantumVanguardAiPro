package com.sentinel.quantum.security

import java.net.URI

/** Never fetch remote avatars or use a photo URI from another content provider. */
internal object ContactPhotoPolicy {
    fun localUri(raw: String?): String? {
        if (raw.isNullOrBlank() || raw.length > 2048) return null
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        return raw.takeIf { uri.scheme == "content" && uri.host == "com.android.contacts" &&
            uri.userInfo == null && uri.fragment == null }
    }
}
