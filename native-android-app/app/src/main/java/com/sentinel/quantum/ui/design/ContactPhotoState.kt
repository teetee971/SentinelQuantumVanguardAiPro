package com.sentinel.quantum.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.sentinel.quantum.security.ContactPhotoIdentity
import com.sentinel.quantum.security.ContactPhotoSnapshot
import com.sentinel.quantum.security.loadContactPhoto

/** Owns the asynchronous state; the caller supplies the bounded local-provider read. */
@Composable
internal fun <T> rememberContactPhoto(identity: ContactPhotoIdentity, load: suspend () -> T?): T? {
    val result by produceState<ContactPhotoSnapshot<T>?>(null, identity) {
        value = loadContactPhoto(identity, load)
    }
    // A previous value can survive recomposition until the new producer starts.
    return result?.forIdentity(identity)
}
