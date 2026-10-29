package com.sentinel.quantum.security

import android.content.Context
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.Security
import java.util.concurrent.TimeUnit

private fun fails(block: () -> Unit) = check(runCatching(block).isFailure) { "Expected failure" }
private fun dir(root: File) = File(root, "recording-vault-r2")
private fun source(root: File) = File(dir(root), "item.rv")
private fun final(root: File) = File(dir(root), "item.v2.rv")
private fun marker(root: File) = File(dir(root), "item.v2.required")
private val payload = "authentic legacy recording".toByteArray()
private fun legacy(root: File, vault: RecordingVaultStore): ByteArray {
    vault.exists("init")
    return RecordingVaultCrypto.encrypt(payload).also { source(root).writeBytes(it) }
}

/** Real operations with a fault before/after exactly one operation. No simulated AES or rename. */
private class FaultIo(private val failAt: Int, private val after: Boolean) : VaultIo() {
    var calls = 0
    private fun call(body: () -> Unit) {
        calls++
        if (calls == failAt && !after) throw IOException("Injected EIO before $calls")
        body()
        if (calls == failAt && after) throw IOException("Injected EIO after $calls")
    }
    override fun writeForced(path: Path, bytes: ByteArray) = call { super.writeForced(path, bytes) }
    override fun forceFile(path: Path) = call { super.forceFile(path) }
    override fun syncDirectory(path: Path) = call { super.syncDirectory(path) }
    override fun publish(source: Path, destination: Path) = call { super.publish(source, destination) }
    override fun delete(path: Path) = call { super.delete(path) }
}

