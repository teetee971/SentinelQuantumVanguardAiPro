package com.sentinel.quantum.ptt

/**
 * Process-level bridge between Telecom call presence and the optional live PTT controller.
 *
 * Telecom truth may arrive before a PTT runtime exists. The latest presence state is therefore
 * retained and replayed when a controller attaches, closing the race where PTT starts while a
 * carrier call is already active. Only one controller may own the process-level PTT channel.
 */
internal class PttTelecomInterlock {
    private var controller: PttController? = null
    private var detachPending: PttController? = null
    private var telecomCallPresent = false

    @Synchronized
    fun attach(candidate: PttController): Boolean {
        val current = controller
        if (current != null && current !== candidate) {
            // An asynchronous teardown may have completed after detach() returned false. The next
            // owner can reclaim the slot only after the old controller has explicit DISCONNECTED
            // truth; otherwise ownership remains fail-closed.
            if (detachPending === current && current.state == PttState.DISCONNECTED) {
                controller = null
                detachPending = null
            } else {
                return false
            }
        }

        if (controller === candidate && detachPending === candidate) {
            if (candidate.state != PttState.DISCONNECTED) return false
            detachPending = null
        }

        controller = candidate
        candidate.onTelecomCallPresenceChanged(telecomCallPresent)
        return true
    }

    @Synchronized
    fun detach(candidate: PttController): Boolean {
        if (controller !== candidate) return false

        if (candidate.state == PttState.DISCONNECTED) {
            controller = null
            detachPending = null
            return true
        }

        detachPending = candidate
        candidate.disconnect()

        // A synchronous deterministic transport may already have acknowledged Disconnected.
        if (candidate.state == PttState.DISCONNECTED) {
            controller = null
            detachPending = null
            return true
        }

        // Keep ownership while DISCONNECTING or ERROR. A later retry, or an attach attempt by a
        // replacement controller after explicit Disconnected, can complete the hand-off safely.
        return false
    }

    @Synchronized
    fun onTelecomCallPresenceChanged(present: Boolean) {
        telecomCallPresent = present
        controller?.onTelecomCallPresenceChanged(present)
    }
}

/** Shared process-level rendezvous for the future PTT runtime/service. */
internal object PttProcessRuntime {
    private val telecomInterlock = PttTelecomInterlock()

    fun attachController(controller: PttController): Boolean = telecomInterlock.attach(controller)

    fun detachController(controller: PttController): Boolean = telecomInterlock.detach(controller)

    fun onTelecomCallPresenceChanged(present: Boolean) {
        telecomInterlock.onTelecomCallPresenceChanged(present)
    }
}
