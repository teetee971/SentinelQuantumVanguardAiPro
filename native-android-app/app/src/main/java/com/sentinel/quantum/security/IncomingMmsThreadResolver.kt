package com.sentinel.quantum.security

/**
 * Pure recipient resolver for an incoming M-Retrieve.conf conversation thread.
 *
 * A normal direct MMS commonly carries one TO address: the device itself. In that case the sender
 * alone defines the thread. Once TO contains several entries or CC is present, self identity is
 * required so Sentinel cannot silently collapse two distinct group conversations into one sender
 * thread.
 */
internal object IncomingMmsThreadResolver {
    sealed interface Result {
        data class Ready(val recipients: Set<String>) : Result
        data object SelfIdentityRequired : Result
    }

    fun resolve(
        sender: String,
        toAddresses: List<String>,
        ccAddresses: List<String>,
        selfAddresses: Set<String>
    ): Result {
        val recipients = linkedSetOf(sender)
        val groupAddressing = toAddresses.size > 1 || ccAddresses.isNotEmpty()
        if (!groupAddressing) return Result.Ready(recipients)
        if (selfAddresses.isEmpty()) return Result.SelfIdentityRequired

        for (address in toAddresses + ccAddresses) {
            if (selfAddresses.none { sameAddress(address, it) }) {
                addDistinct(recipients, address)
            }
        }
        return Result.Ready(recipients)
    }

    private fun addDistinct(recipients: MutableSet<String>, candidate: String) {
        if (recipients.none { sameAddress(it, candidate) }) recipients += candidate
    }

    internal fun sameAddress(leftRaw: String, rightRaw: String): Boolean {
        val left = leftRaw.substringBefore('/').trim()
        val right = rightRaw.substringBefore('/').trim()
        if (left == right) return true
        val leftPhone = CallRuleEngine.normalizeNumber(left)
        val rightPhone = CallRuleEngine.normalizeNumber(right)
        if (leftPhone != null && rightPhone != null) return leftPhone == rightPhone
        return left.contains('@') && right.contains('@') && left.equals(right, ignoreCase = true)
    }
}
