package com.sentinel.quantum.ai

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.SecretKey

internal const val SENTINEL_AI_C11_PACKAGE = "com.sentinel.quantum.aivalidation.c11"

internal enum class SentinelAiRestartKind(val argument: String) {
    PROCESS("process"),
    REBOOT("reboot")
}

internal data class SentinelAiRestartIdentity(val pid: Int, val startTicks: Long, val bootCount: Int) {
    init {
        require(pid > 0 && startTicks > 0 && bootCount >= 0) { "Process identity is unavailable" }
    }

    fun provesRestartFrom(previous: SentinelAiRestartIdentity, kind: SentinelAiRestartKind): Boolean = when (kind) {
        SentinelAiRestartKind.PROCESS -> bootCount == previous.bootCount &&
            (pid != previous.pid || startTicks != previous.startTicks)
        SentinelAiRestartKind.REBOOT -> bootCount > previous.bootCount
    }

    fun encode(): String = "$pid|$startTicks|$bootCount"

    fun authenticate(key: SecretKey, fixture: String): String {
        val payload = encode()
        return "$payload|${mac(key, fixture, payload)}"
    }

    companion object {
        const val MAX_AUTHENTICATED_LENGTH = 128L

        fun verifyAuthenticated(record: String, key: SecretKey, fixture: String): SentinelAiRestartIdentity {
            val delimiter = record.lastIndexOf('|')
            require(record.length <= MAX_AUTHENTICATED_LENGTH && delimiter > 0) { "Malformed process metadata" }
            val payload = record.substring(0, delimiter)
            val supplied = record.substring(delimiter + 1)
            check(supplied.length == 64 && MessageDigest.isEqual(
                mac(key, fixture, payload).toByteArray(Charsets.US_ASCII),
                supplied.toByteArray(Charsets.US_ASCII)
            )) { "Fixture metadata altered or out of scope" }
            return decode(payload)
        }

        private fun mac(key: SecretKey, fixture: String, payload: String): String = Mac.getInstance("HmacSHA256").run {
            init(key)
            doFinal(("cycle11-restart-fixture-v1|$fixture|$payload").toByteArray(Charsets.US_ASCII))
                .joinToString("") { "%02x".format(it) }
        }

        fun decode(value: String): SentinelAiRestartIdentity {
            val fields = value.split('|', limit = 4)
            require(fields.size == 3) { "Malformed process metadata" }
            return SentinelAiRestartIdentity(fields[0].toInt(), fields[1].toLong(), fields[2].toInt())
                .also { require(it.encode() == value) { "Non-canonical process metadata" } }
        }

        fun fromProcStat(stat: String, pid: Int, bootCount: Int): SentinelAiRestartIdentity {
            val commandStarts = stat.indexOf(" (")
            val commandEnds = stat.lastIndexOf(") ")
            require(commandStarts > 0 && commandEnds > commandStarts &&
                stat.substring(0, commandStarts).toIntOrNull() == pid) { "Unrecognized /proc/self/stat identity" }
            // The substring starts at field 3 (state). starttime is field 22.
            val startTicks = stat.substring(commandEnds + 2).trim().split(' ')
                .getOrNull(19)?.toLongOrNull()
            requireNotNull(startTicks) { "Missing /proc/self/stat starttime" }
            return SentinelAiRestartIdentity(pid, startTicks, bootCount)
        }
    }
}
