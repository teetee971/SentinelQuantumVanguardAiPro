package com.sentinel.quantum.security

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Shared load boundary used by the Compose producer. */
internal suspend fun <T> loadContactPhoto(
    identity: ContactPhotoIdentity,
    load: suspend () -> T?
): ContactPhotoSnapshot<T>? {
    val image = load()
    // Provider I/O may finish despite cancellation. It must never publish over a newer load.
    currentCoroutineContext().ensureActive()
    return image?.let { ContactPhotoSnapshot(identity, it) }
}
