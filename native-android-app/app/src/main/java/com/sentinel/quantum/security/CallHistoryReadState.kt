package com.sentinel.quantum.security

/** In-memory UI snapshot bound to observed role/permission and foreground access, not just a successful query. */
internal data class CallHistoryReadState(
    val allowed: Boolean = false,
    val entries: List<SystemCallLogReader.Entry> = emptyList(),
    val loading: Boolean = false,
    val generation: Long = 0
) {
    fun withAccess(roleHeld: Boolean, permissionGranted: Boolean, resumed: Boolean = true): CallHistoryReadState {
        val nextAllowed = roleHeld && permissionGranted && resumed
        if (nextAllowed == allowed) return this
        return copy(
            allowed = nextAllowed,
            entries = emptyList(),
            loading = false,
            generation = generation + 1
        )
    }

    fun beginRead(): CallHistoryReadState =
        if (!allowed || loading) this else copy(loading = true, generation = generation + 1)

    fun complete(
        readGeneration: Long,
        rows: List<SystemCallLogReader.Entry>,
        canReadNow: Boolean
    ): CallHistoryReadState {
        if (!canReadNow) return withAccess(false, false)
        if (!allowed || !loading || generation != readGeneration) return this
        return copy(entries = rows.toList(), loading = false)
    }
}
