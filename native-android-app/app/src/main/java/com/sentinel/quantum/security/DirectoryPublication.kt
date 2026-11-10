package com.sentinel.quantum.security

import java.util.concurrent.atomic.AtomicInteger

/**
 * Process-local directory publication. No contacts provider and no permission API:
 * the caller supplies the permission bit and the loaded numbers.
 * A revocation closes the view and advances the generation under the same lock that publishes,
 * so an older refresh cannot restore a ready set afterwards.
 */
internal class DirectoryPublication {
    data class View(val ready: Boolean, val numbers: Set<String>)

    private val generation = AtomicInteger()
    private val lock = Any()

    @Volatile
    private var view: View = View(ready = false, numbers = emptySet())

    fun current(): View = view

    fun closeBecausePermissionMissing(): View {
        val closed = View(ready = false, numbers = emptySet())
        synchronized(lock) {
            generation.incrementAndGet()
            view = closed
        }
        return closed
    }

    fun openRefresh(): Int = generation.incrementAndGet()

    fun isLatest(ticket: Int): Boolean = generation.get() == ticket

    /**
     * @return false when [ticket] is no longer current. A ready view is stored only when
     * [permissionGranted] is still true.
     */
    fun publish(ticket: Int, next: View, permissionGranted: Boolean): Boolean {
        synchronized(lock) {
            if (generation.get() != ticket) return false
            view = if (next.ready && !permissionGranted) {
                View(ready = false, numbers = emptySet())
            } else {
                next
            }
            return true
        }
    }

    companion object {
        const val MAX_NUMBERS = 10_000

        /** True while another accepted number would still fit. At the cap the load must fail open. */
        fun hasRoom(size: Int, max: Int = MAX_NUMBERS): Boolean = size < max
    }
}
