package com.sentinel.quantum.security

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/** Cancellable sampling; callers own lifecycle and dispatcher, and unchanged facts do not emit. */
internal fun <T> phoneCoreSnapshots(
    read: suspend () -> T,
    pause: suspend () -> Unit = { delay(3_000L) }
): Flow<T> = flow {
    while (currentCoroutineContext().isActive) {
        emit(read())
        pause()
    }
}.distinctUntilChanged()

