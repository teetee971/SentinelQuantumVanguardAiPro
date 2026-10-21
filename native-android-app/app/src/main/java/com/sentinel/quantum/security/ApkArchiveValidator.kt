package com.sentinel.quantum.security

import java.io.File
import java.util.zip.ZipException
import java.util.zip.ZipFile

/** Structural screening only. Cryptographic validity is checked separately with apksig. */
internal object ApkArchiveValidator {
    fun validate(file: File, deadlineNanos: Long = System.nanoTime() + 30_000_000_000L) {
        fun checkDeadline() {
            require(System.nanoTime() - deadlineNanos < 0L) { "Délai d'analyse ZIP dépassé" }
        }
        checkDeadline()
        require(file.isFile && file.length() > 0) { "APK vide ou absent" }
        file.inputStream().use { input ->
            val header = ByteArray(4)
            require(input.read(header) == 4 &&
                header[0] == 0x50.toByte() && header[1] == 0x4b.toByte() &&
                header[2] == 0x03.toByte() && header[3] == 0x04.toByte()) {
                "Le fichier n'est pas une archive APK ZIP"
            }
        }
        try {
            ZipFile(file).use { zip ->
                var count = 0
                var totalExpanded = 0L
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    checkDeadline()
                    count++
                    require(count <= 100_000) { "Trop d'entrées ZIP" }
                    require(entry.name.length <= 1024) { "Nom d'entrée ZIP excessif" }
                    require(!entry.name.startsWith("/") && !entry.name.split('/').contains("..")) {
                        "Chemin ZIP non sûr"
                    }
                    val size = entry.size
                    require(size >= 0) { "Taille décompressée inconnue" }
                    require(size <= 350L * 1024 * 1024) { "Entrée ZIP trop grande" }
                    totalExpanded += size
                    require(totalExpanded <= 1_000L * 1024 * 1024) { "Archive trop volumineuse après extraction" }
                    val compressed = entry.compressedSize
                    require(compressed >= 0) { "Taille compressée inconnue" }
                    require(size <= 1024L * maxOf(1L, compressed)) { "Compression suspecte" }
                }
                require(zip.getEntry("AndroidManifest.xml") != null) { "Manifeste Android absent" }
                require(zip.entries().asSequence().any { it.name.matches(Regex("classes[0-9]*\\.dex")) }) {
                    "Code DEX absent : archive APK non prise en charge"
                }
                // Read the security-critical entries through a bounded stream. This validates
                // actual expansion and CRC, rather than trusting the ZIP directory alone.
                for (name in listOf("AndroidManifest.xml", "classes.dex")) {
                    val entry = zip.getEntry(name) ?: continue
                    val cap = if (name == "AndroidManifest.xml") 8L * 1024 * 1024
                              else 128L * 1024 * 1024
                    var expanded = 0L
                    zip.getInputStream(entry).use { stream ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = stream.read(buffer)
                            if (n == -1) break
                            checkDeadline()
                            expanded += n
                            require(expanded <= cap) { "Entrée critique décompressée trop volumineuse" }
                        }
                    }
                    require(expanded == entry.size) { "Taille ZIP déclarée incohérente" }
                }
            }
        } catch (e: ZipException) {
            throw IllegalArgumentException("Archive ZIP invalide", e)
        }
    }
}
