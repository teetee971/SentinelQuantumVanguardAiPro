package com.sentinel.quantum.security

import java.util.UUID

/** Correlation id shared by the provider projection and M-Send.req header. */
internal object MmsTransactionIdFactory {
    fun create(): String = UUID.randomUUID().toString()
}
