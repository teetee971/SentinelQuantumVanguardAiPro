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
    private var telecomCallPresent = false

    @Synchronized
    fun attach(candidate: PttController): Boolean {
        val current = controller
        if (current != null && current !== candidate) return false

        controller = candidate
        candidate.onTelecomCallPresenceChanged(telecomCallPresent)
        return true
    }

    @Synchronized
    fun detach(candidate: PttController): Boolean {
        if (controller !== candidate) return false

        if (candidate.state != PttState.DISCONNECTED) {
            candidate.disconnect()
        }
        if (candidate.state != PttState.DISCONNECTED) return false

        controller = null
        return true
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
