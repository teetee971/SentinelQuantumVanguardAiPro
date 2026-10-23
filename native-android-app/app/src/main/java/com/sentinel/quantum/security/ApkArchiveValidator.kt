package com.sentinel.quantum.security

import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipException

object ApkArchiveValidator {
    private const val MAX_ENTRY_BYTES = 350L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 350L * 1024 * 1024

    fun validate(file: File) {
        require(file.isFile && file.length() > 0) {
            "APK absent ou vide"
        }

        ZipFile(file).use { zip ->
            require(zip.size() in 1..100000) {
                "Nombre d'entrees ZIP invalide"
            }

            val entries = zip.entries()
            val names = HashSet<String>()
            var manifestFound = false
            var totalDeclaredBytes = 0L
            var totalActualBytes = 0L
            val buffer = ByteArray(64 * 1024)

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name

                require(
                    !name.startsWith("/") &&
                    !name.contains("\\") &&
                    name.split('/').none { it == ".." }
                ) {
                    "Chemin APK dangereux"
                }

                val size = entry.size
                require(size in 0..MAX_ENTRY_BYTES) {
                    "Taille ZIP non determinee ou excessive"
                }
                require(totalDeclaredBytes <= MAX_TOTAL_BYTES - size) {
                    "Contenu ZIP total trop volumineux"
                }
                totalDeclaredBytes += size

                require(names.add(name)) {
                    "Entree ZIP dupliquee"
                }

                if (!entry.isDirectory) {
                    var entryActualBytes = 0L
                    zip.getInputStream(entry).use { input ->
                        while (true) {
                            val count = input.read(buffer)
                            if (count == -1) break
                            entryActualBytes += count
                            totalActualBytes += count
                            require(entryActualBytes <= MAX_ENTRY_BYTES && totalActualBytes <= MAX_TOTAL_BYTES) {
                                "Contenu ZIP decompresse excessif"
                            }
                        }
                    }
                    require(entryActualBytes == size) { "Taille ZIP reelle incoherente" }
                }

                if (name == "AndroidManifest.xml" && !entry.isDirectory && size > 0) {
                    manifestFound = true
                }
            }

            require(manifestFound) {
                "Manifest Android absent"
            }
        }
    }
}
