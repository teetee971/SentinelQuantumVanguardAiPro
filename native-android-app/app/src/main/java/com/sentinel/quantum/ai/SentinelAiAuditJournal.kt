package com.sentinel.quantum.ai

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.SecretKey

/** Bounded metadata-only journal. A partial two-file commit poisons verification: never repair/reset
 * silently. It detects single-file rollback/tampering, not restoration of BOTH files to an older
 * authenticated snapshot. Caller must disable AI effects on any storage uncertainty.
 * Approvals, messages, responses and identifiers are NEVER persisted here.
 */
internal class SentinelAiAuditJournal private constructor(private val directory: File, private val key: SecretKey) {
    enum class Kind { SESSION_OPEN, REQUEST, PROPOSED, REJECTED, AUTHENTICATED, CHECKPOINT, REVOKED, PROCESS_REVOKED, ERROR }
    data class Receipt(val sequence: Long, val digest: String)
    companion object {
        const val MAX_EVENTS = 256
        private const val MAX_BYTES = 65536L
        private val mutex = Any() // Also prevents OverlappingFileLockException across same-process instances.
        private val HEX = Regex("[0-9a-f]{64}")
        private val ZERO = "0".repeat(64)
        fun create(directory: File, key: SecretKey): SentinelAiAuditJournal = synchronized(mutex) {
            if (directory.exists() || !directory.mkdir()) throw IOException("Audit directory not new")
            val journal = SentinelAiAuditJournal(directory, key)
            journal.locked {
                journal.replace("events", "SAI1\n")
                val anchor = "0|$ZERO"
                journal.replace("anchor", anchor + "|" + journal.sign("anchor|$anchor") + "\n")
            }
            journal
        }
        fun open(directory: File, key: SecretKey): SentinelAiAuditJournal {
            if (!directory.isDirectory) throw IOException("Audit directory missing")
            return SentinelAiAuditJournal(directory, key).also { it.verify() }
        }
    }
    private fun sign(value: String): String = Mac.getInstance("HmacSHA256").run {
        init(key)
        doFinal(("sentinel-ai-audit-v1|" + value).toByteArray(Charsets.US_ASCII))
            .joinToString("") { "%02x".format(it) }
    }
    private fun same(a: String, b: String): Boolean = HEX.matches(b) && MessageDigest.isEqual(
        a.toByteArray(Charsets.US_ASCII), b.toByteArray(Charsets.US_ASCII))
    private fun <T> locked(block: () -> T): T = synchronized(mutex) {
        if (!directory.isDirectory) throw IOException("Audit directory missing")
        FileOutputStream(File(directory, "lock"), true).channel.use { channel ->
            channel.lock().use { block() }
        }
    }
    private fun read(name: String, limit: Long): String {
        val file = File(directory, name)
        if (!file.isFile || file.length() !in 1..limit) throw IOException("Audit storage missing or oversized")
        val data = file.inputStream().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > limit) throw IOException("Audit storage oversized")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        if (data.any { (it.toInt() and 0xff) !in 0x0a..0x7e }) throw IOException("Audit encoding rejected")
        return String(data, Charsets.US_ASCII)
    }
    private data class Verified(val raw: String, val receipt: Receipt)
    private fun verified(): Verified {
        val raw = read("events", MAX_BYTES)
        if (!raw.startsWith("SAI1\n") || !raw.endsWith('\n')) throw IOException("Audit framing rejected")
        val lines = raw.removeSuffix("\n").split('\n')
        if (lines.size > MAX_EVENTS + 1) throw IOException("Audit event count rejected")
        var previous = ZERO
        var sequence = 0L
        for (line in lines.drop(1)) {
            val parts = line.split('|')
            if (parts.size != 4) throw IOException("Audit record rejected")
            sequence++
            val uptime = parts[1].toLongOrNull()
            if (parts[0] != sequence.toString() || uptime == null || uptime < 0 || parts[1] != uptime.toString() ||
                Kind.entries.none { it.name == parts[2] }) throw IOException("Audit metadata rejected")
            val expected = sign("event|$previous|${parts.take(3).joinToString("|")}")
            if (!same(expected, parts[3])) throw IOException("Audit authentication rejected")
            previous = parts[3]
        }
        val anchor = read("anchor", 256)
        val expected = "$sequence|$previous"
        if (anchor != expected + "|" + sign("anchor|$expected") + "\n") throw IOException("Audit anchor mismatch")
        return Verified(raw, Receipt(sequence, previous))
    }
    fun verify(): Receipt = locked { verified().receipt }
    fun append(kind: Kind, elapsedRealtimeMs: Long): Receipt {
        require(elapsedRealtimeMs >= 0)
        return locked {
            val state = verified()
            if (state.receipt.sequence >= MAX_EVENTS) throw IOException("Audit capacity reached")
            val sequence = state.receipt.sequence + 1
            val metadata = "$sequence|$elapsedRealtimeMs|${kind.name}"
            val digest = sign("event|${state.receipt.digest}|$metadata")
            val next = state.raw + metadata + "|" + digest + "\n"
            if (next.length > MAX_BYTES) throw IOException("Audit capacity reached")
            replace("events", next)
            val anchor = "$sequence|$digest"
            replace("anchor", anchor + "|" + sign("anchor|$anchor") + "\n")
            Receipt(sequence, digest)
        }
    }
    private fun replace(name: String, text: String) {
        val temporary = File.createTempFile("audit-", ".new", directory)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(text.toByteArray(Charsets.US_ASCII))
                output.fd.sync()
            }
            // Same-directory POSIX rename on Android/Linux; works on minSdk24 without java.nio API26.
            if (!temporary.renameTo(File(directory, name))) throw IOException("Audit commit failed")
        } finally {
            if (temporary.exists()) temporary.delete() // Only our own uncommitted temporary file.
        }
    }
}