internal fun runMigrationTests(test: (String, (File, RecordingVaultStore) -> Unit) -> Unit) {
    // EIO at every durable I/O boundary, both before effect and after effect (ambiguous result).
    // The probe discovers operation count rather than silently leaving new transitions untested.
    val probeRoot = Files.createTempDirectory("vault-probe").toFile()
    val probe = FaultIo(Int.MAX_VALUE, false)
    val boundaries = try {
        legacy(probeRoot, RecordingVaultStore(Context(probeRoot)))
        RecordingVaultStore(Context(probeRoot), io = probe).migrate("item")
        probe.calls
    } finally { probeRoot.deleteRecursively() }
    for (boundary in 1..boundaries) for (after in listOf(false, true)) {
        test("EIO ${if (after) "after" else "before"} migration boundary $boundary/$boundaries") { root, vault ->
            val original = legacy(root, vault)
            fails { RecordingVaultStore(Context(root), io = FaultIo(boundary, after)).migrate("item") }
            check(source(root).readBytes().contentEquals(original))
            vault.migrate("item")
            check(vault.read("item").contentEquals(payload))
            check(source(root).readBytes().contentEquals(original))
        }
    }
    test("interrupted new save exposes and recovers sole authenticated pending copy") { root, _ ->
        val io = object : VaultIo() {
            override fun syncDirectory(path: Path) {
                super.syncDirectory(path)
                if (File(path.toFile(), "item.v2.pending").exists()) throw IOException("crash before intent")
            }
        }
        fails { RecordingVaultStore(Context(root), io = io).save("item", payload) }
        val pending = File(dir(root), "item.v2.pending").readBytes()
        val fresh = RecordingVaultStore(Context(root))
        check(fresh.exists("item"))
        check(fresh.read("item").contentEquals(payload))
        check(source(root).readBytes().contentEquals(pending))
    }
    test("partial new save is preserved but not falsely recovered") { root, _ ->
        val io = object : VaultIo() {
            override fun writeForced(path: Path, bytes: ByteArray) {
                Files.write(path, bytes.copyOf(9))
                throw IOException("ENOSPC")
            }
        }
        fails { RecordingVaultStore(Context(root), io = io).save("item", payload) }
        val pending = File(dir(root), "item.v2.pending")
        val original = pending.readBytes()
        val fresh = RecordingVaultStore(Context(root))
        check(fresh.exists("item"))
        fails { fresh.read("item") }
        fails { fresh.migrate("item") }
        check(pending.readBytes().contentEquals(original))
    }
    test("enrollment forces canonical V2 before publishing required marker") { root, vault ->
        vault.exists("init")
        source(root).writeBytes(RecordingVaultCrypto.encryptV2(payload, "recording-vault-r2", "item"))
        var forced = false
        val io = object : VaultIo() {
            override fun forceFile(path: Path) {
                super.forceFile(path)
                if (path.toString().endsWith("item.rv")) forced = true
            }
            override fun publish(source: Path, destination: Path) {
                if (destination.toString().endsWith(".v2.required")) check(forced) { "Marker before data force" }
                super.publish(source, destination)
            }
        }
        check(RecordingVaultStore(Context(root), io = io).read("item").contentEquals(payload))
    }
    test("partial ENOSPC preparation preserves V1 and can resume") { root, vault ->
        val original = legacy(root, vault)
        val io = object : VaultIo() {
            override fun writeForced(path: Path, bytes: ByteArray) {
                Files.write(path, bytes.copyOf(9))
                throw IOException("Injected ENOSPC")
            }
        }
        fails { RecordingVaultStore(Context(root), io = io).migrate("item") }
        check(source(root).readBytes().contentEquals(original))
        check(vault.read("item").contentEquals(payload))
        check(vault.migrate("item"))
        check(vault.read("item").contentEquals(payload))
    }
    test("corrupt V1 and truncation never cause migration mutation") { root, vault ->
        val original = legacy(root, vault)
        for (bytes in listOf(original.copyOf(12), original.copyOf(original.size - 1),
                original.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() })) {
            source(root).writeBytes(bytes)
            fails { vault.migrate("item") }
            check(source(root).readBytes().contentEquals(bytes))
            check(!final(root).exists() && !marker(root).exists())
        }
    }
    test("missing or corrupt committed V2 never falls back to preserved V1") { root, vault ->
        val original = legacy(root, vault)
        vault.migrate("item")
        val bytes = final(root).readBytes()
        for (bad in listOf(original, bytes.copyOf(12), bytes.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() })) {
            final(root).writeBytes(bad)
            fails { vault.read("item") }
            fails { vault.migrate("item") }
            check(source(root).readBytes().contentEquals(original))
        }
        final(root).delete()
        fails { vault.read("item") }
    }
    test("corrupt missing or substituted marker is fail closed") { root, vault ->
        legacy(root, vault)
        vault.migrate("item")
        val committed = marker(root).readBytes()
        marker(root).writeBytes(committed.copyOf(12))
        fails { vault.read("item") }
        marker(root).writeBytes(RecordingVaultCrypto.encryptV2(ByteArray(65), "recording-vault-r2", "item"))
        fails { vault.read("item") } // Domain separation, even with right key/id/directory.
        marker(root).delete()
        fails { vault.read("item") }
        fails { vault.migrate("item") }
    }
    test("V2 replacement between identifiers and directories is rejected") { root, vault ->
        legacy(root, vault)
        vault.migrate("item")
        vault.save("other", "wrong id".toByteArray())
        val other = File(dir(root), "other.rv").readBytes()
        final(root).writeBytes(other)
        fails { vault.read("item") }
        val foreign = RecordingVaultStore(Context(root), "foreign")
        foreign.save("item", "wrong directory".toByteArray())
        final(root).writeBytes(File(root, "foreign/item.rv").readBytes())
        fails { vault.read("item") }
    }
    test("new V2 canonical cannot be downgraded to authentic V1") { root, vault ->
        vault.save("item", payload)
        source(root).writeBytes(RecordingVaultCrypto.encrypt(payload))
        fails { vault.read("item") }
        fails { vault.migrate("item") }
    }
    test("valid V2 re-encryption in the same context cannot replace committed bytes") { root, vault ->
        legacy(root, vault)
        vault.migrate("item")
        final(root).writeBytes(RecordingVaultCrypto.encryptV2(payload, "recording-vault-r2", "item"))
        fails { vault.read("item") }
    }
    test("commit markers cannot be substituted across id or directory") { root, vault ->
        legacy(root, vault)
        vault.migrate("item")
        val original = marker(root).readBytes()
        vault.save("other", payload)
        marker(root).writeBytes(File(dir(root), "other.v2.required").readBytes())
        fails { vault.read("item") }
        val foreign = RecordingVaultStore(Context(root), "foreign")
        foreign.save("item", payload)
        marker(root).writeBytes(File(root, "foreign/item.v2.required").readBytes())
        fails { vault.read("item") }
        marker(root).writeBytes(original)
        check(vault.read("item").contentEquals(payload))
    }
    test("preexisting V2 canonical is enrolled without ciphertext rewrite") { root, vault ->
        vault.exists("init")
        val bytes = RecordingVaultCrypto.encryptV2(payload, "recording-vault-r2", "item")
        source(root).writeBytes(bytes)
        check(vault.read("item").contentEquals(payload))
        check(marker(root).exists())
        check(!vault.migrate("item"))
        check(source(root).readBytes().contentEquals(bytes))
    }
    test("sidecar and marker symlinks are rejected without touching external data") { root, vault ->
        legacy(root, vault)
        val outside = File(root, "outside").apply { writeText("untouched") }
        for (suffix in listOf(".v2.pending", ".v2.rv", ".v2.required", ".v2.required.pending")) {
            val link = File(dir(root), "item$suffix").toPath()
            Files.createSymbolicLink(link, outside.toPath())
            fails { vault.migrate("item") }
            check(outside.readText() == "untouched")
            Files.delete(link)
        }
    }
    test("migration retains source even with colliding publication destination") { root, vault ->
        val original = legacy(root, vault)
        val io = object : VaultIo() {
            override fun publish(source: Path, destination: Path) {
                if (destination.toString().endsWith(".v2.rv")) Files.write(destination, byteArrayOf(91))
                super.publish(source, destination)
            }
        }
        fails { RecordingVaultStore(Context(root), io = io).migrate("item") }
        check(final(root).readBytes().contentEquals(byteArrayOf(91)))
        check(source(root).readBytes().contentEquals(original))
        fails { vault.read("item") }
    }
    test("explicit delete removes all versions and allows identifier reuse") { root, vault ->
        legacy(root, vault)
        vault.migrate("item")
        check(vault.delete("item"))
        check(!vault.exists("item"))
        check(dir(root).listFiles()!!.none { it.name.startsWith("item.") })
        vault.save("item", payload)
        check(vault.read("item").contentEquals(payload))
    }
    test("failed deletion retains required marker until all data entries are removed") { root, vault ->
        legacy(root, vault)
        vault.migrate("item")
        val io = object : VaultIo() {
            override fun delete(path: Path) {
                if (path.toString().endsWith("item.rv")) throw IOException("EIO")
                super.delete(path)
            }
        }
        fails { RecordingVaultStore(Context(root), io = io).delete("item") }
        check(marker(root).exists() && source(root).exists())
        final(root).delete()
        fails { vault.read("item") }
    }
    test("missing key during migration does not create a replacement") { root, vault ->
        legacy(root, vault)
        HostKeyStore.key = null
        val before = HostKeyStore.generations
        fails { vault.migrate("item") }
        check(HostKeyStore.key == null && HostKeyStore.generations == before)
        check(!final(root).exists())
    }
    for (phase in listOf("prepared", "required", "published")) {
        test("process death at $phase resumes in a separate JVM") { root, vault ->
            val original = legacy(root, vault)
            val dead = child(root, phase)
            check(dead.first == 73) { dead.toString() }
            check(source(root).readBytes().contentEquals(original))
            val recovery = child(root, "recover")
            check(recovery.first == 0 && recovery.second.trim() == "RECOVERED") { recovery.toString() }
            check(vault.read("item").contentEquals(payload))
        }
    }
    test("four real processes migrate one source without overwriting V2") { root, vault ->
        val original = legacy(root, vault)
        val processes = (1..4).map { startChild(root, "recover") }
        for (process in processes) {
            check(process.waitFor(15, TimeUnit.SECONDS))
            check(process.exitValue() == 0) { process.inputStream.bufferedReader().readText() }
        }
        check(source(root).readBytes().contentEquals(original))
        check(vault.read("item").contentEquals(payload))
    }
}

