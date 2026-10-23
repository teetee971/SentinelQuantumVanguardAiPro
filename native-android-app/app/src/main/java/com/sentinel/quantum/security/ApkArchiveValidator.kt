package com.sentinel.quantum.security

import java.io.File
import java.util.zip.ZipFile

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

                if (name == "AndroidManifest.xml") {
                    manifestFound = true
                }
            }

            require(manifestFound) {
                "Manifest Android absent"
            }
        }
    }
}
