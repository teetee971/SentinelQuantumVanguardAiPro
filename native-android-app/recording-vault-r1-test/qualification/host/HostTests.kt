package com.sentinel.quantum.security

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption.*
import java.security.Security
import javax.crypto.spec.SecretKeySpec

private fun rejected(action: () -> Unit) {
    val failure = runCatching(action).exceptionOrNull()
    check(failure != null) { "Expected rejection, operation succeeded" }
}
fun main(args: Array<String>) {
    Security.addProvider(HostProvider())
    if (args.firstOrNull() == "worker") {
        val vault = RecordingVaultStore(Context(File(args[1])))
        while (!File(args[1], "go").exists()) Thread.sleep(5)
        File(args[1], "attempting-${args[2]}").writeText("attempting")
        try {
            vault.save("race", args[2].toByteArray())
            println("SAVED ${args[2]}")
        } catch (e: IllegalStateException) { println("DUPLICATE") }
        return
    }
    var failed = 0
    var passed = 0
    fun test(name: String, body: (File, RecordingVaultStore) -> Unit) {
        val root = Files.createTempDirectory("vault-host-").toFile()
        HostKeyStore.key = SecretKeySpec(ByteArray(32) { 7 }, "AES")
        HostKeyStore.onRead = null
        try {
            body(root, RecordingVaultStore(Context(root)))
            println("PASS HOST $name")
            passed++
        } catch (t: Throwable) {
            println("FAIL HOST $name: $t")
            t.printStackTrace(System.out)
            failed++
        } finally {
            HostKeyStore.onRead = null
            root.deleteRecursively()
        }
    }
    test("dangling destination introduced during encryption is not replaced") { root, vault ->
        val destination = File(root, "recording-vault-r2/item.rv").toPath()
        val absent = File(root, "absent").toPath()
        HostKeyStore.onRead = { Files.createSymbolicLink(destination, absent); HostKeyStore.onRead = null }
        rejected { vault.save("item", byteArrayOf(1)) }
        check(Files.isSymbolicLink(destination)) { "Destination was replaced" }
        check(!Files.exists(absent))
    }
    test("decrypt with missing key must not generate one") { _, _ ->
        val encrypted = RecordingVaultCrypto.encrypt(byteArrayOf(1, 2, 3))
        HostKeyStore.key = null
        val before = HostKeyStore.generations
        rejected { RecordingVaultCrypto.decrypt(encrypted) }
        check(HostKeyStore.generations == before && HostKeyStore.key == null)
    }
    test("roundtrip duplicate and recreate store") { root, vault ->
        val original = "confidential".toByteArray()
        vault.save("item", original)
        rejected { vault.save("item", byteArrayOf(9)) }
        check(RecordingVaultStore(Context(root)).read("item").contentEquals(original))
        check(vault.delete("item"))
        check(!vault.exists("item"))
    }
    test("size boundaries and malformed envelopes") { root, vault ->
        rejected { vault.save("empty", byteArrayOf()) }
        rejected { vault.save("large", ByteArray(16 * 1024 * 1024 + 1)) }
        val maximum = ByteArray(16 * 1024 * 1024) { (it % 251).toByte() }
        vault.save("max", maximum)
        check(vault.read("max").contentEquals(maximum))
        val file = File(root, "recording-vault-r2/max.rv")
        check(file.length() == 16 * 1024 * 1024 + 29L)
        for (size in listOf(0, 1, 12, 28, 29, 16 * 1024 * 1024 + 30)) {
            file.writeBytes(ByteArray(size))
            rejected { vault.read("max") }
        }
    }
    test("version IV ciphertext and tag corruption") { root, vault ->
        vault.save("item", "integrity".toByteArray())
        val file = File(root, "recording-vault-r2/item.rv")
        val valid = file.readBytes()
        for (index in listOf(0, 1, 13, valid.lastIndex)) {
            val damaged = valid.copyOf()
            damaged[index] = (damaged[index].toInt() xor 1).toByte()
            file.writeBytes(damaged)
            rejected { vault.read("item") }
        }
        file.writeBytes(valid.copyOf(valid.size - 1))
        rejected { vault.read("item") }
    }
    test("characterization: legacy valid ciphertext substitution is ACCEPTED") { root, vault ->
        vault.save("a", "first".toByteArray())
        vault.save("b", "second".toByteArray())
        File(root, "recording-vault-r2/a.rv").writeBytes(File(root, "recording-vault-r2/b.rv").readBytes())
        check(vault.read("a").toString(Charsets.UTF_8) == "second")
    }
    test("temporary residue is ignored and preserved") { root, vault ->
        vault.save("stable", byteArrayOf(42))
        val residue = File(root, "recording-vault-r2/r2-abandoned.part")
        residue.writeBytes(byteArrayOf(1, 2, 3))
        vault.save("next", byteArrayOf(43))
        check(vault.read("stable").contentEquals(byteArrayOf(42)))
        check(residue.readBytes().contentEquals(byteArrayOf(1, 2, 3)))
        check(!vault.exists("r2-abandoned"))
    }
    test("symbolic file and directory rejected") { root, vault ->
        vault.exists("init")
        val outside = File(root, "outside").apply { writeText("protected") }
        val link = File(root, "recording-vault-r2/link.rv").toPath()
        Files.createSymbolicLink(link, outside.toPath())
        rejected { vault.read("link") }
        rejected { vault.save("link", byteArrayOf(1)) }
        rejected { vault.delete("link") }
        check(outside.readText() == "protected")
        val external = File(root, "external").apply { mkdir() }
        Files.createSymbolicLink(File(root, "linked").toPath(), external.toPath())
        rejected { RecordingVaultStore(Context(root), "linked").save("item", byteArrayOf(1)) }
        check(external.listFiles()!!.isEmpty())
    }
    test("invalid identifiers rejected") { _, vault ->
        for (id in listOf("", "../x", "a/b", "a.b", "x".repeat(65))) {
            rejected { vault.save(id, byteArrayOf(1)) }
        }
    }
    test("store waits for a lock held by another process") { root, vault ->
        val lockPath = File(root, ".recording-vault-r3.lock").toPath()
        var process: Process? = null
        try {
            FileChannel.open(lockPath, CREATE, WRITE).use { channel ->
                channel.lock().use {
                    process = ProcessBuilder(System.getProperty("java.home") + "/bin/java", "-cp",
                        System.getProperty("java.class.path"), "com.sentinel.quantum.security.HostTestsKt",
                        "worker", root.path, "locked").redirectErrorStream(true).start()
                    File(root, "go").writeText("go")
                    val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10)
                    while (!File(root, "attempting-locked").exists()) {
                        check(System.nanoTime() < deadline) { "Worker not ready" }
                        Thread.sleep(5)
                    }
                    check(!process!!.waitFor(1, java.util.concurrent.TimeUnit.SECONDS)) { "Store ignored interprocess lock" }
                    check(!File(root, "recording-vault-r2/race.rv").exists())
                }
            }
            check(process!!.waitFor(10, java.util.concurrent.TimeUnit.SECONDS))
            val output = process!!.inputStream.bufferedReader().readText().trim()
            check(process!!.exitValue() == 0 && output == "SAVED locked") { output }
            check(vault.read("race").toString(Charsets.UTF_8) == "locked")
        } finally { process?.destroyForcibly() }
    }
    test("lock file symlink is rejected without touching its target") { root, vault ->
        val external = File(root, "outside").apply { writeText("protected") }
        Files.createSymbolicLink(File(root, ".recording-vault-r3.lock").toPath(), external.toPath())
        rejected { vault.save("item", byteArrayOf(1)) }
        check(external.readText() == "protected")
    }
    test("eight real JVM processes cannot overwrite one recording") { root, vault ->
        val processes = (0..7).map { index ->
            ProcessBuilder(System.getProperty("java.home") + "/bin/java", "-cp",
                System.getProperty("java.class.path"), "com.sentinel.quantum.security.HostTestsKt",
                "worker", root.path, "writer-$index").redirectErrorStream(true).start()
        }
        try {
            File(root, "go").writeText("go")
            val output = processes.map {
                check(it.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) { "Worker timeout" }
                val text = it.inputStream.bufferedReader().readText()
                check(it.exitValue() == 0) { text }
                text.trim()
            }
            check(output.count { it.startsWith("SAVED ") } == 1) { output.toString() }
            check(output.count { it == "DUPLICATE" } == 7) { output.toString() }
            check(vault.read("race").toString(Charsets.UTF_8) == output.single { it.startsWith("SAVED ") }.removePrefix("SAVED "))
        } finally { processes.forEach { it.destroyForcibly() } }
    }
    println("HOST SUMMARY passed=$passed failed=$failed; Android APIs/KeyStore are simulated, filesystem and AES/GCM are JVM/Linux")
    check(failed == 0)
}