private fun startChild(root: File, phase: String): Process = ProcessBuilder(
    System.getProperty("java.home") + "/bin/java", "-cp", System.getProperty("java.class.path"),
    "com.sentinel.quantum.security.HostTestsKt", "migration-worker", root.path, phase
).redirectErrorStream(true).start()
private fun child(root: File, phase: String): Pair<Int, String> {
    val process = startChild(root, phase)
    try {
        check(process.waitFor(15, TimeUnit.SECONDS))
        return process.exitValue() to process.inputStream.bufferedReader().readText()
    } finally { process.destroyForcibly() }
}

fun migrationWorker(args: Array<String>) {
    Security.addProvider(HostProvider())
    val phase = args[2]
    val io = object : VaultIo() {
        override fun syncDirectory(path: Path) {
            super.syncDirectory(path)
            if (phase == "prepared" && File(path.toFile(), "item.v2.pending").exists() &&
                    !File(path.toFile(), "item.v2.required").exists()) Runtime.getRuntime().halt(73)
        }
        override fun publish(source: Path, destination: Path) {
            super.publish(source, destination)
            if (phase == "required" && destination.toString().endsWith(".v2.required") ||
                phase == "published" && destination.toString().endsWith(".v2.rv")) Runtime.getRuntime().halt(73)
        }
    }
    val store = RecordingVaultStore(Context(File(args[1])), io = io)
    store.migrate("item")
    check(store.read("item").contentEquals(payload))
    println("RECOVERED")
}
